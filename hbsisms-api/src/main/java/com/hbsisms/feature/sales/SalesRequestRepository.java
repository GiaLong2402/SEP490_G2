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

/** Persistence for {@link SalesRequest}. */
public interface SalesRequestRepository
        extends JpaRepository<SalesRequest, Long>, JpaSpecificationExecutor<SalesRequest> {

    Optional<SalesRequest> findByCode(String code);

    @EntityGraph(attributePaths = {"customer", "handledBy"})
    Optional<SalesRequest> findByCodeAndCustomer_Id(String code, Long customerId);

    @EntityGraph(attributePaths = {"customer", "handledBy"})
    Page<SalesRequest> findByCustomer_IdOrderByReceivedAtDescIdDesc(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"customer", "handledBy"})
    Optional<SalesRequest> findWithDetailsById(Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "handledBy"})
    Page<SalesRequest> findAll(Specification<SalesRequest> spec, Pageable pageable);

    @Query("select r.code from SalesRequest r where r.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);
}
