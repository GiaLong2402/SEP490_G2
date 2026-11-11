package com.hbsisms.feature.storefront;

import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.MeCustomerResolver;
import com.hbsisms.feature.storefront.dto.MeOrderDetailResponse;
import com.hbsisms.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Inter 2 #29 list/detail + #30 cancel unpaid (`/me/orders`). */
@RestController
@RequestMapping("/me/orders")
public class MeOrderController {

    private final MeCustomerResolver meCustomerResolver;
    private final MeOrderService meOrderService;

    public MeOrderController(MeCustomerResolver meCustomerResolver, MeOrderService meOrderService) {
        this.meCustomerResolver = meCustomerResolver;
        this.meOrderService = meOrderService;
    }

    @GetMapping
    public PageResponse<MeOrderDetailResponse> list(
            @AuthenticationPrincipal AuthUser user,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int pageSize) {
        return meOrderService.listMine(customer(user), page, pageSize);
    }

    @GetMapping("/{code}")
    public MeOrderDetailResponse get(@AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        return meOrderService.getMine(customer(user), code);
    }

    @PostMapping("/{code}/cancel")
    public MeOrderDetailResponse cancel(@AuthenticationPrincipal AuthUser user, @PathVariable String code) {
        return meOrderService.cancelMine(customer(user), code);
    }

    private Customer customer(AuthUser user) {
        return meCustomerResolver.requireForUser(user.id());
    }
}
