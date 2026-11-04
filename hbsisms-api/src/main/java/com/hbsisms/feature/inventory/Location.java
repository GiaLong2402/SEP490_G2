package com.hbsisms.feature.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** A bin / shelf / freezer slot inside a warehouse; {@code code} is unique per warehouse. */
@Entity
@Table(name = "location", uniqueConstraints =
        @UniqueConstraint(name = "uk_location_warehouse_code", columnNames = {"warehouse_id", "code"}))
@Getter
@Setter
@NoArgsConstructor
public class Location {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "warehouse_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Warehouse warehouse;

    @Column(nullable = false, length = 30)
    private String code;

    @Column(length = 60)
    private String zone;

    /** Max trays at this bin; required (no unlimited). */
    @Column(nullable = false, columnDefinition = "integer not null default 1")
    private Integer capacity = 1;

    @Column(length = 200)
    private String note;
}
