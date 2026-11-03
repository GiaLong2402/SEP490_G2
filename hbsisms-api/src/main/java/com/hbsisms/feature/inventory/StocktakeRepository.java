package com.hbsisms.feature.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
}
