package com.hbsisms.feature.storefront;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
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
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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

    @Test
    void getOrCreate_newSession_persistsEmptyCart() {
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
            Cart cart = inv.getArgument(0);
            ReflectionTestUtils.setField(cart, "id", 7L);
            return cart;
        });
        when(cartRepository.findWithItemsByIdAndStatus(7L, CartStatus.ACTIVE)).thenAnswer(inv -> {
            Cart cart = Cart.forSession(session);
            ReflectionTestUtils.setField(cart, "id", 7L);
            return Optional.of(cart);
        });

        CartResponse response = cartService.getOrCreate(session);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalQty()).isZero();
        verify(cartRepository).save(any(Cart.class));
    }

    // ------------------------------------------------------------------
    // Phase 1 — CartService_addItem (UTCID02-05, 07-12)
    // ------------------------------------------------------------------

    /** UTCID02: existing line qty 2 + add 1 → merged line qty 3, totalQty 3 (no new CartItem). */
    @Test
    void addItem_existingLine_mergesQty() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        CartItem existing = cartItem(100L, variant, 2);
        cart.addItem(existing);

        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCart_IdAndVariant_Id(1L, 20L)).thenReturn(Optional.of(existing));
        when(cartRepository.findWithItemsByIdAndStatus(1L, CartStatus.ACTIVE)).thenReturn(Optional.of(cart));
        when(priceResolver.resolve(eq(null), eq(20L), eq(3)))
                .thenReturn(new ResolvedPrice(new BigDecimal("189000.00"), PriceBasis.LIST));

        CartResponse response = cartService.addItem(session, 20L, 1);

        assertThat(existing.getQty()).isEqualTo(3);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).qty()).isEqualTo(3);
        assertThat(response.totalQty()).isEqualTo(3);
        verify(cartItemRepository).save(existing);
        verify(cartRepository, never()).save(any(Cart.class));
    }

    /** UTCID03: sessionToken null → 400 "Thiếu header X-Cart-Session". */
    @Test
    void addItem_nullSession_rejects() {
        assertBadRequest(() -> cartService.addItem(null, 20L, 1), "Thiếu header X-Cart-Session");
        verify(productVariantRepository, never()).findWithProductById(any());
    }

    /** UTCID04 / UTCID10: variant 999 does not exist → 400 "Quy cách không tồn tại". */
    @Test
    void addItem_variantNotFound_rejects() {
        Cart cart = cartWithId(1L);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(999L)).thenReturn(Optional.empty());

        assertBadRequest(() -> cartService.addItem(session, 999L, 1), "Quy cách không tồn tại");
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID05: variant.active=false → 400 "Quy cách không còn bán". */
    @Test
    void addItem_inactiveVariant_rejects() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        variant.setActive(false);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));

        assertBadRequest(() -> cartService.addItem(session, 20L, 1), "Quy cách không còn bán");
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID07 (qty=0) and UTCID08 (qty=-1): 400 "Số lượng khay phải ≥ 1". */
    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void addItem_qtyBelowOne_rejects(int qty) {
        assertBadRequest(() -> cartService.addItem(session, 20L, qty), "Số lượng khay phải ≥ 1");
        verify(cartRepository, never()).save(any());
        verify(productVariantRepository, never()).findWithProductById(any());
    }

    /** UTCID09: boundary qty=1 on empty cart → CartResponse with one line, totalQty 1. */
    @Test
    void addItem_qtyOneBoundary_succeeds() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);

        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));
        when(cartItemRepository.findByCart_IdAndVariant_Id(1L, 20L)).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findWithItemsByIdAndStatus(1L, CartStatus.ACTIVE)).thenAnswer(inv -> {
            Cart saved = cartWithId(1L);
            saved.addItem(cartItem(100L, variant, 1));
            return Optional.of(saved);
        });
        when(priceResolver.resolve(eq(null), eq(20L), eq(1)))
                .thenReturn(new ResolvedPrice(new BigDecimal("189000.00"), PriceBasis.LIST));

        CartResponse response = cartService.addItem(session, 20L, 1);

        assertThat(response).isNotNull();
        assertThat(response.items()).hasSize(1);
        assertThat(response.totalQty()).isEqualTo(1);
        assertThat(response.totalAmount()).isEqualByComparingTo("189000.00");
    }

    /** UTCID06/12: unpublished product — also assert message (existing test only asserted status). */
    @Test
    void addItem_unpublishedProduct_rejectsWithMessage() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        variant.getProduct().setPublished(false);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));

        assertBadRequest(() -> cartService.addItem(session, 20L, 1), "Sản phẩm chưa được đăng bán công khai");
    }

    // ------------------------------------------------------------------
    // Phase 1 — CartService_updateItemQty (UTCID01-05, 07-10)
    // ------------------------------------------------------------------

    /** UTCID01 / UTCID09: update line qty to 5 → success. */
    @Test
    void updateItemQty_success_setsQty() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        CartItem item = cartItem(100L, variant, 1);
        cart.addItem(item);
        stubUpdateHappyPath(cart, item, variant, 5);

        CartResponse response = cartService.updateItemQty(session, 100L, 5);

        assertThat(item.getQty()).isEqualTo(5);
        assertThat(response.items()).hasSize(1);
        assertThat(response.items().get(0).qty()).isEqualTo(5);
        assertThat(response.totalQty()).isEqualTo(5);
        verify(cartItemRepository).save(item);
    }

    /** UTCID02: boundary qty=1 → success. */
    @Test
    void updateItemQty_qtyOneBoundary_succeeds() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        CartItem item = cartItem(100L, variant, 4);
        cart.addItem(item);
        stubUpdateHappyPath(cart, item, variant, 1);

        CartResponse response = cartService.updateItemQty(session, 100L, 1);

        assertThat(item.getQty()).isEqualTo(1);
        assertThat(response.totalQty()).isEqualTo(1);
        verify(cartItemRepository).save(item);
    }

    /** UTCID03: null session → 400 "Thiếu header X-Cart-Session". */
    @Test
    void updateItemQty_nullSession_rejects() {
        assertBadRequest(() -> cartService.updateItemQty(null, 100L, 5), "Thiếu header X-Cart-Session");
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID04: no active cart for session → 404 "Không tìm thấy giỏ hàng". */
    @Test
    void updateItemQty_cartNotFound_returns404() {
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.empty());

        assertApi(() -> cartService.updateItemQty(session, 100L, 5), HttpStatus.NOT_FOUND, "Không tìm thấy giỏ hàng");
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID05 / UTCID10: item 999 not in cart → 404 "Không tìm thấy dòng giỏ hàng". */
    @Test
    void updateItemQty_itemNotFound_returns404() {
        Cart cart = cartWithId(1L);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(999L, 1L)).thenReturn(Optional.empty());

        assertApi(
                () -> cartService.updateItemQty(session, 999L, 5),
                HttpStatus.NOT_FOUND,
                "Không tìm thấy dòng giỏ hàng");
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID07: variant of the line became inactive → 400 "Quy cách không còn bán". */
    @Test
    void updateItemQty_inactiveVariant_rejects() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        variant.setActive(false);
        CartItem item = cartItem(100L, variant, 1);
        cart.addItem(item);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(100L, 1L)).thenReturn(Optional.of(item));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));

        assertBadRequest(() -> cartService.updateItemQty(session, 100L, 5), "Quy cách không còn bán");
        assertThat(item.getQty()).isEqualTo(1);
        verify(cartItemRepository, never()).save(any());
    }

    /** UTCID08: product unpublished → 400 "Sản phẩm chưa được đăng bán công khai". */
    @Test
    void updateItemQty_unpublishedProduct_rejects() {
        Cart cart = cartWithId(1L);
        ProductVariant variant = sellableVariant(20L, 10L);
        variant.getProduct().setPublished(false);
        CartItem item = cartItem(100L, variant, 1);
        cart.addItem(item);
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(100L, 1L)).thenReturn(Optional.of(item));
        when(productVariantRepository.findWithProductById(20L)).thenReturn(Optional.of(variant));

        assertBadRequest(() -> cartService.updateItemQty(session, 100L, 5), "Sản phẩm chưa được đăng bán công khai");
        assertThat(item.getQty()).isEqualTo(1);
        verify(cartItemRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void stubUpdateHappyPath(Cart cart, CartItem item, ProductVariant variant, int newQty) {
        when(cartRepository.findWithItemsBySessionTokenAndStatus(session, CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(cartItemRepository.findByIdAndCart_Id(item.getId(), cart.getId())).thenReturn(Optional.of(item));
        when(productVariantRepository.findWithProductById(variant.getId())).thenReturn(Optional.of(variant));
        when(cartRepository.findWithItemsByIdAndStatus(cart.getId(), CartStatus.ACTIVE))
                .thenReturn(Optional.of(cart));
        when(priceResolver.resolve(eq(null), eq(variant.getId()), eq(newQty)))
                .thenReturn(new ResolvedPrice(new BigDecimal("189000.00"), PriceBasis.LIST));
    }

    private Cart cartWithId(Long id) {
        Cart cart = Cart.forSession(session);
        ReflectionTestUtils.setField(cart, "id", id);
        return cart;
    }

    private static CartItem cartItem(Long id, ProductVariant variant, int qty) {
        CartItem item = new CartItem();
        ReflectionTestUtils.setField(item, "id", id);
        item.setVariant(variant);
        item.setQty(qty);
        return item;
    }

    private static void assertBadRequest(ThrowingCallable call, String message) {
        assertApi(call, HttpStatus.BAD_REQUEST, message);
    }

    private static void assertApi(ThrowingCallable call, HttpStatus status, String message) {
        assertThatThrownBy(call)
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(status);
                    assertThat(api.getMessage()).isEqualTo(message);
                });
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
