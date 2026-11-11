package com.hbsisms.feature.inventory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLedgerRepository extends JpaRepository<StockLedger, Long> {

    @Query("""
            select distinct e from StockLedger e
            join fetch e.variant v
            join fetch v.product
            left join fetch e.lot
            left join fetch e.location l
            left join fetch l.warehouse
            left join fetch e.toLocation tl
            left join fetch tl.warehouse
            left join fetch e.createdBy
            order by e.createdAt desc, e.id desc
            """)
    List<StockLedger> findAllWithDetailsOrderByCreatedAtDesc();

    @Query("""
            select distinct e from StockLedger e
            join fetch e.variant v
            join fetch v.product
            left join fetch e.lot
            where e.refType = :refType and e.refId = :refId
            order by e.id asc
            """)
    List<StockLedger> findByRefTypeAndRefIdWithDetails(
            @Param("refType") String refType, @Param("refId") Long refId);

    /** Chỉ id + associations lazy — dùng tính net HOLD trong cùng tx với đơn (tránh join-fetch xung đột). */
    @Query("""
            select e from StockLedger e
            where e.refType = :refType and e.refId = :refId
            order by e.id asc
            """)
    List<StockLedger> findByRefTypeAndRefIdForAllocation(
            @Param("refType") String refType, @Param("refId") Long refId);
}
