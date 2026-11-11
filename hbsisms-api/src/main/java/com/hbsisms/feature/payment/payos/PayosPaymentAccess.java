package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.payment.Payment;
import com.hbsisms.feature.user.AppUser;

/** Shared ownership checks for customer PayOS APIs. */
final class PayosPaymentAccess {

    private PayosPaymentAccess() {}

    /** 404 (not 403) when payment is missing for this user — avoid leaking existence. */
    static void assertOwner(Payment payment, Long userId) {
        Customer customer = payment.getCustomer();
        AppUser account = customer == null ? null : customer.getAccountUser();
        if (account == null || userId == null || !userId.equals(account.getId())) {
            throw ApiException.notFound("Không tìm thấy thanh toán PayOS");
        }
    }
}
