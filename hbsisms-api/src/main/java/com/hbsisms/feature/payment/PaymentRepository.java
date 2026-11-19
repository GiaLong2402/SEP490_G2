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

    @EntityGraph(attributePaths = {"order", "customer", "customer.accountUser"})
    Optional<Payment> findWithOrderCustomerByPayosOrderCode(Long payosOrderCode);

    @EntityGraph(attributePaths = {"order", "customer", "receivedBy"})
    Optional<Payment> findWithDetailsById(Long id);

    @EntityGraph(attributePaths = {"order", "customer", "receivedBy"})
    List<Payment> findByOrder_IdOrderByPaidAtAscIdAsc(Long orderId);

    /** Batch net thu cho export đơn (Inter 3 dòng 12). */
    List<Payment> findByOrder_IdIn(java.util.Collection<Long> orderIds);

    /** Thanh toán lệch / đối soát tay — inbox Chủ (Inter 3 dòng 11). */
    @EntityGraph(attributePaths = {"order"})
    List<Payment> findByReconcileFlagIn(java.util.Collection<PaymentReconcileFlag> flags);

    /**
     * PayOS tiền về muộn cần hoàn ({@code raw_payload.needsRefund=true}).
     * Native vì map JSON → JPQL không ổn định giữa bản Hibernate.
     */
    @Query(
            value = """
                    select p.id from hbsisms.payment p
                    where (p.raw_payload ->> 'needsRefund') = 'true'
                    """,
            nativeQuery = true)
    List<Long> findIdsWithNeedsRefundFlag();

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

    @Query("""
            select coalesce(sum(p.amount), 0) from Payment p
            where p.order.id = :orderId
              and p.direction = com.hbsisms.feature.payment.PayDirection.REFUND
              and p.status = com.hbsisms.feature.payment.PayTxnStatus.SUCCESS
            """)
    java.math.BigDecimal sumSuccessfulRefundByOrderId(@Param("orderId") Long orderId);

    @Query("select p.code from Payment p where p.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Query("select coalesce(max(p.payosOrderCode), 130000) from Payment p")
    Long maxPayosOrderCode();
}
