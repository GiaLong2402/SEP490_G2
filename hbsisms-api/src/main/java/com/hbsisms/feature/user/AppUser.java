package com.hbsisms.feature.user;

import com.hbsisms.common.persistence.AuditableEntity;
import com.hbsisms.feature.role.AppRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Every login account: internal staff (HR fields filled) and B2C customers.
 * {@code username} and {@code email} are stored lower-cased so uniqueness is case-insensitive.
 * {@code @DynamicUpdate} writes only changed columns, so concurrent edits to different fields do not overwrite each other.
 */
@Entity
@DynamicUpdate
@Table(name = "app_user",
        indexes = @Index(name = "idx_user_role", columnList = "role_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_app_user_phone", columnNames = "phone"))
@Getter
@Setter
@NoArgsConstructor
public class AppUser extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @Column(unique = true, length = 60)
    private String username;

    @Column(unique = true, length = 160)
    private String email;

    /** Also the identifier Firebase phone sign-in is matched against (see AuthIdentity). Unique via uk_app_user_phone. */
    @Column(length = 20)
    private String phone;

    private Instant emailVerifiedAt;

    private Instant phoneVerifiedAt;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 160)
    private String fullName;

    @Column(length = 500)
    private String avatarUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserType userType = UserType.CUSTOMER;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private AppRole role;

    @Column(unique = true, length = 30)
    private String employeeCode;

    @Column(length = 80)
    private String position;

    private LocalDate hiredAt;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    private Instant lastLoginAt;

    /**
     * Embedded in issued JWTs; incrementing it revokes every token issued before.
     * Only changed through {@link UserRepository#incrementTokenVersion} so concurrent updates cannot lose a bump.
     */
    @Column(nullable = false)
    @Setter(lombok.AccessLevel.NONE)
    private int tokenVersion;
}
