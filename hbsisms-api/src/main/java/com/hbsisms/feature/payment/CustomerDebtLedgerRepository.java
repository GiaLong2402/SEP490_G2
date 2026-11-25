package com.hbsisms.feature.payment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only persistence for {@link CustomerDebtLedger}. */
public interface CustomerDebtLedgerRepository extends JpaRepository<CustomerDebtLedger, Long> {

    List<CustomerDebtLedger> findByCustomer_IdIn(Collection<Long> customerIds);
}
