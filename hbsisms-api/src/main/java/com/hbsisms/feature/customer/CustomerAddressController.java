package com.hbsisms.feature.customer;

import com.hbsisms.feature.customer.dto.AddressRequest;
import com.hbsisms.feature.customer.dto.AddressResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Address endpoints under a customer. Customer list/detail/CRUD belongs to Duy;
 * this controller only owns {@code /customers/{id}/addresses}.
 */
@RestController
@RequestMapping("/customers/{customerId}/addresses")
@PreAuthorize("hasAuthority('customers')")
public class CustomerAddressController {

    private final CustomerAddressService addressService;

    public CustomerAddressController(CustomerAddressService addressService) {
        this.addressService = addressService;
    }

    @GetMapping
    public List<AddressResponse> list(@PathVariable Long customerId) {
        return addressService.list(customerId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AddressResponse create(
            @PathVariable Long customerId, @Valid @RequestBody AddressRequest request) {
        return addressService.create(customerId, request);
    }

    @PutMapping("/{addressId}")
    public AddressResponse update(
            @PathVariable Long customerId,
            @PathVariable Long addressId,
            @Valid @RequestBody AddressRequest request) {
        return addressService.update(customerId, addressId, request);
    }

    @DeleteMapping("/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long customerId, @PathVariable Long addressId) {
        addressService.delete(customerId, addressId);
    }
}
