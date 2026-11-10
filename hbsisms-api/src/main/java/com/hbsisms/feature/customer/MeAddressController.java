package com.hbsisms.feature.customer;

import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Inter 2 #26 — B2C sổ địa chỉ của khách đang đăng nhập (`/me/addresses`). */
@RestController
@RequestMapping("/me/addresses")
public class MeAddressController {

    private final MeCustomerResolver meCustomerResolver;
    private final CustomerAddressService addressService;

    public MeAddressController(MeCustomerResolver meCustomerResolver, CustomerAddressService addressService) {
        this.meCustomerResolver = meCustomerResolver;
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@AuthenticationPrincipal AuthUser user) {
        return addressService.list(customerId(user));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody AddressRequest request) {
        return addressService.create(customerId(user), request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(
            @AuthenticationPrincipal AuthUser user,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(customerId(user), addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser user, @PathVariable Long addressId) {
        addressService.delete(customerId(user), addressId);
    }

    private Long customerId(AuthUser user) {
        return meCustomerResolver.requireForUser(user.id()).getId();
    }
}
