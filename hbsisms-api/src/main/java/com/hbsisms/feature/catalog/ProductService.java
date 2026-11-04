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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
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

    public ProductResponse get(Long id) {
        Product product = productRepository
                .findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        return toResponse(product);
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String code = requireCode(request.code());
        String name = requireName(request.name());
        assertCodeAvailable(code, null);
        Category category = resolveCategory(request.categoryId());
        String slug = uniqueSlug(Strings.slugify(name), null);

        Product product = new Product();
        apply(product, code, name, slug, category, request);
        product.setImageUrl(null);
        product.setGallery(List.of());
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
        Category category = resolveCategory(request.categoryId());

        String slug = Strings.trimToNull(product.getSlug());
        if (slug == null) {
            slug = uniqueSlug(Strings.slugify(name), id);
        }

        apply(product, code, name, slug, category, request);
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
            Product product, String code, String name, String slug, Category category, ProductRequest request) {
        product.setCode(code);
        product.setName(name);
        product.setSlug(slug);
        product.setCategory(category);
        product.setDescription(Strings.trimToNull(request.description()));
        product.setStorageNote(Strings.trimToNull(request.storageNote()));
        product.setOrigin(Strings.trimToNull(request.origin()));
        product.setPublished(Boolean.TRUE.equals(request.isPublished()));
        product.setActive(Boolean.TRUE.equals(request.isActive()));
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
