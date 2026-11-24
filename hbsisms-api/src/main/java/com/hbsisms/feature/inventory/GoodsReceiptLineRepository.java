package com.hbsisms.feature.inventory;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "rawLot", "productVariant"})
    List<GoodsReceiptLine> findByReceipt_IdOrderByLineNoAsc(Long receiptId);

    /** Dòng của cả trang danh sách phiếu trong 1 query (tránh N+1). */
    @EntityGraph(attributePaths = {"receipt", "rawMaterial", "productVariant"})
    List<GoodsReceiptLine> findByReceipt_IdInOrderByLineNoAsc(Collection<Long> receiptIds);
}
