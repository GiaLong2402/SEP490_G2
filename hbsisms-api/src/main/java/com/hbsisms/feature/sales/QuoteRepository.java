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

/** Persistence for {@link Quote}. */
public interface QuoteRepository extends JpaRepository<Quote, Long>, JpaSpecificationExecutor<Quote> {

    @EntityGraph(attributePaths = {"customer", "createdBy", "approvedBy", "request"})
    Optional<Quote> findWithHeaderById(Long id);

    @Query("""
            select distinct q from Quote q
            left join fetch q.lines l
            left join fetch l.variant v
            left join fetch v.product
            left join fetch q.customer
            left join fetch q.createdBy
            left join fetch q.approvedBy
            left join fetch q.request
            where q.id = :id
            """)
    Optional<Quote> findWithDetailsById(@Param("id") Long id);

    @Override
    @EntityGraph(attributePaths = {"customer", "createdBy", "request"})
    Page<Quote> findAll(Specification<Quote> spec, Pageable pageable);

    @Query("select q.code from Quote q where q.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);
}
