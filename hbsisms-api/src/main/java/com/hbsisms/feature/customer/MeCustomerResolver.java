package com.hbsisms.feature.customer;

import com.hbsisms.common.exception.ApiException;
import org.springframework.stereotype.Component;

/** Resolves the Customer row linked to the authenticated portal user (`account_user_id`). */
@Component
public class MeCustomerResolver {

    private final CustomerRepository customerRepository;

    public MeCustomerResolver(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    public Customer requireForUser(Long accountUserId) {
        return customerRepository
                .findByAccountUser_Id(accountUserId)
                .orElseThrow(() -> ApiException.notFound("Chưa liên kết hồ sơ khách hàng"));
    }
}
