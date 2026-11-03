package com.hbsisms.feature.customer.pricing;

import com.hbsisms.feature.customer.pricing.dto.PricingRequest;
import com.hbsisms.feature.customer.pricing.dto.PricingResultResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 22 — tính giá áp dụng (PriceResolver) cho preview đơn / POS / báo giá.
 * FE: {@code POST /pricing/resolve}.
 */
@RestController
@RequestMapping("/pricing")
@PreAuthorize("isAuthenticated()")
public class PricingController {

    private final PricingService pricingService;

    public PricingController(PricingService pricingService) {
        this.pricingService = pricingService;
    }

    @PostMapping("/resolve")
    public PricingResultResponse resolve(@Valid @RequestBody PricingRequest request) {
        return pricingService.resolve(request);
    }
}
