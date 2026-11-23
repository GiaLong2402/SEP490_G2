package com.hbsisms.feature.production;

import com.hbsisms.common.persistence.AuditableEntity;
import com.hbsisms.feature.catalog.Product;
import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Lệnh sản xuất (Inter 1 dòng 48–49). */
@Entity
@Table(name = "production_order")
@Getter
@Setter
@NoArgsConstructor
public class ProductionOrder extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "planned_qty", nullable = false, precision = 14, scale = 4)
    private BigDecimal plannedQty;

    @Column(name = "planned_due_date")
    private LocalDate plannedDueDate;

    @Column(columnDefinition = "text")
    private String note;

    /** Chi phí SX dự kiến do người dùng nhập. */
    @Column(name = "estimated_production_cost", precision = 14, scale = 2)
    private BigDecimal estimatedProductionCost;

    /** Chi phí NVL tạm tính (server). */
    @Column(name = "estimated_material_cost", precision = 14, scale = 2)
    private BigDecimal estimatedMaterialCost = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductionOrderStatus status = ProductionOrderStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private AppUser approvedBy;

    @OneToMany(mappedBy = "productionOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<ProductionOrderMaterialLine> materialLines = new ArrayList<>();

    @OneToOne(mappedBy = "productionOrder", fetch = FetchType.LAZY)
    private MaterialIssue materialIssue;

    public void replaceMaterialLines(List<ProductionOrderMaterialLine> lines) {
        materialLines.clear();
        int order = 0;
        for (ProductionOrderMaterialLine line : lines) {
            line.setProductionOrder(this);
            line.setSortOrder(order++);
            materialLines.add(line);
        }
    }

    public boolean allMaterialsSufficient() {
        return materialLines.stream().allMatch(ProductionOrderMaterialLine::isSufficient);
    }
}
