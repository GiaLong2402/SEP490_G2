package com.hbsisms.feature.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Packaging / processing material tracked by quantity only (kg, cuộn, …), not by lot. */
@Entity
@Table(name = "raw_material")
@Getter
@Setter
@NoArgsConstructor
public class RawMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qtyOnHand = BigDecimal.ZERO;

    @Column(precision = 12, scale = 3)
    private BigDecimal reorderLevel;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
