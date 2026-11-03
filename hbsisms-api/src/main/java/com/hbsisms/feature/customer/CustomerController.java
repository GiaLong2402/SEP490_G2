package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.dto.AddressResponse;
import com.hbsisms.feature.customer.dto.CustomerDetailResponse;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Minimal customer read API so FE detail + Address tab can be tested.
 * Full list/create/update/lock belongs to Duy — extend this controller later, do not duplicate.
 */
@RestController
@RequestMapping("/customers")
@PreAuthorize("hasAuthority('customers')")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;

    public CustomerController(
            CustomerRepository customerRepository, CustomerAddressRepository addressRepository) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
    }

    @GetMapping("/{id}")
    public CustomerDetailResponse get(@PathVariable Long id) {
        Customer customer = customerRepository
                .findWithAccountById(id)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy khách hàng"));
        List<AddressResponse> addresses = addressRepository
                .findByCustomerIdOrderByDefaultAddressDescIdAsc(id)
                .stream()
                .map(AddressResponse::from)
                .toList();
        return CustomerDetailResponse.from(customer, addresses);
    }
}
