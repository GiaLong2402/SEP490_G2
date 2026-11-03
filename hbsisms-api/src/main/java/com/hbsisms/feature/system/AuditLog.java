package com.hbsisms.feature.system;

import com.hbsisms.feature.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only log of who did what to which record. */
@Entity
@Immutable
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_time", columnList = "created_at"),
        @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id")})
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false, length = 30)
    private String action;

    @Column(length = 60)
    private String entityType;

    private Long entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> detail;

    /** IPv4 or IPv6 text. */
    @Column(length = 45)
    private String ipAddress;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Map<String, Object> getDetail() {
        return detail == null ? null : Map.copyOf(detail);
    }

    public void setDetail(Map<String, Object> detail) {
        this.detail = detail == null ? null : new HashMap<>(detail);
    }
}
