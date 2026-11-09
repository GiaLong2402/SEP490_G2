package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.dto.ProductRequest;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import com.hbsisms.feature.catalog.dto.VariantWithTiersResponse;
import com.hbsisms.feature.inventory.StockRepository;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private static final Sort LIST_SORT = Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"));

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VolumeTierRepository volumeTierRepository;
    private final StockRepository stockRepository;

    public ProductService(
            ProductRepository productRepository,
            CategoryRepository categoryRepository,
            ProductVariantRepository productVariantRepository,
            VolumeTierRepository volumeTierRepository,
            StockRepository stockRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.productVariantRepository = productVariantRepository;
        this.volumeTierRepository = volumeTierRepository;
        this.stockRepository = stockRepository;
    }

    public PageResponse<ProductResponse> list(String q, Long categoryId, int page, int pageSize) {
        List<Long> categoryIds = categoryId == null ? List.of() : categoryIdsIncludingDescendants(categoryId);
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (!categoryIds.isEmpty()) {
                predicates.add(root.get("category").get("id").in(categoryIds));
            }
            String needle = Strings.trimToNull(q);
            if (needle != null) {
                String pattern = "%" + Strings.escapeLike(needle.toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), pattern, '\\'),
                        cb.like(cb.lower(root.get("name")), pattern, '\\')));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(productRepository
                .findAll(spec, PageRequests.of(page, pageSize, LIST_SORT))
                .map(this::toResponse));
    }

    /** Selected category plus every descendant in the category tree. */
    private List<Long> categoryIdsIncludingDescendants(Long categoryId) {
        List<Category> all = categoryRepository.findAll(Sort.by(Sort.Order.asc("id")));
        Map<Long, List<Long>> childrenByParentId = new HashMap<>();
        for (Category category : all) {
            Category parent = category.getParent();
            if (parent == null) {
                continue;
            }
            childrenByParentId.computeIfAbsent(parent.getId(), ignored -> new ArrayList<>()).add(category.getId());
        }
        List<Long> ids = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            ids.add(id);
            List<Long> children = childrenByParentId.get(id);
            if (children != null) {
                queue.addAll(children);
            }
        }
        return ids;
    }

    public ProductResponse get(Long id) {
        Product product = productRepository
                .findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        return toResponse(product);
    }

    public ProductResponse getBySlug(String slug) {
        String normalized = Strings.trimToNull(slug);
        if (normalized == null) {
            throw ApiException.notFound("Không tìm thấy sản phẩm");
        }
        Product product = productRepository
                .findWithCategoryBySlugIgnoreCase(normalized)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String code = requireCode(request.code());
        String name = requireName(request.name());
        assertCodeAvailable(code, null);
        assertCanPublish(null, request.isPublished());
        Category category = resolveCategory(request.categoryId());
        String slug = uniqueSlug(Strings.slugify(name), null);

        Product product = new Product();
        apply(product, code, name, slug, category, request, true);
        Product saved = productRepository.save(product);
        return toResponse(productRepository.findWithCategoryById(saved.getId()).orElse(saved));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository
                .findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));

        String code = requireCode(request.code());
        String name = requireName(request.name());
        assertCodeAvailable(code, id);
        assertCanPublish(id, request.isPublished());
        Category category = resolveCategory(request.categoryId());

        String slug = Strings.trimToNull(product.getSlug());
        if (slug == null) {
            slug = uniqueSlug(Strings.slugify(name), id);
        }

        apply(product, code, name, slug, category, request, false);
        Product saved = productRepository.save(product);
        return toResponse(productRepository.findWithCategoryById(saved.getId()).orElse(saved));
    }

    private ProductResponse toResponse(Product product) {
        List<ProductVariant> variants = product.getId() == null
                ? List.of()
                : productVariantRepository.findByProduct_IdOrderByIdAsc(product.getId());
        List<VariantWithTiersResponse> variantRows = new ArrayList<>(variants.size());
        for (ProductVariant variant : variants) {
            List<VolumeTier> tiers = volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(variant.getId());
            long available = stockRepository.sumQtyAvailableByVariantId(variant.getId());
            variantRows.add(VariantWithTiersResponse.from(variant, tiers, available));
        }
        return ProductResponse.from(product, variantRows);
    }

    private void apply(
            Product product,
            String code,
            String name,
            String slug,
            Category category,
            ProductRequest request,
            boolean creating) {
        product.setCode(code);
        product.setName(name);
        product.setSlug(slug);
        product.setCategory(category);
        product.setDescription(Strings.trimToNull(request.description()));
        product.setStorageNote(Strings.trimToNull(request.storageNote()));
        product.setOrigin(Strings.trimToNull(request.origin()));
        product.setImageUrl(normalizeImageUrl(request.imageUrl()));
        if (creating) {
            product.setGallery(cleanGallery(request.gallery()));
        } else if (request.gallery() != null) {
            product.setGallery(cleanGallery(request.gallery()));
        }
        product.setPublished(Boolean.TRUE.equals(request.isPublished()));
        product.setActive(Boolean.TRUE.equals(request.isActive()));
    }

    private static String normalizeImageUrl(String imageUrl) {
        String trimmed = Strings.trimToNull(imageUrl);
        if (trimmed != null && trimmed.length() > 500) {
            throw ApiException.badRequest("URL ảnh chính tối đa 500 ký tự");
        }
        return trimmed;
    }

    /** Trims entries, drops blanks; each URL max 500 chars. Never null — returns empty list. */
    private static List<String> cleanGallery(List<String> gallery) {
        if (gallery == null || gallery.isEmpty()) {
            return List.of();
        }
        List<String> cleaned = new ArrayList<>();
        for (String raw : gallery) {
            String url = Strings.trimToNull(raw);
            if (url == null) {
                continue;
            }
            if (url.length() > 500) {
                throw ApiException.badRequest("Mỗi URL gallery tối đa 500 ký tự");
            }
            cleaned.add(url);
        }
        return cleaned;
    }

    private Category resolveCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy loại sản phẩm"));
    }

    private String uniqueSlug(String base, Long excludeId) {
        if (base == null) {
            throw ApiException.badRequest("Không tạo được slug từ tên sản phẩm");
        }
        String candidate = base;
        int suffix = 2;
        while (slugTaken(candidate, excludeId)) {
            candidate = base + "-" + suffix;
            suffix++;
            if (suffix > 10_000) {
                throw ApiException.conflict("Không tạo được slug duy nhất cho sản phẩm");
            }
        }
        return candidate;
    }

    private boolean slugTaken(String slug, Long excludeId) {
        if (excludeId == null) {
            return productRepository.existsBySlugIgnoreCase(slug);
        }
        return productRepository.existsBySlugIgnoreCaseAndIdNot(slug, excludeId);
    }

    private void assertCodeAvailable(String code, Long excludeId) {
        boolean taken = excludeId == null
                ? productRepository.existsByCodeIgnoreCase(code)
                : productRepository.existsByCodeIgnoreCaseAndIdNot(code, excludeId);
        if (taken) {
            throw ApiException.conflict("Mã " + code + " đã tồn tại");
        }
    }

    /**
     * B2C publish requires at least one active variant. Create always fails when published
     * because the product has no variants yet.
     */
    private void assertCanPublish(Long productId, Boolean isPublished) {
        if (!Boolean.TRUE.equals(isPublished)) {
            return;
        }
        if (productId == null || !productVariantRepository.existsByProduct_IdAndActiveTrue(productId)) {
            throw ApiException.badRequest("Không thể hiển thị cổng B2C khi chưa có quy cách đang bán");
        }
    }

    private static String requireCode(String code) {
        String trimmed = Strings.trimToNull(code);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập mã sản phẩm");
        }
        return trimmed;
    }

    private static String requireName(String name) {
        String trimmed = Strings.trimToNull(name);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập tên sản phẩm");
        }
        return trimmed;
    }
}
