package com.hbsisms.feature.sales;

import com.hbsisms.feature.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
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

    /**
     * Top variants trên đơn {@code COMPLETED} trong khoảng — sort/limit ở service.
     */
    @Query(
            value = """
                    select
                        v.id as variantId,
                        p.name as productName,
                        v.sku as sku,
                        v.pack_label as packLabel,
                        coalesce(sum(l.qty_khay), 0) as qtyKhay,
                        coalesce(sum(l.line_total), 0) as revenue,
                        coalesce(sum(l.line_total - coalesce(l.unit_cost, 0) * l.qty_khay), 0) as profit
                    from hbsisms.sales_order o
                    join hbsisms.sales_order_line l on l.order_id = o.id
                    join hbsisms.product_variant v on v.id = l.variant_id
                    join hbsisms.product p on p.id = v.product_id
                    where o.state = 'COMPLETED'
                      and o.placed_at >= :fromInstant
                      and o.placed_at < :toExclusive
                    group by v.id, p.name, v.sku, v.pack_label
                    """,
            nativeQuery = true)
    List<TopProductRow> aggregateTopProducts(
            @Param("fromInstant") Instant fromInstant, @Param("toExclusive") Instant toExclusive);

    interface TopProductRow {
        Long getVariantId();

        String getProductName();

        String getSku();

        String getPackLabel();

        /** Postgres {@code sum} may surface as {@link BigDecimal}. */
        Number getQtyKhay();

        BigDecimal getRevenue();

        BigDecimal getProfit();
    }

    /**
     * Top khách trên đơn {@code COMPLETED} — walk-in ({@code customer_id} null) gộp một nhóm.
     */
    @Query(
            value = """
                    with order_stats as (
                        select
                            o.id as order_id,
                            o.customer_id as customer_id,
                            o.customer_name as customer_name,
                            o.order_type as order_type,
                            o.total as revenue,
                            coalesce(sum(l.line_total - coalesce(l.unit_cost, 0) * l.qty_khay), 0) as profit
                        from hbsisms.sales_order o
                        left join hbsisms.sales_order_line l on l.order_id = o.id
                        where o.state = 'COMPLETED'
                          and o.placed_at >= :fromInstant
                          and o.placed_at < :toExclusive
                        group by o.id, o.customer_id, o.customer_name, o.order_type, o.total
                    )
                    select
                        os.customer_id as customerId,
                        coalesce(max(c.name), max(os.customer_name), 'Khách vãng lai') as customerName,
                        max(os.order_type) as orderType,
                        count(*) as orderCount,
                        coalesce(sum(os.revenue), 0) as revenue,
                        coalesce(sum(os.profit), 0) as profit
                    from order_stats os
                    left join hbsisms.customer c on c.id = os.customer_id
                    group by os.customer_id
                    """,
            nativeQuery = true)
    List<TopCustomerRow> aggregateTopCustomers(
            @Param("fromInstant") Instant fromInstant, @Param("toExclusive") Instant toExclusive);

    interface TopCustomerRow {
        Long getCustomerId();

        String getCustomerName();

        String getOrderType();

        /** Postgres {@code count} may surface as {@link Long} or {@link BigInteger}. */
        Number getOrderCount();

        BigDecimal getRevenue();

        BigDecimal getProfit();
    }

    Optional<SalesOrder> findByCode(String code);

    /** Khóa ghi bi quan (pessimistic) (SELECT ... FOR UPDATE) — gọi trong transaction trước khi đọc chi tiết/đổi trạng thái. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<SalesOrder> findByIdForUpdate(@Param("id") Long id);

    /** Inbox Chủ — đơn chờ hoàn tiền / lệch tiền. */
    List<SalesOrder> findByPaymentStatusOrderByUpdatedAtDesc(PaymentStatus paymentStatus);

    /**
     * Ứng viên soạn hàng ngày: đã thu + giữ đủ + đang trong luồng chuẩn bị/giao.
     * Lọc theo ngày giao nằm ở service (không giao hoặc có chuyến khớp ngày).
     */
    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select o from SalesOrder o
            where o.state in :states
              and o.paymentStatus = :paymentStatus
              and o.allocationStatus = :allocationStatus
            order by o.code asc
            """)
    List<SalesOrder> findForDailyPicking(
            @Param("states") Collection<OrderState> states,
            @Param("paymentStatus") PaymentStatus paymentStatus,
            @Param("allocationStatus") AllocationStatus allocationStatus);

    Optional<SalesOrder> findFirstBySourceQuote_Id(Long quoteId);

    /** Đơn đặt trước chờ cọc đã quá hạn. */
    @Query("""
            select o from SalesOrder o
            where o.preorder = true
              and o.state = com.hbsisms.feature.sales.OrderState.PENDING
              and o.paymentStatus = com.hbsisms.feature.payment.PaymentStatus.UNPAID
              and o.depositDueAt is not null
              and o.depositDueAt < :now
            """)
    List<SalesOrder> findOverduePreorderDeposits(@Param("now") Instant now);

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

    long countByStateIn(Collection<OrderState> states);

    @Query("""
            select coalesce(sum(o.total), 0) from SalesOrder o
            where o.state not in (
                    com.hbsisms.feature.sales.OrderState.CANCELLED,
                    com.hbsisms.feature.sales.OrderState.EXPIRED)
              and o.placedAt >= :from
              and o.placedAt < :to
            """)
    java.math.BigDecimal sumRevenueBetween(@Param("from") Instant from, @Param("to") Instant to);

    @EntityGraph(attributePaths = {"customer"})
    @Query("""
            select o from SalesOrder o
            where o.state not in :excluded
            order by o.placedAt desc, o.id desc
            """)
    List<SalesOrder> findRecentExcluding(
            @Param("excluded") Collection<OrderState> excluded,
            org.springframework.data.domain.Pageable pageable);

}
