package com.hbsisms.feature.customer;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, Long> {

    List<CustomerAddress> findByCustomerIdOrderByDefaultAddressDescIdAsc(Long customerId);

    Optional<CustomerAddress> findByIdAndCustomerId(Long id, Long customerId);

    long countByCustomerId(Long customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update CustomerAddress a set a.defaultAddress = false where a.customer.id = :customerId and a.defaultAddress = true")
    int clearDefaultForCustomer(@Param("customerId") Long customerId);
}
