package com.hbsisms.feature.customer;

import com.hbsisms.feature.customer.dto.PriceProposeRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 20 — đề xuất giá B2B. Duyệt/từ chối là task 21; PriceResolver là task 22.
 */
@RestController
@RequestMapping("/customers/{customerId}/prices")
@PreAuthorize("hasAuthority('customers')")
public class CustomerPriceController {

    private final CustomerPriceService customerPriceService;

    public CustomerPriceController(CustomerPriceService customerPriceService) {
        this.customerPriceService = customerPriceService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void propose(
            @PathVariable Long customerId,
            @Valid @RequestBody PriceProposeRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        customerPriceService.propose(customerId, request, actor.id());
    }
}
