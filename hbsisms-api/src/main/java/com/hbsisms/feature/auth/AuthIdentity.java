package com.hbsisms.feature.auth;

import com.hbsisms.feature.user.AppUser;
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
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Links an external sign-in account (Firebase Auth: phone OTP, email link, Google) to an {@link AppUser}.
 * Firebase sends and checks the OTP; the backend verifies the Firebase ID token and looks up {@code providerUid}.
 */
@Entity
@Table(name = "auth_identity", uniqueConstraints = {
        @UniqueConstraint(name = "uk_identity_provider_uid", columnNames = {"provider", "provider_uid"}),
        @UniqueConstraint(name = "uk_identity_user_provider", columnNames = {"user_id", "provider"})})
@Getter
@Setter
@NoArgsConstructor
public class AuthIdentity {

    public static final String PROVIDER_FIREBASE = "firebase";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Setter(lombok.AccessLevel.NONE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser user;

    @Column(nullable = false, length = 30)
    private String provider = PROVIDER_FIREBASE;

    /** Firebase uid (the ID token's {@code sub} claim). */
    @Column(nullable = false, length = 128)
    private String providerUid;

    /** phone | email_link | google — the method used most recently. */
    @Column(length = 30)
    private String signInMethod;

    @Column(length = 20)
    private String phone;

    @Column(length = 160)
    private String email;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant lastLoginAt;
}
