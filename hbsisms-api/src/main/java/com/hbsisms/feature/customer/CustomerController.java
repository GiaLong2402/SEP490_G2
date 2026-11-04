package com.hbsisms.feature.customer;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.dto.CustomerActiveRequest;
import com.hbsisms.feature.customer.dto.CustomerDetailResponse;
import com.hbsisms.feature.customer.dto.CustomerListItemResponse;
import com.hbsisms.feature.customer.dto.CustomerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer APIs (Duy): list/search, detail, create/update, lock/unlock.
 * Address/price write remain with Khôi.
 */
@RestController
@RequestMapping("/customers")
@PreAuthorize("hasAuthority('customers')")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public PageResponse<CustomerListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) CustomerType type,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return customerService.search(q, type, isActive, page, pageSize);
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse get(@PathVariable Long id) {
        return customerService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDetailResponse create(@Valid @RequestBody CustomerRequest request) {
        return customerService.create(request);
    }

    @PutMapping("/{id}")
    public CustomerDetailResponse update(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    public CustomerDetailResponse setActive(
            @PathVariable Long id, @Valid @RequestBody CustomerActiveRequest request) {
        return customerService.setActive(id, Boolean.TRUE.equals(request.isActive()));
    }
}
