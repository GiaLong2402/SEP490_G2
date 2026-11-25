package com.hbsisms.feature.storefront;

import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.MeCustomerResolver;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.storefront.dto.MeSalesRequestRejectRequest;
import com.hbsisms.feature.storefront.dto.MeSalesRequestResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inter 2 — B2C yêu cầu mua vượt tồn (`/me/sales-requests`). */
@RestController
@RequestMapping("/me/sales-requests")
public class MeSalesRequestController {

    private final MeCustomerResolver meCustomerResolver;
    private final MeSalesRequestService meSalesRequestService;

    public MeSalesRequestController(
            MeCustomerResolver meCustomerResolver, MeSalesRequestService meSalesRequestService) {
        this.meCustomerResolver = meCustomerResolver;
        this.meSalesRequestService = meSalesRequestService;
    }

    @GetMapping
    public PageResponse<MeSalesRequestResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        return meSalesRequestService.listMine(customer(user), page, pageSize);
    }

    @GetMapping("/{code}")
    public MeSalesRequestResponse get(
            @AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        return meSalesRequestService.getMine(customer(user), code);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MeSalesRequestResponse create(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody SalesRequestInputRequest request) {
        return meSalesRequestService.create(customer(user), request);
    }

    /** Khách đồng ý báo giá → tạo đơn. */
    @PostMapping("/{code}/accept")
    public MeSalesRequestResponse accept(
            @AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        return meSalesRequestService.accept(customer(user), code, user == null ? null : user.id());
    }

    /** Khách từ chối báo giá — body bắt buộc lý do (cửa hàng xem được). */
    @PostMapping("/{code}/reject")
    public MeSalesRequestResponse reject(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable String code,
            @Valid @RequestBody MeSalesRequestRejectRequest request) {
        return meSalesRequestService.reject(customer(user), code, request);
    }

    private Customer customer(AuthUser user) {
        return meCustomerResolver.requireForUser(user.id());
    }
}
