package com.hbsisms.feature.inventory;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptRepository
        extends JpaRepository<GoodsReceipt, Long>, JpaSpecificationExecutor<GoodsReceipt> {

    @Query("select max(g.code) from GoodsReceipt g where g.code like 'PN-%'")
    Optional<String> findMaxPnCode();

    /** Khóa phiếu khi xác nhận / hủy — 2 người bấm cùng lúc không cộng tồn 2 lần. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GoodsReceipt g where g.id = :id")
    Optional<GoodsReceipt> findByIdForUpdate(@Param("id") Long id);
}
