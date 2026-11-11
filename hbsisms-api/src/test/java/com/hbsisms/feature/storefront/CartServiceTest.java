package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.pricing.PriceResolver;
import com.hbsisms.feature.customer.pricing.ResolvedPrice;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.storefront.dto.CartResponse;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;
    @Mock
    private CartItemRepository cartItemRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private PriceResolver priceResolver;

    private CartService cartService;
    private UUID session;

    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productVariantRepository, priceResolver);
        session = UUID.randomUUID();
    }

    @Test
    void addItem_createsLine_andResolvesListPrice() {
        Cart cart = Cart.forSession(session);
        ReflectionTestUtils.setField(cart, "id", 1L);
        ProductVariant variant = sellableVariant(20L, 10L);

        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCart_IdAndVariant_Id(1L, 20L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findWithItemsByIdAndStatus(1L, CartStatus.ACTIVE)).thenAnswer(inv -> {
            Cart saved = Cart.forSession(session);
            ReflectionTestUtils.setField(saved, "id", 1L);
            CartItem item = new CartItem();
            ReflectionTestUtils.setField(item, "id", 100L);
            item.setVariant(variant);
            item.setQty(2);
            saved.addItem(item);
            return Optional.of(saved);
        });
        when(priceResolver.resolve(eq(null), eq(20L), eq(2)))
                .thenReturn(new ResolvedPrice(new BigDecimal("189000.00"), PriceBasis.LIST));

        CartResponse response = cartService.addItem(session, 20L, 2);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).unitPrice()).isEqualByComparingTo("189000.00");
        assertThat(response.items().get(0).priceBasis()).isEqualTo(PriceBasis.LIST);
        assertThat(response.totalQty()).isEqualTo(2);
        ArgumentCaptor<Cart> captor = ArgumentCaptor.forClass(Cart.class);
        verify(cartRepository).save(captor.capture());
        assertThat(captor.getValue().getItems()).hasSize(1);
    }

    @Test
    void addItem_unpublishedProduct_rejects() {
        Cart cart = Cart.forSession(session);
        ReflectionTestUtils.setField(cart, "id", 1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        variant.getProduct().setPublished(false);

        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));

        assertThatThrownBy(() -> cartService.addItem(session, 20L, 1))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void updateItemQty_belowOne_rejects() {
        assertThatThrownBy(() -> cartService.updateItemQty(session, 1L, 0))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    private static ProductVariant sellableVariant(Long variantId, Long productId) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setCode("TOM-SU-01");
        product.setName("Tôm sú đông lạnh");
        product.setSlug("tom-su-dong-lanh");
        product.setPublished(true);
        product.setActive(true);
        product.setImageUrl("/images/demo-products/product-tom-su.png");

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        variant.setProduct(product);
        variant.setSku("TOM-SU-500");
        variant.setPackLabel("Khay 500g");
        variant.setUnitWeightG(500);
        variant.setListPrice(new BigDecimal("189000.00"));
        variant.setActive(true);
        return variant;
    }
}
