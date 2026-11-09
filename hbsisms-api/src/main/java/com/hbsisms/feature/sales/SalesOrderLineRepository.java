package com.hbsisms.feature.sales;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SalesOrderLine}. */
public interface SalesOrderLineRepository extends JpaRepository<SalesOrderLine, Long> {

    List<SalesOrderLine> findByOrder_IdOrderByIdAsc(Long orderId);

    /** Dòng còn thiếu phân bổ — dùng khi nhập thành phẩm ưu tiên đơn Paid. */
    @Query("""
            select l from SalesOrderLine l
            join fetch l.order o
            join fetch l.variant
            where l.outstandingQty > 0
              and o.paymentStatus = com.hbsisms.feature.payment.PaymentStatus.PAID
              and o.state <> com.hbsisms.feature.sales.OrderState.CANCELLED
            order by o.placedAt asc, l.id asc
            """)
    List<SalesOrderLine> findOutstandingPaidLines();

    @Query("""
            select coalesce(sum(l.outstandingQty), 0) from SalesOrderLine l
            where l.order.id = :orderId
            """)
    int sumOutstandingByOrderId(@Param("orderId") Long orderId);
}
