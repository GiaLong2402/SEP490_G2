package com.hbsisms.feature.sales;

import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Delivery}. */
public interface DeliveryRepository extends JpaRepository<Delivery, Long>, JpaSpecificationExecutor<Delivery> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    List<Delivery> findByOrder_IdOrderByIdAsc(Long orderId);

    /** Batch trạng thái giao cho export đơn (Inter 3 dòng 12). */
    List<Delivery> findByOrder_IdIn(Collection<Long> orderIds);

    /** Màn Giao hàng: lấy kèm đơn, khách, địa chỉ trong 1 query (tránh N+1). */
    @Override
    @EntityGraph(attributePaths = {"order", "order.customer", "address"})
    Page<Delivery> findAll(Specification<Delivery> spec, Pageable pageable);

    /** Tổng khay của từng đơn (dùng cho đợt giao "Trọn đơn" không có danh sách hàng riêng). Mỗi dòng: [orderId, tổng khay]. */
    @Query("select l.order.id, sum(l.qtyKhay) from SalesOrderLine l where l.order.id in :orderIds group by l.order.id")
    List<Object[]> sumQtyKhayByOrderIds(@Param("orderIds") Collection<Long> orderIds);
}
