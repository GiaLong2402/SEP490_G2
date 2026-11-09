package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerType;
import java.math.BigDecimal;
import java.time.Instant;

/** Nested customer on order detail. Matches FE {@code Customer}. */
public record OrderCustomerResponse(
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
        Instant updatedAt) {

    public static OrderCustomerResponse from(Customer customer) {
        return new OrderCustomerResponse(
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
                customer.getUpdatedAt());
    }
}
