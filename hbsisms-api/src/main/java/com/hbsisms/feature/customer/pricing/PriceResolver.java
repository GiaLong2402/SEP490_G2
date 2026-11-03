package com.hbsisms.feature.customer.pricing;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.CustomerPrice;
import com.hbsisms.feature.catalog.CustomerPriceRepository;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.catalog.VolumeTier;
import com.hbsisms.feature.catalog.VolumeTierRepository;
import com.hbsisms.feature.sales.PriceBasis;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task 22 — tính giá áp dụng cho (khách, variant, SL khay).
 * Ưu tiên: giá riêng đã duyệt còn hiệu lực → bậc số lượng → niêm yết.
 * Service nội bộ cho module bán hàng; chữ ký ổn định.
 */
@Service
@Transactional(readOnly = true)
public class PriceResolver {

    private final ProductVariantRepository productVariantRepository;
    private final CustomerPriceRepository customerPriceRepository;
    private final VolumeTierRepository volumeTierRepository;

    public PriceResolver(
            ProductVariantRepository productVariantRepository,
            CustomerPriceRepository customerPriceRepository,
            VolumeTierRepository volumeTierRepository) {
        this.productVariantRepository = productVariantRepository;
        this.customerPriceRepository = customerPriceRepository;
        this.volumeTierRepository = volumeTierRepository;
    }

    public ResolvedPrice resolve(Long customerId, Long variantId, int qtyKhay) {
        if (variantId == null) {
            throw ApiException.badRequest("variantId là bắt buộc");
        }
        if (qtyKhay <= 0) {
            throw ApiException.badRequest("Số lượng khay phải lớn hơn 0");
        }

        ProductVariant variant = productVariantRepository
                .findById(variantId)
                .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));

        if (customerId != null) {
            CustomerPrice special = customerPriceRepository
                    .findEffectiveSpecial(customerId, variantId, LocalDate.now())
                    .orElse(null);
            if (special != null) {
                return new ResolvedPrice(special.getUnitPrice(), PriceBasis.CUSTOMER);
            }
        }

        List<VolumeTier> tiers = volumeTierRepository
                .findByVariant_IdAndMinQtyKhayLessThanEqualOrderByMinQtyKhayDesc(variantId, qtyKhay);
        if (!tiers.isEmpty()) {
            return new ResolvedPrice(tiers.get(0).getUnitPrice(), PriceBasis.TIER);
        }

        return new ResolvedPrice(variant.getListPrice(), PriceBasis.LIST);
    }
}
