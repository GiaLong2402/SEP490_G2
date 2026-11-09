package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.auth.dto.LoginRequest;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.system.AuditLogService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T08:00:00Z");

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthIdentityRepository authIdentityRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private LoginAttemptService loginAttempts;
    @Mock
    private com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier firebaseTokenVerifier;

    private AuthService authService;
    private AppUser user;
    private boolean passwordLoginAllRoles = true;

    @BeforeEach
    void setUp() {
        when(passwordEncoder.encode(anyString())).thenReturn("dummy-hash");
        when(loginAttempts.tryAcquire(anyString())).thenReturn(true);
        authService = newAuthService();
        AppRole role = TestFixtures.role((short) 1, "sales", Set.of(Permission.ORDERS));
        user = TestFixtures.staff(7L, "mai", role);
    }

    private AuthService newAuthService() {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), "test", Duration.ofHours(1)),
                new SecurityProperties.Cors(List.of("http://localhost")),
                new SecurityProperties.Login(5, Duration.ofMinutes(15), passwordLoginAllRoles));
        return new AuthService(
                userRepository,
                authIdentityRepository,
                customerRepository,
                roleRepository,
                auditLogService,
                passwordEncoder,
                tokenService,
                loginAttempts,
                firebaseTokenVerifier,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void login_validCredentials_returnsTokenAndRecordsLogin() {
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash-mai")).thenReturn(true);
        when(tokenService.issue(user)).thenReturn(new IssuedToken("jwt", NOW.plusSeconds(60)));

        LoginResponse response = authService.login(new LoginRequest("  MAI ", "secret123"));

        assertThat(response.token()).isEqualTo("jwt");
        assertThat(response.user().roleCode()).isEqualTo("sales");
        assertThat(response.user().permissions()).containsExactly("orders");
        verify(userRepository).updateLastLoginAt(7L, NOW);
        verify(loginAttempts).recordSuccess("mai");
    }

    @Test
    void login_wrongPassword_returns400AndKeepsAttemptCounted() {
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("bad", "hash-mai")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("mai", "bad")))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.INVALID_CREDENTIALS)
                .extracting("status").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(loginAttempts).tryAcquire("mai");
        verify(loginAttempts, never()).recordSuccess(anyString());
    }

    @Test
    void login_unknownUser_stillChecksDummyHashAndFails() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("ghost", "whatever")))
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(passwordEncoder).matches("whatever", "dummy-hash");
    }

    @Test
    void login_passwordOver72Bytes_failsWithoutCallingBcrypt() {
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("mai", "ạ".repeat(30))))
                .hasMessage(AuthService.INVALID_CREDENTIALS);
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void login_lockedAccount_returns403WithoutIssuingToken() {
        user.setActive(false);
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash-mai")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("mai", "secret123")))
                .hasMessage(AuthService.ACCOUNT_LOCKED)
                .extracting("status").isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenService, never()).issue(any());
        verify(userRepository, never()).updateLastLoginAt(anyLong(), any());
    }

    @Test
    void login_tooManyAttempts_returns429WithoutQueryingUser() {
        when(loginAttempts.tryAcquire("mai")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("mai", "secret123")))
                .extracting("status").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        verify(userRepository, never()).findByUsername(anyString());
    }

    @Test
    void login_nonAdmin_whenPasswordLoginRestricted_returns403() {
        passwordLoginAllRoles = false;
        authService = newAuthService();
        when(userRepository.findByUsername("mai")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("secret123", "hash-mai")).thenReturn(true);

        assertThatThrownBy(() -> authService.login(new LoginRequest("mai", "secret123")))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.PASSWORD_LOGIN_USE_OTP)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenService, never()).issue(any());
    }

    @Test
    void login_admin_whenPasswordLoginRestricted_stillSucceeds() {
        passwordLoginAllRoles = false;
        authService = newAuthService();
        AppRole adminRole = TestFixtures.role((short) 1, DefaultRoles.ADMIN, Set.of(Permission.ADMIN));
        AppUser admin = TestFixtures.staff(1L, "admin", adminRole);
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("secret123", "hash-admin")).thenReturn(true);
        when(tokenService.issue(admin)).thenReturn(new IssuedToken("jwt-admin", NOW.plusSeconds(60)));

        LoginResponse response = authService.login(new LoginRequest("admin", "secret123"));

        assertThat(response.token()).isEqualTo("jwt-admin");
        assertThat(response.user().roleCode()).isEqualTo("admin");
    }

    @Test
    void logout_incrementsTokenVersionAtomically() {
        authService.logout(7L);

        verify(userRepository).incrementTokenVersion(7L);
    }
}
