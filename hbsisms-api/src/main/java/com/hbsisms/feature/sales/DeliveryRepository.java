package com.hbsisms.feature.sales;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Delivery}. */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = "assignedTo")
    List<Delivery> findByOrder_IdOrderByIdAsc(Long orderId);

    /** Batch trạng thái giao cho export đơn (Inter 3 dòng 12). */
    List<Delivery> findByOrder_IdIn(Collection<Long> orderIds);
}
