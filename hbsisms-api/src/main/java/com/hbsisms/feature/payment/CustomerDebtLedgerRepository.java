package com.hbsisms.feature.payment;

import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only persistence for {@link CustomerDebtLedger}. */
public interface CustomerDebtLedgerRepository extends JpaRepository<CustomerDebtLedger, Long> {
}
