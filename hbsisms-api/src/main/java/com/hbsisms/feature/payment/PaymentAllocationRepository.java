package com.hbsisms.feature.payment;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link PaymentAllocation}. */
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {

    List<PaymentAllocation> findByPayment_IdOrderByIdAsc(Long paymentId);

    List<PaymentAllocation> findByOrder_IdOrderByIdAsc(Long orderId);
}
