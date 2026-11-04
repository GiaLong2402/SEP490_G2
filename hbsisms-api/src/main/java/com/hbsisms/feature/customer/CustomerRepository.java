package com.hbsisms.feature.customer;

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

/** Persistence for {@link Customer}. List/search + CRUD write APIs belong to Duy. */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @EntityGraph(attributePaths = "accountUser")
    Optional<Customer> findWithAccountById(Long id);

    Page<Customer> findAll(Specification<Customer> spec, Pageable pageable);

    /** Codes matching a generated-prefix pattern (e.g. {@code KH-%} / {@code KH-B%}) for next-code. */
    @Query("select c.code from Customer c where c.code like concat(:prefix, '%')")
    List<String> findCodesByPrefix(@Param("prefix") String prefix);
}
