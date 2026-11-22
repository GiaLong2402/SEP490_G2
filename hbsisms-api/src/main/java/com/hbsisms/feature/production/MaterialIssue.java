package com.hbsisms.feature.production;

import com.hbsisms.common.persistence.AuditableEntity;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Phiếu xuất NVL cho lệnh SX. Task 49 tạo {@link MaterialIssueStatus#PENDING}; task 50 xác nhận trừ tồn.
 */
@Entity
@Table(name = "material_issue")
@Getter
@Setter
@NoArgsConstructor
public class MaterialIssue extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "production_order_id", nullable = false, unique = true)
    private ProductionOrder productionOrder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MaterialIssueStatus status = MaterialIssueStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(name = "completed_at")
    private Instant completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "completed_by")
    private AppUser completedBy;

    @Column(columnDefinition = "text")
    private String note;

    @OneToMany(mappedBy = "materialIssue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, id ASC")
    private List<MaterialIssueLine> lines = new ArrayList<>();

    public void replaceLines(List<MaterialIssueLine> issueLines) {
        lines.clear();
        int order = 0;
        for (MaterialIssueLine line : issueLines) {
            line.setMaterialIssue(this);
            line.setSortOrder(order++);
            lines.add(line);
        }
    }
}
