package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.pricing.PriceResolver;
import com.hbsisms.feature.customer.pricing.ResolvedPrice;
import com.hbsisms.feature.storefront.dto.CartItemResponse;
import com.hbsisms.feature.storefront.dto.CartResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B2C cart (Inter 2 #25): add/update/remove lines; price via {@link PriceResolver}; does not hold
 * stock.
 */
@Service
@Transactional(readOnly = true)
public class CartService {

    public static final String SESSION_HEADER = "X-Cart-Session";

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final PriceResolver priceResolver;

    public CartService(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductVariantRepository productVariantRepository,
            PriceResolver priceResolver) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productVariantRepository = productVariantRepository;
        this.priceResolver = priceResolver;
    }

    @Transactional
    public CartResponse getOrCreate(UUID sessionToken) {
        Cart cart = findActiveOrCreate(requireSession(sessionToken));
        return toResponse(reload(cart.getId()));
    }

    @Transactional
    public CartResponse addItem(UUID sessionToken, Long variantId, int qty) {
        requireQty(qty);
        Cart cart = findActiveOrCreate(requireSession(sessionToken));
        ProductVariant variant = requireSellableVariant(variantId);

        CartItem existing =
                cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variant.getId()).orElse(null);
        if (existing != null) {
            existing.setQty(existing.getQty() + qty);
            cartItemRepository.save(existing);
        } else {
            CartItem item = new CartItem();
            item.setVariant(variant);
            item.setQty(qty);
            cart.addItem(item);
            cartRepository.save(cart);
        }
        return toResponse(reload(cart.getId()));
    }

    @Transactional
    public CartResponse updateItemQty(UUID sessionToken, Long itemId, int qty) {
        requireQty(qty);
        Cart cart = requireActiveCart(requireSession(sessionToken));
        CartItem item = cartItemRepository
                .findByIdAndCart_Id(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng giỏ hàng"));
        requireSellableVariant(item.getVariant().getId());
        item.setQty(qty);
        cartItemRepository.save(item);
        return toResponse(reload(cart.getId()));
    }

    @Transactional
    public CartResponse removeItem(UUID sessionToken, Long itemId) {
        Cart cart = requireActiveCart(requireSession(sessionToken));
        CartItem item = cartItemRepository
                .findByIdAndCart_Id(itemId, cart.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy dòng giỏ hàng"));
        cart.removeItem(item);
        cartRepository.save(cart);
        return toResponse(reload(cart.getId()));
    }

    @Transactional
    public CartResponse clear(UUID sessionToken) {
        Cart cart = requireActiveCart(requireSession(sessionToken));
        List<CartItem> copy = new ArrayList<>(cart.getItems());
        for (CartItem item : copy) {
            cart.removeItem(item);
        }
        cartRepository.save(cart);
        return toResponse(reload(cart.getId()));
    }

    private Cart findActiveOrCreate(UUID sessionToken) {
        return cartRepository
                .findWithItemsBySessionTokenAndStatus(sessionToken, CartStatus.ACTIVE)
                .orElseGet(() -> cartRepository.save(Cart.forSession(sessionToken)));
    }

    private Cart requireActiveCart(UUID sessionToken) {
        return cartRepository
                .findWithItemsBySessionTokenAndStatus(sessionToken, CartStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giỏ hàng"));
    }

    private Cart reload(Long cartId) {
        return cartRepository
                .findWithItemsByIdAndStatus(cartId, CartStatus.ACTIVE)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giỏ hàng"));
    }

    private ProductVariant requireSellableVariant(Long variantId) {
        ProductVariant variant = productVariantRepository
                .findWithProductById(variantId)
                .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));
        if (!variant.isActive()) {
            throw ApiException.badRequest("Quy cách không còn bán");
        }
        Product product = variant.getProduct();
        if (product == null || !product.isActive() || !product.isPublished()) {
            throw ApiException.badRequest("Sản phẩm chưa được đăng bán công khai");
        }
        return variant;
    }

    private CartResponse toResponse(Cart cart) {
        // Guest cart: no customer special price (customerId = null).
        Long customerId = cart.getCustomer() == null ? null : cart.getCustomer().getId();
        List<CartItemResponse> lines = new ArrayList<>();
        int totalQty = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            ProductVariant variant = item.getVariant();
            Product product = variant.getProduct();
            ResolvedPrice resolved = priceResolver.resolve(customerId, variant.getId(), item.getQty());
            BigDecimal lineTotal = resolved.unitPrice().multiply(BigDecimal.valueOf(item.getQty()));
            totalQty += item.getQty();
            totalAmount = totalAmount.add(lineTotal);
            lines.add(new CartItemResponse(
                    item.getId(),
                    variant.getId(),
                    product.getId(),
                    product.getName(),
                    product.getSlug(),
                    product.getImageUrl(),
                    variant.getSku(),
                    variant.getPackLabel(),
                    variant.getUnitWeightG(),
                    item.getQty(),
                    resolved.unitPrice(),
                    resolved.priceBasis(),
                    lineTotal));
        }

        return new CartResponse(
                cart.getId(),
                cart.getSessionToken(),
                cart.getStatus().name(),
                lines,
                totalQty,
                totalAmount);
    }

    private static UUID requireSession(UUID sessionToken) {
        if (sessionToken == null) {
            throw ApiException.badRequest("Thiếu header " + SESSION_HEADER);
        }
        return sessionToken;
    }

    private static void requireQty(int qty) {
        if (qty < 1) {
            throw ApiException.badRequest("Số lượng khay phải ≥ 1");
        }
    }
}
