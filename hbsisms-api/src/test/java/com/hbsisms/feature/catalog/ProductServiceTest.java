package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.ProductRequest;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import com.hbsisms.feature.inventory.StockRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

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

    // --- create ---

    @Test
    void create_trimsAndPersistsWithCategoryAndGallery() {
        Category category = withId(newCategory("cha-muc", "Chả mực"), 5L);
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(categoryRepository.findById(5L)).thenReturn(Optional.of(category));
        when(productRepository.existsBySlugIgnoreCase("cha-muc-gia-tay")).thenReturn(false);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> withId((Product) inv.getArgument(0), 20L));
        when(productRepository.findWithCategoryById(20L)).thenAnswer(inv -> {
            Product p = withId(new Product(), 20L);
            p.setCode("P-TOM");
            p.setName("Chả mực giã tay");
            p.setSlug("cha-muc-gia-tay");
            p.setCategory(category);
            p.setGallery(List.of("https://img/a.jpg"));
            p.setPublished(false);
            p.setActive(true);
            return Optional.of(p);
        });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(20L)).thenReturn(List.of());

        ProductResponse response = productService.create(new ProductRequest(
                " P-TOM ",
                " Chả mực giã tay ",
                5L,
                "  mô tả ",
                "  bảo quản ",
                "  VN ",
                " https://img/main.jpg ",
                new ArrayList<>(Arrays.asList(" https://img/a.jpg ", "  ", null)),
                false,
                true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        Product saved = captor.getValue();
        assertThat(saved.getCode()).isEqualTo("P-TOM");
        assertThat(saved.getName()).isEqualTo("Chả mực giã tay");
        assertThat(saved.getSlug()).isEqualTo("cha-muc-gia-tay");
        assertThat(saved.getCategory()).isEqualTo(category);
        assertThat(saved.getDescription()).isEqualTo("mô tả");
        assertThat(saved.getStorageNote()).isEqualTo("bảo quản");
        assertThat(saved.getOrigin()).isEqualTo("VN");
        assertThat(saved.getImageUrl()).isEqualTo("https://img/main.jpg");
        assertThat(saved.getGallery()).containsExactly("https://img/a.jpg");
        assertThat(saved.isPublished()).isFalse();
        assertThat(saved.isActive()).isTrue();
        assertThat(response.id()).isEqualTo(20L);
        assertThat(response.code()).isEqualTo("P-TOM");
    }

    @Test
    void create_rejectsBlankCode() {
        assertBadRequest(() -> productService.create(req("  ", "Tôm", null, false, true)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsBlankName() {
        assertBadRequest(() -> productService.create(req("P-1", "   ", null, false, true)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(req("P-TOM", "Tôm", null, false, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("P-TOM");
                });
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsPublishWithoutActiveVariant() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);

        assertThatThrownBy(() -> productService.create(req("P-TOM", "Tôm", null, true, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("chưa có quy cách đang bán");
                });
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsMissingCategory() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(req("P-TOM", "Tôm", 99L, false, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_allowsNullCategory() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCase("tom")).thenReturn(false);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> withId((Product) inv.getArgument(0), 1L));
        when(productRepository.findWithCategoryById(1L)).thenAnswer(inv -> {
            Product p = withId(new Product(), 1L);
            p.setCode("P-TOM");
            p.setName("Tôm");
            p.setSlug("tom");
            p.setPublished(false);
            p.setActive(true);
            return Optional.of(p);
        });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(1L)).thenReturn(List.of());

        ProductResponse response = productService.create(req("P-TOM", "Tôm", null, false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getCategory()).isNull();
        assertThat(response.code()).isEqualTo("P-TOM");
    }

    @Test
    void create_rejectsImageUrlTooLong() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCase("tom")).thenReturn(false);

        String longUrl = "https://x/" + "a".repeat(500);
        assertBadRequest(() -> productService.create(new ProductRequest(
                "P-TOM", "Tôm", null, null, null, null, longUrl, null, false, true)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_rejectsGalleryUrlTooLong() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCase("tom")).thenReturn(false);

        String longUrl = "https://x/" + "a".repeat(500);
        assertBadRequest(() -> productService.create(new ProductRequest(
                "P-TOM", "Tôm", null, null, null, null, null, List.of(longUrl), false, true)));
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_appendsSuffixWhenSlugTaken() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCase("tom")).thenReturn(true);
        when(productRepository.existsBySlugIgnoreCase("tom-2")).thenReturn(true);
        when(productRepository.existsBySlugIgnoreCase("tom-3")).thenReturn(false);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> withId((Product) inv.getArgument(0), 1L));
        when(productRepository.findWithCategoryById(1L)).thenAnswer(inv -> {
            Product p = withId(new Product(), 1L);
            p.setCode("P-TOM");
            p.setName("Tôm");
            p.setSlug("tom-3");
            p.setPublished(false);
            p.setActive(true);
            return Optional.of(p);
        });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(1L)).thenReturn(List.of());

        productService.create(req("P-TOM", "Tôm", null, false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("tom-3");
    }

    @Test
    void create_rejectsWhenSlugifyFails() {
        when(productRepository.existsByCodeIgnoreCase("P-X")).thenReturn(false);

        assertThatThrownBy(() -> productService.create(req("P-X", "@@@", null, false, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("Không tạo được slug");
                });
        verify(productRepository, never()).save(any());
    }

    @Test
    void create_persistsInactiveWhenRequested() {
        when(productRepository.existsByCodeIgnoreCase("P-TOM")).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCase("tom")).thenReturn(false);
        when(productRepository.save(any(Product.class)))
                .thenAnswer(inv -> withId((Product) inv.getArgument(0), 1L));
        when(productRepository.findWithCategoryById(1L)).thenAnswer(inv -> {
            Product p = withId(new Product(), 1L);
            p.setCode("P-TOM");
            p.setName("Tôm");
            p.setSlug("tom");
            p.setPublished(false);
            p.setActive(false);
            return Optional.of(p);
        });
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(1L)).thenReturn(List.of());

        productService.create(req("P-TOM", "Tôm", null, false, false));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().isActive()).isFalse();
    }

    // --- update ---

    @Test
    void update_notFoundWhenProductMissing() {
        when(productRepository.findWithCategoryById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(99L, req("P-TOM", "Tôm", null, false, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_keepsExistingSlug() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-OLD");
        existing.setName("Cũ");
        existing.setSlug("slug-cu");
        existing.setGallery(List.of("https://old.jpg"));
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-NEW", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(7L, req("P-NEW", "Tôm mới", null, false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("slug-cu");
        assertThat(captor.getValue().getCode()).isEqualTo("P-NEW");
        assertThat(captor.getValue().getName()).isEqualTo("Tôm mới");
        verify(productRepository, never()).existsBySlugIgnoreCase(anyString());
        verify(productRepository, never()).existsBySlugIgnoreCaseAndIdNot(anyString(), anyLong());
    }

    @Test
    void update_generatesSlugWhenMissing() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-OLD");
        existing.setName("Cũ");
        existing.setSlug("  ");
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productRepository.existsBySlugIgnoreCaseAndIdNot("tom", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(7L, req("P-TOM", "Tôm", null, false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getSlug()).isEqualTo("tom");
    }

    @Test
    void update_rejectsDuplicateCodeOnOtherId() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-OLD");
        existing.setName("Cũ");
        existing.setSlug("cu");
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TAKEN", 7L)).thenReturn(true);

        assertThatThrownBy(() -> productService.update(7L, req("P-TAKEN", "Tôm", null, false, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_allowsSameCodeAsSelf() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(7L, req("P-TOM", "Tôm cập nhật", null, false, true));

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void update_rejectsPublishWithoutActiveVariant() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productVariantRepository.existsByProduct_IdAndActiveTrue(7L)).thenReturn(false);

        assertThatThrownBy(() -> productService.update(7L, req("P-TOM", "Tôm", null, true, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).contains("chưa có quy cách đang bán");
                });
        verify(productRepository, never()).save(any());
    }

    @Test
    void update_allowsPublishWhenActiveVariantExists() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productVariantRepository.existsByProduct_IdAndActiveTrue(7L)).thenReturn(true);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(7L, req("P-TOM", "Tôm", null, true, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().isPublished()).isTrue();
    }

    @Test
    void update_nullGalleryKeepsExisting() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        existing.setGallery(List.of("https://keep.jpg"));
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(
                7L,
                new ProductRequest("P-TOM", "Tôm", null, null, null, null, null, null, false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getGallery()).containsExactly("https://keep.jpg");
    }

    @Test
    void update_emptyGalleryClearsExisting() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        existing.setGallery(List.of("https://old.jpg"));
        existing.setPublished(false);
        existing.setActive(true);

        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productVariantRepository.findByProduct_IdOrderByIdAsc(7L)).thenReturn(List.of());

        productService.update(
                7L,
                new ProductRequest("P-TOM", "Tôm", null, null, null, null, null, List.of(), false, true));

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getGallery()).isEmpty();
    }

    @Test
    void update_rejectsMissingCategory() {
        Product existing = withId(new Product(), 7L);
        existing.setCode("P-TOM");
        existing.setName("Tôm");
        existing.setSlug("tom");
        when(productRepository.findWithCategoryById(7L)).thenReturn(Optional.of(existing));
        when(productRepository.existsByCodeIgnoreCaseAndIdNot("P-TOM", 7L)).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.update(7L, req("P-TOM", "Tôm", 99L, false, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(productRepository, never()).save(any());
    }

    private static ProductRequest req(
            String code, String name, Long categoryId, boolean published, boolean active) {
        return new ProductRequest(code, name, categoryId, null, null, null, null, null, published, active);
    }

    private static Category newCategory(String code, String name) {
        Category category = new Category();
        category.setCode(code);
        category.setName(name);
        category.setSortOrder(0);
        category.setActive(true);
        return category;
    }

    private static Product withId(Product product, Long id) {
        try {
            var field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
            return product;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static Category withId(Category category, Long id) {
        try {
            var field = Category.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(category, id);
            return category;
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void assertBadRequest(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
