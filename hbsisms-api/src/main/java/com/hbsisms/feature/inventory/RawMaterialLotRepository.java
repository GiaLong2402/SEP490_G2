package com.hbsisms.feature.inventory;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RawMaterialLotRepository extends JpaRepository<RawMaterialLot, Long> {

    boolean existsByRawMaterial_IdAndLotCodeIgnoreCase(Long rawMaterialId, String lotCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from RawMaterialLot l where l.id = :id")
    Optional<RawMaterialLot> findByIdForUpdate(@Param("id") Long id);

    /**
     * Lô của 1 NVL theo thứ tự FEFO: HSD gần nhất trước (lô không có HSD xếp cuối), cùng HSD thì nhập trước.
     * Dòng 50 (xuất NVL cho xưởng) dùng đúng thứ tự này.
     */
    @EntityGraph(attributePaths = "receipt")
    @Query("""
            select l from RawMaterialLot l
            where l.rawMaterial.id = :rawMaterialId
              and (:availableOnly = false or l.qtyRemaining > 0)
            order by case when l.expDate is null then 1 else 0 end, l.expDate, l.receivedAt, l.id
            """)
    List<RawMaterialLot> findFefo(
            @Param("rawMaterialId") Long rawMaterialId, @Param("availableOnly") boolean availableOnly);

    @Query("select coalesce(sum(l.qtyRemaining), 0) from RawMaterialLot l where l.rawMaterial.id = :rawMaterialId")
    BigDecimal sumRemaining(@Param("rawMaterialId") Long rawMaterialId);
}
