package com.hbsisms.feature.sales;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SalesOrder}. */
public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    /**
     * Bucket doanh thu / COGS đơn {@code COMPLETED} theo {@code date_trunc} (day|month|year).
     * Revenue lấy từ {@code sales_order.total}; COGS = Σ coalesce(unit_cost,0)×qty_khay.
     */
    @Query(
            value = """
                    with order_buckets as (
                        select
                            (date_trunc(:truncUnit, o.placed_at at time zone 'Asia/Ho_Chi_Minh')
                                at time zone 'Asia/Ho_Chi_Minh') as bucket_start,
                            o.id as order_id,
                            o.total as revenue
                        from hbsisms.sales_order o
                        where o.state = 'COMPLETED'
                          and o.placed_at >= :fromInstant
                          and o.placed_at < :toExclusive
                    ),
                    order_cogs as (
                        select
                            ob.order_id,
                            coalesce(sum(coalesce(l.unit_cost, 0) * l.qty_khay), 0) as cogs
                        from order_buckets ob
                        left join hbsisms.sales_order_line l on l.order_id = ob.order_id
                        group by ob.order_id
                    )
                    select
                        ob.bucket_start as bucketStart,
                        coalesce(sum(ob.revenue), 0) as revenue,
                        coalesce(sum(oc.cogs), 0) as cogs,
                        count(*) as orderCount
                    from order_buckets ob
                    join order_cogs oc on oc.order_id = ob.order_id
                    group by ob.bucket_start
                    order by ob.bucket_start
                    """,
            nativeQuery = true)
    List<RevenueBucketRow> aggregateCompletedRevenue(
            @Param("fromInstant") Instant fromInstant,
            @Param("toExclusive") Instant toExclusive,
            @Param("truncUnit") String truncUnit);

    /** Projection for {@link #aggregateCompletedRevenue}. */
    interface RevenueBucketRow {
        Instant getBucketStart();

        BigDecimal getRevenue();

        BigDecimal getCogs();

        long getOrderCount();
    }

    Optional<SalesOrder> findByCode(String code);

    /** Khóa ghi bi quan (pessimistic) (SELECT ... FOR UPDATE) — gọi trong transaction trước khi đọc chi tiết/đổi trạng thái. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    /** Inbox Chủ — đơn chờ hoàn tiền / lệch tiền. */
    List<SalesOrder> findByPaymentStatusOrderByUpdatedAtDesc(com.hbsisms.feature.payment.PaymentStatus paymentStatus);

    Optional<SalesOrder> findFirstBySourceQuote_Id(Long quoteId);

    @EntityGraph(attributePaths = {"customer", "shipAddress", "placedBy", "approvedBy", "sourceQuote"})
    Optional<SalesOrder> findByCodeAndCustomer_Id(String code, Long customerId);

    @EntityGraph(attributePaths = {"customer", "shipAddress"})
    Page<SalesOrder> findByCustomer_IdOrderByPlacedAtDescIdDesc(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "shipAddress", "placedBy", "approvedBy", "sourceQuote"})
    Optional<SalesOrder> findWithHeaderById(Long id);

    @Query("""
            select distinct o from SalesOrder o
            left join fetch o.lines l
            left join fetch l.variant v
            left join fetch v.product
            left join fetch o.customer
            left join fetch o.shipAddress
            left join fetch o.placedBy
            left join fetch o.approvedBy
            left join fetch o.sourceQuote
            where o.id = :id
            """)
    Optional<SalesOrder> findWithDetailsById(@Param("id") Long id);

    /**
     * List + filter qua {@link JpaSpecificationExecutor#findAll}.
     * Không gắn {@code @EntityGraph} ở đây — trên một số bản Spring Data, EntityGraph trên
     * {@code findAll(Specification, Pageable)} làm Specification không được áp (lọc bị bỏ).
     * Fetch customer/placedBy nằm trong {@link OrderSpecifications}.
     */

    @Query("select o.code from SalesOrder o where o.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    /** Đơn còn hold và đã quá hạn — dùng cho job hết hạn thanh toán / giữ Sales. */
    @Query("""
            select o from SalesOrder o
            where o.holdExpiresAt is not null
              and o.holdExpiresAt < :now
              and o.state <> com.hbsisms.feature.sales.OrderState.CANCELLED
              and o.state <> com.hbsisms.feature.sales.OrderState.EXPIRED
            """)
    List<SalesOrder> findExpiredHolds(@Param("now") Instant now);

    /** Đơn Sales sắp hết hạn giữ — báo trước (event). */
    @Query("""
            select o from SalesOrder o
            where o.holdExpiresAt is not null
              and o.holdExpiresAt > :now
              and o.holdExpiresAt <= :warnBefore
              and o.state <> com.hbsisms.feature.sales.OrderState.CANCELLED
              and o.state <> com.hbsisms.feature.sales.OrderState.EXPIRED
              and o.paymentStatus in (
                    com.hbsisms.feature.payment.PaymentStatus.UNPAID,
                    com.hbsisms.feature.payment.PaymentStatus.WAITING)
            """)
    List<SalesOrder> findHoldsNeedingWarn(
            @Param("now") Instant now, @Param("warnBefore") Instant warnBefore);
}
