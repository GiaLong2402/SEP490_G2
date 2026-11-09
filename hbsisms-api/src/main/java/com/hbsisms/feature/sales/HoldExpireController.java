package com.hbsisms.feature.sales;

import com.hbsisms.feature.sales.dto.HoldExpireJobResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for hold-expiry job (ops / demo). */
@RestController
@RequestMapping("/sales/hold-expire")
@PreAuthorize("hasAnyAuthority('orders', 'admin')")
public class HoldExpireController {

    private final HoldExpireService holdExpireService;

    public HoldExpireController(HoldExpireService holdExpireService) {
        this.holdExpireService = holdExpireService;
    }

    @PostMapping
    public HoldExpireJobResponse run() {
        return holdExpireService.run();
    }
}
