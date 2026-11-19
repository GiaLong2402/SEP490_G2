package com.hbsisms.feature.sales;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Đọc/ghi phiếu trả — inbox Chủ (Inter 3), chi tiết đơn, API Trả hàng (#29–#30).
 */
public interface SalesReturnRepository extends JpaRepository<SalesReturn, Long>, JpaSpecificationExecutor<SalesReturn> {

    @EntityGraph(attributePaths = {"order"})
    List<SalesReturn> findByStatusOrderByCreatedAtDesc(ReturnStatus status);

    @EntityGraph(attributePaths = {"createdBy"})
    List<SalesReturn> findByOrder_IdOrderByCreatedAtDesc(Long orderId);

    @EntityGraph(attributePaths = {"order", "customer", "createdBy", "delivery"})
    Optional<SalesReturn> findWithDetailsById(Long id);

    List<SalesReturn> findByOrder_IdAndStatusNot(Long orderId, ReturnStatus status);

    List<SalesReturn> findByDelivery_IdAndStatusNot(Long deliveryId, ReturnStatus status);

    @Query("select r.code from SalesReturn r where r.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);

    @Override
    Page<SalesReturn> findAll(Specification<SalesReturn> spec, Pageable pageable);
}
