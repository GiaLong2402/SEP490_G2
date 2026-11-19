package com.hbsisms.feature.sales;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence for {@link Delivery}. */
public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Delivery d where d.id = :id")
    Optional<Delivery> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = "assignedTo")
    List<Delivery> findByOrder_IdOrderByIdAsc(Long orderId);
}
