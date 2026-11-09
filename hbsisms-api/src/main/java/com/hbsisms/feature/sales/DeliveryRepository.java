package com.hbsisms.feature.sales;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Delivery}. */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @EntityGraph(attributePaths = "assignedTo")
    List<Delivery> findByOrder_IdOrderByIdAsc(Long orderId);
}
