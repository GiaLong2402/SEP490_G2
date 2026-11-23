package com.hbsisms.feature.production;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionOrderRepository extends JpaRepository<ProductionOrder, Long> {

    @EntityGraph(attributePaths = {
        "product",
        "createdBy",
        "approvedBy",
        "materialIssue",
        "finishedVariant",
        "finishedReceipt"
    })
    @Query("select o from ProductionOrder o where o.id = :id")
    Optional<ProductionOrder> findWithHeaderById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from ProductionOrder o where o.id = :id")
    Optional<ProductionOrder> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"product", "createdBy", "approvedBy", "materialIssue"})
    @Query("""
            select o from ProductionOrder o
            where (:status is null or o.status = :status)
              and (
                :q is null or :q = ''
                or lower(o.code) like lower(concat('%', cast(:q as string), '%'))
                or lower(o.product.code) like lower(concat('%', cast(:q as string), '%'))
                or lower(o.product.name) like lower(concat('%', cast(:q as string), '%'))
              )
            """)
    Page<ProductionOrder> search(
            @Param("q") String q, @Param("status") ProductionOrderStatus status, Pageable pageable);
}
