package com.hbsisms.feature.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    boolean existsByLocation_Id(Long locationId);

    @Query("""
            select coalesce(sum(s.qtyAvailable + s.qtyHeld + s.qtyQuarantine + s.qtyExpired), 0)
            from Stock s
            where s.location.id = :locationId
            """)
    long sumQtyByLocationId(@Param("locationId") Long locationId);

    @Query("""
            select coalesce(sum(s.qtyAvailable + s.qtyHeld + s.qtyQuarantine + s.qtyExpired), 0)
            from Stock s
            where s.location.warehouse.id = :warehouseId
            """)
    long sumQtyByWarehouseId(@Param("warehouseId") Long warehouseId);

    /**
     * Atomic tray adjustment for available qty. Returns 0 if the row is missing or the
     * update would make {@code qty_available} negative — callers treat 0 as conflict.
     */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.qtyAvailable = s.qtyAvailable + :delta
            where s.id = :id and s.qtyAvailable + :delta >= 0
            """)
    int adjustQtyAvailable(@Param("id") Long id, @Param("delta") int delta);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.qtyHeld = s.qtyHeld + :delta
            where s.id = :id and s.qtyHeld + :delta >= 0
            """)
    int adjustQtyHeld(@Param("id") Long id, @Param("delta") int delta);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.qtyQuarantine = s.qtyQuarantine + :delta
            where s.id = :id and s.qtyQuarantine + :delta >= 0
            """)
    int adjustQtyQuarantine(@Param("id") Long id, @Param("delta") int delta);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
            set s.qtyExpired = s.qtyExpired + :delta
            where s.id = :id and s.qtyExpired + :delta >= 0
            """)
    int adjustQtyExpired(@Param("id") Long id, @Param("delta") int delta);

    /** Khay bán được (không tính lô đã quá hạn) — dùng cho pricing preview. */
    @Query("""
            select coalesce(sum(s.qtyAvailable), 0)
            from Stock s
            where s.variant.id = :variantId
              and (s.lot.expDate is null or s.lot.expDate >= :today)
            """)
    long sumAvailableByVariantExcludingExpired(
            @Param("variantId") Long variantId, @Param("today") java.time.LocalDate today);
}
