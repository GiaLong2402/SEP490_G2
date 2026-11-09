package com.hbsisms.feature.payment;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Payment}. */
public interface PaymentRepository
        extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByCode(String code);

    Optional<Payment> findByPayosOrderCode(Long payosOrderCode);

    @EntityGraph(attributePaths = {"order", "customer", "receivedBy"})
    Optional<Payment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"order", "customer", "receivedBy"})
    List<Payment> findByOrder_IdOrderByPaidAtAscIdAsc(Long orderId);

    @Override
    @EntityGraph(attributePaths = {"order", "customer", "receivedBy"})
    Page<Payment> findAll(Specification<Payment> spec, Pageable pageable);

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.order.id = :orderId
              and p.direction = com.hbsisms.feature.payment.PayDirection.IN
              and p.status = com.hbsisms.feature.payment.PayTxnStatus.SUCCESS
            """)
    java.math.BigDecimal sumSuccessfulInByOrderId(@Param("orderId") Long orderId);

    @Query("select p.code from Payment p where p.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);
}
