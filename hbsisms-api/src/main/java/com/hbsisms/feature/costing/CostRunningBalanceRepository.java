package com.hbsisms.feature.costing;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CostRunningBalanceRepository extends JpaRepository<CostRunningBalance, Long> {

    Optional<CostRunningBalance> findByItemTypeAndItemId(CostItemType itemType, Long itemId);

    List<CostRunningBalance> findAllByOrderByItemTypeAscItemIdAsc();

    List<CostRunningBalance> findByItemTypeOrderByItemIdAsc(CostItemType itemType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            select b from CostRunningBalance b
            where b.itemType = :itemType and b.itemId = :itemId
            """)
    Optional<CostRunningBalance> findForUpdate(
            @Param("itemType") CostItemType itemType, @Param("itemId") Long itemId);
}
