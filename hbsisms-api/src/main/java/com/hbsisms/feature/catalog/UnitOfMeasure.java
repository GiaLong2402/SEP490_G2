package com.hbsisms.feature.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Master unit of measure (kg, cái, ml, …). Created via CRUD — no auto-seed. */
@Entity
@Table(name = "unit_of_measure")
@Getter
@Setter
@NoArgsConstructor
public class UnitOfMeasure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
