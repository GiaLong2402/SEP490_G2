package com.hbsisms.feature.inventory;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StocktakeRepository
        extends JpaRepository<Stocktake, Long>, JpaSpecificationExecutor<Stocktake> {

    @Query("select max(s.code) from Stocktake s where s.code like 'KK-%'")
    Optional<String> findMaxKkCode();

    @Query(
            """
            select s from Stocktake s
            left join fetch s.createdBy
            left join fetch s.countedBy
            where s.id = :id
            """)
    Optional<Stocktake> findWithActorsById(@Param("id") Long id);
}
