package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.dto.CategoryRequest;
import com.hbsisms.feature.catalog.dto.CategoryResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final Sort LIST_SORT = Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("name"));

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    public List<CategoryResponse> list() {
        List<Category> categories = categoryRepository.findAll(LIST_SORT);
        Map<Long, Long> productCounts = productCountsByCategoryId(categories);
        List<CategoryResponse> result = new ArrayList<>(categories.size());
        for (Category category : categories) {
            result.add(CategoryResponse.from(category, productCounts.getOrDefault(category.getId(), 0L)));
        }
        return result;
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String code = requireCode(request.code());
        String name = requireName(request.name());
        assertCodeAvailable(code, null);
        Category parent = resolveParent(request.parentId(), null);

        Category category = new Category();
        apply(category, code, name, request, parent);
        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved, 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = categoryRepository
                .findWithParentById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại sản phẩm"));

        String code = requireCode(request.code());
        String name = requireName(request.name());
        assertCodeAvailable(code, id);
        Category parent = resolveParent(request.parentId(), id);

        apply(category, code, name, request, parent);
        Category saved = categoryRepository.save(category);
        return CategoryResponse.from(saved, productRepository.countByCategory_Id(id));
    }

    private void apply(Category category, String code, String name, CategoryRequest request, Category parent) {
        category.setCode(code);
        category.setName(name);
        category.setDescription(Strings.trimToNull(request.description()));
        category.setImageUrl(Strings.trimToNull(request.imageUrl()));
        category.setParent(parent);
        category.setSortOrder(request.sortOrder());
        category.setActive(Boolean.TRUE.equals(request.isActive()));
    }

    private Category resolveParent(Long parentId, Long currentId) {
        if (parentId == null) {
            return null;
        }
        assertParentNotSelfOrDescendant(parentId, currentId);
        return categoryRepository
                .findWithParentById(parentId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại cha"));
    }

    /**
     * Walks ancestors of {@code parentId}. Fails if {@code currentId} is an ancestor of the chosen
     * parent (would create a cycle), or if parent is the category itself.
     */
    void assertParentNotSelfOrDescendant(Long parentId, Long currentId) {
        if (parentId == null || currentId == null) {
            return;
        }
        if (parentId.equals(currentId)) {
            throw ApiException.conflict("Loại cha không được là chính nó hoặc loại con của nó");
        }
        Map<Long, Long> parentById = new HashMap<>();
        for (Category category : categoryRepository.findAll(Sort.by("id"))) {
            Long p = category.getParent() == null ? null : category.getParent().getId();
            parentById.put(category.getId(), p);
        }
        Long cursor = parentId;
        while (cursor != null) {
            if (cursor.equals(currentId)) {
                throw ApiException.conflict("Loại cha không được là chính nó hoặc loại con của nó");
            }
            cursor = parentById.get(cursor);
        }
    }

    private static String requireCode(String code) {
        String trimmed = Strings.trimToNull(code);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã loại");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập tên loại");
        }
        return trimmed;
    }

    private void assertCodeAvailable(String code, Long currentId) {
        boolean taken = currentId == null
                ? categoryRepository.existsByCodeIgnoreCase(code)
                : categoryRepository.existsByCodeIgnoreCaseAndIdNot(code, currentId);
        if (taken) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
    }

    private Map<Long, Long> productCountsByCategoryId(List<Category> categories) {
        Map<Long, Long> counts = new HashMap<>();
        for (Category category : categories) {
            counts.put(category.getId(), productRepository.countByCategory_Id(category.getId()));
        }
        return counts;
    }
}
