package com.hbsisms.feature.catalog;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.catalog.dto.TierInput;
import com.hbsisms.feature.catalog.dto.VariantRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class VariantService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final VolumeTierRepository volumeTierRepository;

    public VariantService(
            ProductRepository productRepository,
            ProductVariantRepository productVariantRepository,
            VolumeTierRepository volumeTierRepository) {
        this.productRepository = productRepository;
        this.productVariantRepository = productVariantRepository;
        this.volumeTierRepository = volumeTierRepository;
    }

    @Transactional
    public void create(Long productId, VariantRequest request) {
        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy sản phẩm"));

        String sku = requireSku(request.sku());
        String packLabel = requirePackLabel(request.packLabel());
        BigDecimal listPrice = requireListPrice(request.listPrice());
        short packPerCase = requirePackPerCase(request.packPerCase());
        assertSkuAvailable(sku, null);

        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        apply(variant, sku, packLabel, listPrice, packPerCase, request);
        ProductVariant saved = productVariantRepository.save(variant);
        replaceTiers(saved, request.tiers(), listPrice);
    }

    @Transactional
    public void update(Long id, VariantRequest request) {
        ProductVariant variant = productVariantRepository
                .findWithProductById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy quy cách"));

        String sku = requireSku(request.sku());
        String packLabel = requirePackLabel(request.packLabel());
        BigDecimal listPrice = requireListPrice(request.listPrice());
        short packPerCase = requirePackPerCase(request.packPerCase());
        assertSkuAvailable(sku, id);

        apply(variant, sku, packLabel, listPrice, packPerCase, request);
        productVariantRepository.save(variant);
        replaceTiers(variant, request.tiers(), listPrice);
    }

    private void apply(
            ProductVariant variant,
            String sku,
            String packLabel,
            BigDecimal listPrice,
            short packPerCase,
            VariantRequest request) {
        variant.setSku(sku);
        variant.setPackLabel(packLabel);
        variant.setUnitWeightG(normalizeWeight(request.unitWeightG()));
        variant.setListPrice(listPrice);
        variant.setPackPerCase(packPerCase);
        variant.setBarcode(Strings.trimToNull(request.barcode()));
        variant.setActive(Boolean.TRUE.equals(request.isActive()));
    }

    void replaceTiers(ProductVariant variant, List<TierInput> rawTiers, BigDecimal listPrice) {
        volumeTierRepository.deleteByVariant_Id(variant.getId());
        // Flush so DELETE hits DB before INSERT — otherwise uk_tier_variant_qty fires.
        volumeTierRepository.flush();
        List<TierInput> tiers = rawTiers == null ? List.of() : rawTiers;
        if (tiers.isEmpty()) {
            return;
        }

        List<TierInput> sorted = new ArrayList<>(tiers);
        sorted.sort(Comparator.comparingInt(t -> t.minQtyKhay() == null ? 0 : t.minQtyKhay()));

        Set<Integer> seenQty = new HashSet<>();
        BigDecimal previousPrice = listPrice;
        for (TierInput tier : sorted) {
            int minQty = tier.minQtyKhay() == null ? 0 : tier.minQtyKhay();
            if (minQty <= 0) {
                throw ApiException.badRequest("Mỗi bậc cần số khay lớn hơn 0");
            }
            if (!seenQty.add(minQty)) {
                throw ApiException.badRequest("Các bậc số lượng không được trùng nhau");
            }
            BigDecimal unitPrice = tier.unitPrice();
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw ApiException.badRequest("Mỗi bậc cần đơn giá lớn hơn 0");
            }
            if (unitPrice.compareTo(listPrice) > 0) {
                throw ApiException.badRequest("Giá bậc không được lớn hơn giá niêm yết");
            }
            if (unitPrice.compareTo(previousPrice) > 0) {
                throw ApiException.badRequest("Giá bậc sau phải nhỏ hơn hoặc bằng bậc trước");
            }

            VolumeTier row = new VolumeTier();
            row.setVariant(variant);
            row.setMinQtyKhay(minQty);
            row.setUnitPrice(unitPrice);
            volumeTierRepository.save(row);
            previousPrice = unitPrice;
        }
    }

    private void assertSkuAvailable(String sku, Long excludeId) {
        boolean taken = excludeId == null
                ? productVariantRepository.existsBySkuIgnoreCase(sku)
                : productVariantRepository.existsBySkuIgnoreCaseAndIdNot(sku, excludeId);
        if (taken) {
            throw ApiException.conflict("SKU " + sku + " đã tồn tại");
        }
    }

    private static String requireSku(String sku) {
        String trimmed = Strings.trimToNull(sku);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập SKU");
        }
        return trimmed;
    }

    private static String requirePackLabel(String packLabel) {
        String trimmed = Strings.trimToNull(packLabel);
        if (trimmed == null) {
            throw ApiException.badRequest("Vui lòng nhập quy cách");
        }
        return trimmed;
    }

    private static BigDecimal requireListPrice(BigDecimal listPrice) {
        if (listPrice == null || listPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("Giá niêm yết phải lớn hơn 0");
        }
        return listPrice;
    }

    private static short requirePackPerCase(Integer packPerCase) {
        if (packPerCase == null || packPerCase < 1) {
            throw ApiException.badRequest("Số khay / thùng phải từ 1 trở lên");
        }
        if (packPerCase > Short.MAX_VALUE) {
            throw ApiException.badRequest("Số khay / thùng quá lớn");
        }
        return packPerCase.shortValue();
    }

    private static Integer normalizeWeight(Integer unitWeightG) {
        if (unitWeightG == null) {
            return null;
        }
        if (unitWeightG < 0) {
            throw ApiException.badRequest("Khối lượng không hợp lệ");
        }
        return unitWeightG;
    }
}
