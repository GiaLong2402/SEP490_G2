package com.hbsisms.feature.customer.dto;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Matches FE {@code CustomerDetail}. {@code recentOrders} stays empty until sales module lands.
 */
public record CustomerDetailResponse(
        Long id,
        String code,
        CustomerType customerType,
        String name,
        String email,
        String phone,
        String taxCode,
        String contactName,
        String contactPhone,
        Long accountUserId,
        BigDecimal creditLimit,
        short netDays,
        BigDecimal currentDebt,
        boolean isActive,
        String note,
        Instant createdAt,
        Instant updatedAt,
        List<AddressResponse> addresses,
        List<CustomerPriceRowResponse> prices,
        List<Object> recentOrders) {

    public static CustomerDetailResponse from(
            Customer customer,
            List<AddressResponse> addresses,
            List<CustomerPriceRowResponse> prices) {
        return new CustomerDetailResponse(
                customer.getId(),
                customer.getCode(),
                customer.getCustomerType(),
                customer.getName(),
                customer.getEmail(),
                customer.getPhone(),
                customer.getTaxCode(),
                customer.getContactName(),
                customer.getContactPhone(),
                customer.getAccountUser() == null ? null : customer.getAccountUser().getId(),
                customer.getCreditLimit(),
                customer.getNetDays(),
                customer.getCurrentDebt(),
                customer.isActive(),
                customer.getNote(),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                addresses,
                prices,
                List.of());
    }
}
