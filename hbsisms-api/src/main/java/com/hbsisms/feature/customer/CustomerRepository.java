package com.hbsisms.feature.customer;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** Persistence for {@link Customer}. List/search + detail read APIs belong to Duy. */
public interface CustomerRepository extends JpaRepository<Customer, Long>, JpaSpecificationExecutor<Customer> {

    @EntityGraph(attributePaths = "accountUser")
    Optional<Customer> findWithAccountById(Long id);

    Page<Customer> findAll(Specification<Customer> spec, Pageable pageable);
}
