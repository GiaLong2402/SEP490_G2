package com.hbsisms.feature.storefront;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.MeCustomerResolver;
import com.hbsisms.feature.storefront.dto.CheckoutRequest;
import com.hbsisms.feature.storefront.dto.CheckoutResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inter 2 #27 — POST /checkout (Idempotency-Key + JWT customer). */
@RestController
@RequestMapping("/checkout")
public class CheckoutController {

    private final MeCustomerResolver meCustomerResolver;
    private final CheckoutService checkoutService;

    public CheckoutController(MeCustomerResolver meCustomerResolver, CheckoutService checkoutService) {
        this.meCustomerResolver = meCustomerResolver;
        this.checkoutService = checkoutService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CheckoutResponse checkout(
            @AuthenticationPrincipal AuthUser user,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = CartService.SESSION_HEADER, required = false) String cartSession,
            @Valid @RequestBody CheckoutRequest request) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        Customer customer = meCustomerResolver.requireForUser(user.id());
        return checkoutService.checkout(user.id(), customer, idempotencyKey, request, cartSession);
    }
}
