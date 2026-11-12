package com.hbsisms.feature.sales;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link SalesOrder}. */
public interface SalesOrderRepository
        extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

    Optional<SalesOrder> findByCode(String code);

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

    /** Đơn online còn hold và đã quá hạn — dùng cho job hết hạn thanh toán. */
    @Query("""
            select o from SalesOrder o
            where o.holdExpiresAt is not null
              and o.holdExpiresAt < :now
              and o.state <> com.hbsisms.feature.sales.OrderState.CANCELLED
            """)
    List<SalesOrder> findExpiredHolds(@Param("now") Instant now);
}
