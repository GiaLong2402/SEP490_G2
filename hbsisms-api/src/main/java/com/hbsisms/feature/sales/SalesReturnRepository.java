package com.hbsisms.feature.sales;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Đọc phiếu trả — Inter 3 dòng 11 (inbox Chủ) / dòng 6 (detail). */
public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long> {

    @EntityGraph(attributePaths = {"order"})
    List<SalesReturn> findByStatusOrderByCreatedAtDesc(ReturnStatus status);

    @EntityGraph(attributePaths = {"createdBy"})
    List<SalesReturn> findByOrder_IdOrderByCreatedAtDesc(Long orderId);
}
