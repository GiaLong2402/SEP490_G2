package com.hbsisms.feature.customer.pricing;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.catalog.ProductVariant;
import com.hbsisms.feature.catalog.ProductVariantRepository;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.pricing.dto.PricedLineResponse;
import com.hbsisms.feature.customer.pricing.dto.PricingLineInput;
import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import com.hbsisms.feature.inventory.StockRepository;
import com.hbsisms.feature.payment.PaymentMethod;
import com.hbsisms.feature.sales.PriceBasis;
import com.hbsisms.feature.sales.SellUnit;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Orchestrates POST /pricing/resolve — dùng {@link PriceResolver} + credit check + tồn khả dụng. */
@Service
@Transactional(readOnly = true)
public class PricingService {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    private final PriceResolver priceResolver;
    private final ProductVariantRepository productVariantRepository;
    private final CustomerRepository customerRepository;
    private final StockRepository stockRepository;

    public PricingService(
            PriceResolver priceResolver,
            ProductVariantRepository productVariantRepository,
            CustomerRepository customerRepository,
            StockRepository stockRepository) {
        this.priceResolver = priceResolver;
        this.productVariantRepository = productVariantRepository;
        this.customerRepository = customerRepository;
        this.stockRepository = stockRepository;
    }

    public PricingResultResponse resolve(PricingRequest request) {
        List<PricedLineResponse> priced = new ArrayList<>(request.lines().size());
        BigDecimal subtotal = ZERO;

        for (PricingLineInput line : request.lines()) {
            PricedLineResponse row = priceLine(request.customerId(), line);
            priced.add(row);
            subtotal = subtotal.add(row.lineTotal());
        }

        BigDecimal discount = moneyOrZero(request.discountTotal());
        BigDecimal shipping = moneyOrZero(request.shippingFee());
        BigDecimal total = subtotal.subtract(discount).add(shipping).max(ZERO);

        CreditCheck credit = creditCheck(request.customerId(), request.paymentMethod(), total);
        return new PricingResultResponse(priced, subtotal, total, credit.isOverLimit(), credit.overLimitAmount());
    }

    private PricedLineResponse priceLine(Long customerId, PricingLineInput line) {
        ProductVariant variant = productVariantRepository
                .findById(line.variantId())
                .orElseThrow(() -> ApiException.badRequest("Quy cách không tồn tại"));

        int qtyKhay = toQtyKhay(line.unit(), line.qty(), variant.getPackPerCase());
        ResolvedPrice resolved = priceResolver.resolve(customerId, variant.getId(), qtyKhay);

        boolean manual = line.manualPrice() != null && line.manualPrice().compareTo(BigDecimal.ZERO) >= 0;
        BigDecimal unitPrice = money(manual ? line.manualPrice() : resolved.unitPrice());
        PriceBasis basis = manual ? PriceBasis.MANUAL : resolved.priceBasis();
        BigDecimal lineTotal = unitPrice
                .multiply(BigDecimal.valueOf(qtyKhay))
                .setScale(0, RoundingMode.HALF_UP)
                .setScale(2, RoundingMode.HALF_UP);

        long available = stockRepository.sumAvailableByVariantExcludingExpired(variant.getId(), LocalDate.now());
        long shortfall = Math.max(0L, (long) qtyKhay - available);
        return new PricedLineResponse(
                variant.getId(),
                line.unit(),
                line.qty(),
                qtyKhay,
                unitPrice,
                basis,
                lineTotal,
                available,
                shortfall);
    }

    static int toQtyKhay(SellUnit unit, int qty, short packPerCase) {
        if (qty <= 0) {
            throw ApiException.badRequest("Số lượng phải là số nguyên dương");
        }
        if (unit == SellUnit.CASE) {
            int packs = packPerCase <= 0 ? 1 : packPerCase;
            return Math.multiplyExact(qty, packs);
        }
        return qty;
    }

    private CreditCheck creditCheck(Long customerId, String paymentMethod, BigDecimal total) {
        if (customerId == null || !isCreditNet(paymentMethod)) {
            return new CreditCheck(false, ZERO);
        }
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return new CreditCheck(false, ZERO);
        }
        BigDecimal over = customer.getCurrentDebt().add(total).subtract(customer.getCreditLimit());
        if (over.compareTo(BigDecimal.ZERO) <= 0) {
            return new CreditCheck(false, ZERO);
        }
        return new CreditCheck(true, money(over));
    }

    private static boolean isCreditNet(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return false;
        }
        return PaymentMethod.CREDIT_NET.code().equals(paymentMethod.trim().toLowerCase(Locale.ROOT));
    }

    private static BigDecimal moneyOrZero(BigDecimal value) {
        return value == null ? ZERO : money(value);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record CreditCheck(boolean isOverLimit, BigDecimal overLimitAmount) {
    }
}
