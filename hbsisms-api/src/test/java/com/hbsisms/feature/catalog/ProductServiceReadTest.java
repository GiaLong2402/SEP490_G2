package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import com.hbsisms.feature.inventory.StockRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Cường — unit tests for ProductService list/get (read). Do not put create/update here
 * (those stay in ProductServiceTest owned by Doanh).
 */
@ExtendWith(MockitoExtension.class)
class ProductServiceReadTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private VolumeTierRepository volumeTierRepository;
    @Mock
    private StockRepository stockRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(
                productRepository,
                categoryRepository,
                productVariantRepository,
                volumeTierRepository,
                stockRepository);
    }

    @Test
    void list_noFilter_usesNameThenIdSort_andMapsEmptyVariants() {
        Product product = product(10L, "P-TOM", "Tôm sú", null);
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(product), pageable, 1);
                });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of());

        PageResponse<ProductResponse> page = productService.list(null, null, 1, 15);

        assertThat(page.page()).isEqualTo(1);
        assertThat(page.pageSize()).isEqualTo(15);
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).code()).isEqualTo("P-TOM");
        assertThat(page.items().get(0).variants()).isEmpty();

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(productRepository).findAll(any(Specification.class), pageableCaptor.capture());
        Sort sort = pageableCaptor.getValue().getSort();
        assertThat(sort.getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.ASC);
        assertThat(sort.getOrderFor("id").getDirection()).isEqualTo(Sort.Direction.ASC);
        verify(categoryRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void list_blankQ_stillQueriesWithoutRequiringCategoryTree() {
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<ProductResponse> page = productService.list("   ", null, 1, 15);

        assertThat(page.items()).isEmpty();
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
        verify(categoryRepository, never()).findAll(any(Sort.class));
    }

    @Test
    void list_qWithLikeWildcards_usesEscapedPatternContract() {
        assertThat(Strings.escapeLike("a%b_c")).isEqualTo("a\\%b\\_c");

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        productService.list("a%b_c", null, 1, 15);

        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void categoryIdsIncludingDescendants_includesSelfChildAndGrandchild() {
        Category root = category(1L, "root", null);
        Category child = category(2L, "child", root);
        Category grandchild = category(3L, "grand", child);
        when(categoryRepository.findAll(Sort.by(Sort.Order.asc("id"))))
                .thenReturn(List.of(root, child, grandchild));

        @SuppressWarnings("unchecked")
        List<Long> ids = (List<Long>) ReflectionTestUtils.invokeMethod(
                productService, "categoryIdsIncludingDescendants", 1L);

        assertThat(ids).containsExactly(1L, 2L, 3L);
    }

    @Test
    void list_withCategoryId_loadsCategoryTreeThenQueriesProducts() {
        Category root = category(1L, "root", null);
        Category child = category(2L, "child", root);
        when(categoryRepository.findAll(Sort.by(Sort.Order.asc("id")))).thenReturn(List.of(root, child));
        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        productService.list(null, 1L, 1, 15);

        verify(categoryRepository).findAll(Sort.by(Sort.Order.asc("id")));
        verify(productRepository).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void get_returnsVariantsTiersAndAvailable() {
        Category category = category(5L, "Hải sản", null);
        Product product = product(10L, "P-TOM", "Tôm sú", category);
        ProductVariant variant = variant(20L, product, "TOM-05");
        VolumeTier tier = tier(30L, variant, 10, "90000");

        when(productRepository.findWithCategoryById(10L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        when(volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(20L)).thenReturn(List.of(tier));
        when(stockRepository.sumQtyAvailableByVariantId(20L)).thenReturn(42L);

        ProductResponse response = productService.get(10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.categoryName()).isEqualTo("Hải sản");
        assertThat(response.variants()).hasSize(1);
        assertThat(response.variants().get(0).sku()).isEqualTo("TOM-05");
        assertThat(response.variants().get(0).available()).isEqualTo(42L);
        assertThat(response.variants().get(0).tiers()).hasSize(1);
        assertThat(response.variants().get(0).tiers().get(0).minQtyKhay()).isEqualTo(10);
    }

    @Test
    void get_missing_returns404() {
        when(productRepository.findWithCategoryById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.get(99L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(api.getMessage()).isEqualTo("Không tìm thấy sản phẩm");
                });
    }

    @Test
    void get_productWithoutVariants_returnsEmptyVariants() {
        Product product = product(11L, "P-EMPTY", "Chưa có quy cách", null);
        when(productRepository.findWithCategoryById(11L)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(11L)).thenReturn(List.of());

        ProductResponse response = productService.get(11L);

        assertThat(response.variants()).isEmpty();
        verify(volumeTierRepository, never()).findByVariant_IdOrderByMinQtyKhayAsc(any());
        verify(stockRepository, never()).sumQtyAvailableByVariantId(any());
    }

    @Test
    void listPublic_masksAvailableAsBinary_andSkipsInactiveVariants() {
        Category category = category(5L, "Hải sản", null);
        Product product = product(10L, "P-TOM", "Tôm sú", category);
        product.setPublished(true);
        ProductVariant active = variant(20L, product, "TOM-05");
        ProductVariant inactive = variant(21L, product, "TOM-OFF");
        inactive.setActive(false);

        when(productRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(product), pageable, 1);
                });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(active, inactive));
        when(volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(20L)).thenReturn(List.of());
        when(stockRepository.sumQtyAvailableByVariantId(20L)).thenReturn(42L);

        PageResponse<ProductResponse> page = productService.listPublic(null, null, 1, 15);

        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).variants()).hasSize(1);
        assertThat(page.items().get(0).variants().get(0).sku()).isEqualTo("TOM-05");
        assertThat(page.items().get(0).variants().get(0).available()).isEqualTo(1L);
    }

    @Test
    void getBySlugPublic_unpublished_returns404() {
        Product product = product(10L, "P-DRAFT", "Nháp", null);
        product.setSlug("nhap");
        product.setPublished(false);
        when(productRepository.findWithCategoryBySlugIgnoreCase("nhap")).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getBySlugPublic("nhap"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getBySlugPublic_published_masksZeroStock() {
        Product product = product(10L, "P-TOM", "Tôm sú", null);
        product.setSlug("tom-su");
        product.setPublished(true);
        ProductVariant variant = variant(20L, product, "TOM-05");
        when(productRepository.findWithCategoryBySlugIgnoreCase("tom-su")).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(10L)).thenReturn(List.of(variant));
        when(volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(20L)).thenReturn(List.of());
        when(stockRepository.sumQtyAvailableByVariantId(20L)).thenReturn(0L);

        ProductResponse response = productService.getBySlugPublic("tom-su");

        assertThat(response.variants()).hasSize(1);
        assertThat(response.variants().get(0).available()).isEqualTo(0L);
    }

    private static Product product(Long id, String code, String name, Category category) {
        Product product = new Product();
        setId(product, id);
        product.setCode(code);
        product.setName(name);
        product.setCategory(category);
        product.setActive(true);
        product.setPublished(false);
        product.setGallery(List.of());
        return product;
    }

    private static Category category(Long id, String name, Category parent) {
        Category category = new Category();
        setId(category, id);
        category.setCode("c-" + id);
        category.setName(name);
        category.setParent(parent);
        category.setSortOrder(0);
        category.setActive(true);
        return category;
    }

    private static ProductVariant variant(Long id, Product product, String sku) {
        ProductVariant variant = new ProductVariant();
        setId(variant, id);
        variant.setProduct(product);
        variant.setSku(sku);
        variant.setPackLabel("Khay 0,5kg");
        variant.setListPrice(new BigDecimal("100000"));
        variant.setPackPerCase((short) 10);
        variant.setActive(true);
        return variant;
    }

    private static VolumeTier tier(Long id, ProductVariant variant, int minQty, String price) {
        VolumeTier tier = new VolumeTier();
        setId(tier, id);
        tier.setVariant(variant);
        tier.setMinQtyKhay(minQty);
        tier.setUnitPrice(new BigDecimal(price));
        return tier;
    }

    private static void setId(Object entity, Long id) {
        try {
            var field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
