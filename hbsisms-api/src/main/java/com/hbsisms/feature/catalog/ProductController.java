package com.hbsisms.feature.catalog;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.catalog.dto.ProductRequest;
import com.hbsisms.feature.catalog.dto.ProductResponse;
import com.hbsisms.feature.catalog.dto.SellPriceHistoryResponse;
import com.hbsisms.feature.media.CloudinaryStorageService;
import com.hbsisms.feature.media.dto.ImageUploadResponse;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final SellPriceSuggestionService sellPriceSuggestionService;

    public ProductController(
            ProductService productService,
            CloudinaryStorageService cloudinaryStorageService,
            SellPriceSuggestionService sellPriceSuggestionService) {
        this.productService = productService;
        this.cloudinaryStorageService = cloudinaryStorageService;
        this.sellPriceSuggestionService = sellPriceSuggestionService;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        if (isGuest()) {
            return productService.listPublic(q, categoryId, page, pageSize);
        }
        return productService.list(q, categoryId, page, pageSize);
    }

    @GetMapping("/slug/{slug}")
    public ProductResponse getBySlug(@PathVariable String slug) {
        if (isGuest()) {
            return productService.getBySlugPublic(slug);
        }
        return productService.getBySlug(slug);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        if (isGuest()) {
            return productService.getPublic(id);
        }
        return productService.get(id);
    }

    /** Task 59 — lịch sử giá bán theo giai đoạn (mọi quy cách của SP). */
    @GetMapping("/{id}/sell-price-history")
    @PreAuthorize("hasAuthority('products')")
    public List<SellPriceHistoryResponse> sellPriceHistory(@PathVariable Long id) {
        productService.get(id);
        return sellPriceSuggestionService.historyForProduct(id);
    }

    /**
     * Upload product images to Cloudinary. Caller picks one URL as cover ({@code imageUrl})
     * and the rest as {@code gallery} when creating/updating the product.
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public ImageUploadResponse uploadImages(@RequestParam("files") MultipartFile[] files) {
        List<MultipartFile> list = files == null ? List.of() : Arrays.asList(files);
        return new ImageUploadResponse(cloudinaryStorageService.uploadImages(list));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public ProductResponse create(@Valid @RequestBody ProductRequest request) {
        return productService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('products') and @roles.ownerOrAdmin()")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.update(id, request);
    }

    /** True when there is no JWT principal (AnonymousAuthenticationToken or null). */
    private static boolean isGuest() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken;
    }
}
