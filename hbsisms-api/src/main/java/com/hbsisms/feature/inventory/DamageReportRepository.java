package com.hbsisms.feature.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DamageReportRepository
        extends JpaRepository<DamageReport, Long>, JpaSpecificationExecutor<DamageReport> {

    @Query("select max(d.code) from DamageReport d where d.code like 'BH-%'")
    Optional<String> findMaxBhCode();

    @Query(
            """
            select d from DamageReport d
            join fetch d.variant v
            join fetch v.product
            left join fetch d.lot
            left join fetch d.location loc
            left join fetch loc.warehouse
            left join fetch d.reportedBy
            left join fetch d.reviewedBy
            where d.id = :id
            """)
    Optional<DamageReport> findDetailedById(@Param("id") Long id);
}
