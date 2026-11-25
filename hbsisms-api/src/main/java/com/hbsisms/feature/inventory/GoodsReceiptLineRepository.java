package com.hbsisms.feature.inventory;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoodsReceiptLineRepository extends JpaRepository<GoodsReceiptLine, Long> {

    @EntityGraph(attributePaths = {"rawMaterial", "rawLot", "productVariant"})
    List<GoodsReceiptLine> findByReceipt_IdOrderByLineNoAsc(Long receiptId);

    /** Dòng của cả trang danh sách phiếu trong 1 query (tránh N+1). */
    @EntityGraph(attributePaths = {"receipt", "rawMaterial", "productVariant"})
    List<GoodsReceiptLine> findByReceipt_IdInOrderByLineNoAsc(Collection<Long> receiptIds);

    /** Giá vốn lô TP (đơn giá ghi lúc nhập) — lấy dòng mới nhất theo id nếu trùng mã lô. */
    @Query("""
            select l from GoodsReceiptLine l
            where l.lotCode in :lotCodes
              and l.unitPrice is not null
            order by l.id desc
            """)
    List<GoodsReceiptLine> findCostLinesByLotCodes(@Param("lotCodes") Collection<String> lotCodes);
}
