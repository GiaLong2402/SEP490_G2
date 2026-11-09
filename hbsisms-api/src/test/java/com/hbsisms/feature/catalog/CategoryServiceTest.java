package com.hbsisms.feature.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.dto.CategoryRequest;
import com.hbsisms.feature.catalog.dto.CategoryResponse;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    private static final Sort LIST_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"));

    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private ProductRepository productRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productRepository);
    }

    @Test
    void create_rejectsDuplicateCode() {
        when(categoryRepository.existsByCodeIgnoreCase("cha-muc")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(req("cha-muc", "Chả mực", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_trimsAndPersists() {
        when(categoryRepository.existsByCodeIgnoreCase("cha-muc")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> withId(inv.getArgument(0), 10L));

        CategoryResponse response = categoryService.create(
                new CategoryRequest(" cha-muc ", " Chả mực ", "  mô tả ", null, null, 1, true));

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.code()).isEqualTo("cha-muc");
        assertThat(response.name()).isEqualTo("Chả mực");
        assertThat(response.description()).isEqualTo("mô tả");
        assertThat(response.isActive()).isTrue();
        assertThat(response.productCount()).isZero();
    }

    @Test
    void create_rejectsMissingParent() {
        when(categoryRepository.existsByCodeIgnoreCase("cha-tom")).thenReturn(false);
        when(categoryRepository.findWithParentById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.create(req("cha-tom", "Chả tôm", 99L, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void update_rejectsSelfAsParent() {
        Category existing = withId(newCategory("cha-hai-san", "Chả hải sản", null, true), 1L);
        when(categoryRepository.findWithParentById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("cha-hai-san", 1L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.update(1L, req("cha-hai-san", "Chả hải sản", 1L, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_rejectsDescendantAsParent() {
        Category root = withId(newCategory("cha-hai-san", "Chả hải sản", null, true), 1L);
        Category child = withId(newCategory("cha-muc", "Chả mực", root, true), 2L);
        when(categoryRepository.findWithParentById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("cha-hai-san", 1L)).thenReturn(false);
        when(categoryRepository.findAll(Sort.by("id"))).thenReturn(List.of(root, child));

        assertThatThrownBy(() -> categoryService.update(1L, req("cha-hai-san", "Chả hải sản", 2L, 1, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("Loại cha không được là chính nó");
                });
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_persistsAndReturnsProductCount() {
        Category existing = withId(newCategory("cha-muc", "Chả mực", null, true), 2L);
        when(categoryRepository.findWithParentById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("cha-muc", 2L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.countByCategory_Id(2L)).thenReturn(3L);

        CategoryResponse response = categoryService.update(2L, req("cha-muc", "Chả mực giã tay", null, 5, false));

        assertThat(response.name()).isEqualTo("Chả mực giã tay");
        assertThat(response.sortOrder()).isEqualTo(5);
        assertThat(response.isActive()).isFalse();
        assertThat(response.productCount()).isEqualTo(3L);
    }

    @Test
    void list_includesParentNameAndProductCount() {
        Category parent = withId(newCategory("cha-hai-san", "Chả hải sản", null, true), 1L);
        Category child = withId(newCategory("cha-muc", "Chả mực", parent, true), 2L);
        when(categoryRepository.findAll(LIST_SORT)).thenReturn(List.of(parent, child));
        when(productRepository.countByCategory_Id(1L)).thenReturn(0L);
        when(productRepository.countByCategory_Id(2L)).thenReturn(2L);

        List<CategoryResponse> list = categoryService.list();

        assertThat(list).hasSize(2);
        assertThat(list.get(0).parentName()).isNull();
        assertThat(list.get(1).parentId()).isEqualTo(1L);
        assertThat(list.get(1).parentName()).isEqualTo("Chả hải sản");
        assertThat(list.get(1).productCount()).isEqualTo(2L);
    }

    @Test
    void create_rejectsBlankCode() {
        assertThatThrownBy(() -> categoryService.create(req("  ", "Chả mực", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).isEqualTo("Vui lòng nhập mã loại");
                });
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_rejectsBlankName() {
        assertThatThrownBy(() -> categoryService.create(req("cha-muc", "  ", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(api.getMessage()).isEqualTo("Vui lòng nhập tên loại");
                });
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void create_withValidParent_persistsParentLink() {
        Category parent = withId(newCategory("cha-hai-san", "Chả hải sản", null, true), 1L);
        when(categoryRepository.existsByCodeIgnoreCase("cha-muc")).thenReturn(false);
        when(categoryRepository.findWithParentById(1L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> withId(inv.getArgument(0), 2L));

        CategoryResponse response = categoryService.create(req("cha-muc", "Chả mực", 1L, 2, true));

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.parentId()).isEqualTo(1L);
        assertThat(response.parentName()).isEqualTo("Chả hải sản");
    }

    @Test
    void create_persistsInactiveWhenRequested() {
        when(categoryRepository.existsByCodeIgnoreCase("ngung")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> {
            Category saved = inv.getArgument(0);
            assertThat(saved.isActive()).isFalse();
            return withId(saved, 3L);
        });

        CategoryResponse response = categoryService.create(req("ngung", "Ngừng", null, 1, false));

        assertThat(response.isActive()).isFalse();
    }

    @Test
    void update_notFoundWhenCategoryMissing() {
        when(categoryRepository.findWithParentById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, req("x", "Y", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_rejectsDuplicateCodeOnOtherId() {
        Category existing = withId(newCategory("cha-muc", "Chả mực", null, true), 2L);
        when(categoryRepository.findWithParentById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("cha-tom", 2L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.update(2L, req("cha-tom", "Chả mực", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_allowsSameCodeAsSelf() {
        Category existing = withId(newCategory("cha-muc", "Chả mực", null, true), 2L);
        when(categoryRepository.findWithParentById(2L)).thenReturn(Optional.of(existing));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("cha-muc", 2L)).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));
        when(productRepository.countByCategory_Id(2L)).thenReturn(0L);

        CategoryResponse response = categoryService.update(2L, req("cha-muc", "Chả mực mới", null, 3, true));

        assertThat(response.code()).isEqualTo("cha-muc");
        assertThat(response.name()).isEqualTo("Chả mực mới");
    }

    @Test
    void update_rejectsBlankCodeAndName() {
        Category existing = withId(newCategory("cha-muc", "Chả mực", null, true), 2L);
        when(categoryRepository.findWithParentById(2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> categoryService.update(2L, req("  ", "Chả mực", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThatThrownBy(() -> categoryService.update(2L, req("cha-muc", " ", null, 1, true)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getStatus())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void update_rejectsGrandchildAsParentOfRoot() {
        Category root = withId(newCategory("root", "Root", null, true), 1L);
        Category child = withId(newCategory("child", "Child", root, true), 2L);
        Category grandchild = withId(newCategory("grand", "Grand", child, true), 3L);
        when(categoryRepository.findWithParentById(1L)).thenReturn(Optional.of(root));
        when(categoryRepository.existsByCodeIgnoreCaseAndIdNot("root", 1L)).thenReturn(false);
        when(categoryRepository.findAll(Sort.by("id"))).thenReturn(List.of(root, child, grandchild));

        assertThatThrownBy(() -> categoryService.update(1L, req("root", "Root", 3L, 1, true)))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException api = (ApiException) ex;
                    assertThat(api.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(api.getMessage()).contains("Loại cha không được là chính nó");
                });
        verify(categoryRepository, never()).save(any());
    }

    private static CategoryRequest req(
            String code, String name, Long parentId, int sortOrder, boolean active) {
        return new CategoryRequest(code, name, null, null, parentId, sortOrder, active);
    }

    private static Category newCategory(String code, String name, Category parent, boolean active) {
        Category category = new Category();
        category.setCode(code);
        category.setName(name);
        category.setParent(parent);
        category.setSortOrder(0);
        category.setActive(active);
        return category;
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
}
