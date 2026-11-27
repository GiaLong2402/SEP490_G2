package com.hbsisms.feature.costing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostItemBalanceRepository extends JpaRepository<CostItemBalance, Long> {

    List<CostItemBalance> findByPeriod_IdOrderByItemTypeAscItemIdAsc(Long periodId);

    List<CostItemBalance> findByPeriod_IdAndItemTypeOrderByItemIdAsc(Long periodId, CostItemType itemType);

    Optional<CostItemBalance> findByPeriod_IdAndItemTypeAndItemId(
            Long periodId, CostItemType itemType, Long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select b from CostItemBalance b
            where b.period.id = :periodId and b.itemType = :itemType and b.itemId = :itemId
            """)
    Optional<CostItemBalance> findForUpdate(
            @Param("periodId") Long periodId,
            @Param("itemType") CostItemType itemType,
            @Param("itemId") Long itemId);

    long countByPeriod_Id(Long periodId);
}
