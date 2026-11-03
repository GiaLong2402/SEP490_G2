package com.hbsisms.feature.customer;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@link Customer}. List/search CRUD APIs belong to Duy; address APIs only need findById. */
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @EntityGraph(attributePaths = "accountUser")
    Optional<Customer> findWithAccountById(Long id);
}
