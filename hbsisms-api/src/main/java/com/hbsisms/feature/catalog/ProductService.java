package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.dto.ProductLotRowResponse;
import com.hbsisms.feature.catalog.dto.ProductRequest;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import com.hbsisms.feature.catalog.dto.VariantWithTiersResponse;
import com.hbsisms.feature.inventory.Lot;
import com.hbsisms.feature.inventory.Stock;
import com.hbsisms.feature.inventory.StockRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
        return listInternal(q, categoryId, page, pageSize, false);
    }

    /**
     * B2C guest catalog list: only published + active products; {@code available} masked as 0|1.
     */
    public PageResponse<ProductResponse> listPublic(String q, Long categoryId, int page, int pageSize) {
        return listInternal(q, categoryId, page, pageSize, true);
    }

    private PageResponse<ProductResponse> listInternal(
            String q, Long categoryId, int page, int pageSize, boolean publicView) {
        List<Long> categoryIds = categoryId == null ? List.of() : categoryIdsIncludingDescendants(categoryId);
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (publicView) {
                predicates.add(cb.isTrue(root.get("published")));
                predicates.add(cb.isTrue(root.get("active")));
            }
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
                .map(publicView ? this::toPublicListResponse : this::toListResponse));
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
        return toResponse(requireBySlug(slug));
    }

    /**
     * B2C guest detail by slug: published only; {@code available} is real tray qty (not binary).
     */
    public ProductResponse getBySlugPublic(String slug) {
        Product product = requireBySlug(slug);
        assertPubliclyVisible(product);
        return toPublicDetailResponse(product);
    }

    /** B2C guest detail by id: published only; {@code available} is real tray qty. */
    public ProductResponse getPublic(Long id) {
        Product product = productRepository
                .findWithCategoryById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
        assertPubliclyVisible(product);
        return toPublicDetailResponse(product);
    }

    private Product requireBySlug(String slug) {
        String normalized = Strings.trimToNull(slug);
        if (normalized == null) {
            throw ApiException.notFound("Không tìm thấy sản phẩm");
        }
        return productRepository
                .findWithCategoryBySlugIgnoreCase(normalized)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));
    }

    private static void assertPubliclyVisible(Product product) {
        if (!product.isPublished() || !product.isActive()) {
            throw ApiException.notFound("Không tìm thấy sản phẩm");
        }
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
        assertNotRawMaterialFlag(request);
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

        assertNotRawMaterialFlag(request);
        apply(product, code, name, slug, category, request, false);
        Product saved = productRepository.save(product);
        return toResponse(productRepository.findWithCategoryById(saved.getId()).orElse(saved));
    }

    /** Staff detail / create / update — includes lot rows. */
    private ProductResponse toResponse(Product product) {
        return toResponse(product, false, false, true);
    }

    /** Staff list — total stock only, no per-lot payload. */
    private ProductResponse toListResponse(Product product) {
        return toResponse(product, false, false, false);
    }

    /** Guest list card: hide inactive variants; mask stock as còn/hết (0|1). */
    private ProductResponse toPublicListResponse(Product product) {
        return toResponse(product, true, true, false);
    }

    /** Guest product detail: hide inactive variants; expose real available trays. */
    private ProductResponse toPublicDetailResponse(Product product) {
        return toResponse(product, true, false, false);
    }

    /**
     * @param publicView only active variants; used for B2C guest payloads
     * @param maskStockBinary when true, {@code available} is 0 or 1 (list); when false, real qty (detail/staff)
     * @param includeLots when true, embed aggregated lot rows (staff product detail)
     */
    private ProductResponse toResponse(
            Product product, boolean publicView, boolean maskStockBinary, boolean includeLots) {
        List<ProductVariant> variants = product.getId() == null
                ? List.of()
                : productVariantRepository.findByProduct_IdOrderByIdAsc(product.getId());
        List<VariantWithTiersResponse> variantRows = new ArrayList<>();
        for (ProductVariant variant : variants) {
            if (publicView && !variant.isActive()) {
                continue;
            }
            List<VolumeTier> tiers = volumeTierRepository.findByVariant_IdOrderByMinQtyKhayAsc(variant.getId());
            long available = stockRepository.sumQtyAvailableByVariantId(variant.getId());
            if (maskStockBinary) {
                available = available > 0 ? 1L : 0L;
            }
            variantRows.add(VariantWithTiersResponse.from(variant, tiers, available));
        }
        long totalStockQty;
        List<ProductLotRowResponse> lots = List.of();
        if (product.getId() == null) {
            totalStockQty = 0;
        } else if (publicView || maskStockBinary) {
            totalStockQty = variantRows.stream().mapToLong(VariantWithTiersResponse::available).sum();
        } else {
            totalStockQty = stockRepository.sumQtyAvailableByProductId(product.getId());
            if (includeLots) {
                lots = lotRowsForProduct(product.getId());
            }
        }
        return ProductResponse.from(product, variantRows, totalStockQty, lots);
    }

    /** Aggregate stock by lot across locations; FEFO by expDate (nulls last). */
    private List<ProductLotRowResponse> lotRowsForProduct(Long productId) {
        LocalDate today = LocalDate.now();
        Map<Long, ProductLotRowResponse> byLotId = new LinkedHashMap<>();
        for (Stock stock : stockRepository.findByProductIdWithLotVariant(productId)) {
            Lot lot = stock.getLot();
            ProductVariant variant = stock.getVariant();
            ProductLotRowResponse existing = byLotId.get(lot.getId());
            int available = existing == null ? stock.getQtyAvailable() : existing.available() + stock.getQtyAvailable();
            Integer daysLeft = lot.getExpDate() == null
                    ? null
                    : (int) ChronoUnit.DAYS.between(today, lot.getExpDate());
            byLotId.put(
                    lot.getId(),
                    new ProductLotRowResponse(
                            lot.getId(),
                            lot.getLotCode(),
                            variant.getId(),
                            variant.getSku(),
                            lot.getMfgDate(),
                            lot.getExpDate(),
                            daysLeft,
                            available));
        }
        return byLotId.values().stream()
                .sorted(Comparator
                        .comparing(
                                (ProductLotRowResponse r) ->
                                        r.expDate() == null ? LocalDate.of(9999, 12, 31) : r.expDate())
                        .thenComparing(ProductLotRowResponse::lotId))
                .toList();
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
        // Dual-role off for now: catalog product = sellable; NVL stays on RawMaterial CRUD.
        product.setRawMaterial(false);
        product.setSellable(pick(request.isSellable(), product.isSellable()));
        product.setProducible(pick(request.isProducible(), product.isProducible()));
        product.setUnit(null);
    }

    private static boolean pick(Boolean requested, boolean current) {
        return requested == null ? current : requested;
    }

    /** Mentor: temporarily no “vừa NVL vừa bán” on products. */
    private static void assertNotRawMaterialFlag(ProductRequest request) {
        if (Boolean.TRUE.equals(request.isRawMaterial())) {
            throw ApiException.badRequest("Tạm thời nguyên liệu quản lý riêng, không đánh dấu trên sản phẩm");
        }
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
