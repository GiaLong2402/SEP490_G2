package com.hbsisms.feature.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "warehouse")
@Getter
@Setter
@NoArgsConstructor
public class Warehouse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 255)
    private String address;

    /** Max trays the warehouse can hold; required (no unlimited). */
    @Column(nullable = false, columnDefinition = "integer not null default 500")
    private Integer capacity = 500;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    /** Thành phẩm / nguyên liệu. Kho cũ mặc định là thành phẩm. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(20) not null default 'FINISHED'")
    private WarehouseKind kind = WarehouseKind.FINISHED;

    public boolean isRaw() {
        return kind == WarehouseKind.RAW;
    }
}
