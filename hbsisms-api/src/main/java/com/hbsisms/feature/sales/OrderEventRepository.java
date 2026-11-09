package com.hbsisms.feature.sales;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Append-only persistence for {@link OrderEvent}. */
public interface OrderEventRepository extends JpaRepository<OrderEvent, Long> {

    @EntityGraph(attributePaths = "createdBy")
    List<OrderEvent> findByOrder_IdOrderByCreatedAtAscIdAsc(Long orderId);
}
