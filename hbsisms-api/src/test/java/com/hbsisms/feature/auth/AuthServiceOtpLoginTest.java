package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpLoginRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.VerifiedFirebaseToken;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.security.SecurityProperties;
import com.hbsisms.security.TokenService;
import com.hbsisms.security.TokenService.IssuedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceOtpLoginTest {

    private static final Instant NOW = Instant.parse("2026-01-01T08:00:00Z");
    private static final String UID = "firebase-uid-1";
    private static final String PHONE = "0901234567";

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthIdentityRepository authIdentityRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private LoginAttemptService loginAttempts;
    @Mock
    private FirebaseTokenVerifier firebaseTokenVerifier;

    private AuthService authService;
    private AppUser user;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(any())).thenReturn("dummy-hash");
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), "test", Duration.ofHours(1)),
                new SecurityProperties.Cors(List.of("http://localhost")),
                new SecurityProperties.Login(5, Duration.ofMinutes(15), true));
        authService = new AuthService(
                userRepository,
                authIdentityRepository,
                passwordEncoder,
                tokenService,
                loginAttempts,
                firebaseTokenVerifier,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
        AppRole role = TestFixtures.role((short) 2, "warehouse", Set.of(Permission.INVENTORY));
        user = TestFixtures.staff(11L, "kho1", role);
        user.setPhone(PHONE);
    }

    @Test
    void loginWithOtp_existingIdentity_issuesJwtAndUpdatesTimestamps() {
        AuthIdentity identity = linkedIdentity(user);
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, "+84901234567"));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.of(identity));
        when(userRepository.findWithRoleById(11L)).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(new IssuedToken("jwt-otp", NOW.plusSeconds(60)));

        LoginResponse response = authService.loginWithOtp(new OtpLoginRequest("tok"));

        assertThat(response.token()).isEqualTo("jwt-otp");
        assertThat(response.user().roleCode()).isEqualTo("warehouse");
        verify(userRepository).updateLastLoginAt(11L, NOW);
        verify(userRepository).updatePhoneVerifiedAt(11L, NOW);
        assertThat(identity.getLastLoginAt()).isEqualTo(NOW);
        assertThat(identity.getPhone()).isEqualTo(PHONE);
        verify(authIdentityRepository).save(identity);
        verify(userRepository, never()).findWithRoleByPhone(any());
    }

    @Test
    void loginWithOtp_noIdentity_linksByPhoneThenIssuesJwt() {
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.empty());
        when(userRepository.findWithRoleByPhone(PHONE)).thenReturn(Optional.of(user));
        when(authIdentityRepository.existsByUser_IdAndProvider(11L, AuthIdentity.PROVIDER_FIREBASE))
                .thenReturn(false);
        when(authIdentityRepository.save(any(AuthIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findWithRoleById(11L)).thenReturn(Optional.of(user));
        when(tokenService.issue(user)).thenReturn(new IssuedToken("jwt-otp", NOW.plusSeconds(60)));

        LoginResponse response = authService.loginWithOtp(new OtpLoginRequest("tok"));

        assertThat(response.token()).isEqualTo("jwt-otp");
        ArgumentCaptor<AuthIdentity> captor = ArgumentCaptor.forClass(AuthIdentity.class);
        verify(authIdentityRepository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        AuthIdentity saved = captor.getAllValues().get(0);
        assertThat(saved.getProviderUid()).isEqualTo(UID);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getPhone()).isEqualTo(PHONE);
        verify(userRepository).updatePhoneVerifiedAt(11L, NOW);
    }

    @Test
    void loginWithOtp_unknownPhone_returns403WithoutCreatingUser() {
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.empty());
        when(userRepository.findWithRoleByPhone(PHONE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithOtp(new OtpLoginRequest("tok")))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.ACCOUNT_NOT_PROVISIONED)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenService, never()).issue(any());
        verify(authIdentityRepository, never()).save(any());
    }

    @Test
    void loginWithOtp_lockedAccount_returns403() {
        user.setActive(false);
        AuthIdentity identity = linkedIdentity(user);
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.of(identity));

        assertThatThrownBy(() -> authService.loginWithOtp(new OtpLoginRequest("tok")))
                .hasMessage(AuthService.ACCOUNT_LOCKED)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenService, never()).issue(any());
        verify(userRepository, never()).updateLastLoginAt(any(), any());
    }

    @Test
    void loginWithOtp_userAlreadyLinkedToOtherFirebase_returns409() {
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.empty());
        when(userRepository.findWithRoleByPhone(PHONE)).thenReturn(Optional.of(user));
        when(authIdentityRepository.existsByUser_IdAndProvider(11L, AuthIdentity.PROVIDER_FIREBASE))
                .thenReturn(true);

        assertThatThrownBy(() -> authService.loginWithOtp(new OtpLoginRequest("tok")))
                .isInstanceOf(ApiException.class)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(tokenService, never()).issue(any());
    }

    private static AuthIdentity linkedIdentity(AppUser owner) {
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(owner);
        identity.setProvider(AuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(UID);
        identity.setPhone(PHONE);
        identity.setSignInMethod("phone");
        return identity;
    }
}
