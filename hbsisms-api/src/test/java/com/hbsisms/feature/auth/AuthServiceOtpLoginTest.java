package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpLoginRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.FirebaseAdminTokenVerifier;
import com.hbsisms.feature.auth.firebase.StubFirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.VerifiedFirebaseToken;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.Permission;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import com.hbsisms.security.SecurityProperties;
import com.hbsisms.security.TokenService;
import com.hbsisms.security.TokenService.IssuedToken;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.util.ReflectionTestUtils;

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
                customerRepository,
                roleRepository,
                auditLogService,
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
        when(customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(PHONE))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.loginWithOtp(new OtpLoginRequest("tok")))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.ACCOUNT_NOT_PROVISIONED)
                .extracting("status")
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(tokenService, never()).issue(any());
        verify(authIdentityRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void loginWithOtp_walkInCustomer_createsUserAndLinks() {
        Customer walkIn = new Customer();
        ReflectionTestUtils.setField(walkIn, "id", 77L);
        walkIn.setCode("KH-010");
        walkIn.setCustomerType(CustomerType.B2C);
        walkIn.setName("Khách quầy");
        walkIn.setPhone(PHONE);

        AppRole customerRole = TestFixtures.role((short) 5, DefaultRoles.CUSTOMER, Set.of());
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.empty());
        when(userRepository.findWithRoleByPhone(PHONE)).thenReturn(Optional.empty());
        when(customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(PHONE))
                .thenReturn(Optional.of(walkIn));
        when(roleRepository.findByCode(DefaultRoles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", 50L);
            }
            return u;
        });
        when(authIdentityRepository.save(any(AuthIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(customerRepository.save(walkIn)).thenReturn(walkIn);
        when(userRepository.findWithRoleById(50L)).thenAnswer(inv -> {
            AppUser u = new AppUser();
            ReflectionTestUtils.setField(u, "id", 50L);
            u.setFullName("Khách quầy");
            u.setPhone(PHONE);
            u.setUserType(UserType.CUSTOMER);
            u.setRole(customerRole);
            return Optional.of(u);
        });
        when(tokenService.issue(any(AppUser.class))).thenReturn(new IssuedToken("jwt-walkin", NOW.plusSeconds(60)));

        LoginResponse response = authService.loginWithOtp(new OtpLoginRequest("tok"));

        assertThat(response.token()).isEqualTo("jwt-walkin");
        assertThat(response.user().roleCode()).isEqualTo("customer");
        assertThat(response.user().fullName()).isEqualTo("Khách quầy");
        assertThat(walkIn.getAccountUser().getId()).isEqualTo(50L);

        ArgumentCaptor<AppUser> userCaptor = ArgumentCaptor.forClass(AppUser.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getFullName()).isEqualTo("Khách quầy");
        assertThat(userCaptor.getValue().getUserType()).isEqualTo(UserType.CUSTOMER);

        verify(auditLogService).record(eq(50L), eq("link_account"), eq("customer"), eq(77L), any());
        verify(customerRepository, never()).findCodesByPrefix(any());
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

    // ---- Report 5.1 Phase1: UTCID06-09 (token verification failures surfaced through AuthService) ----

    /** UTCID06: Stub verifier, malformed token ("invalid-token") -> 400 "idToken không hợp lệ". */
    @Test
    void loginWithOtp_invalidStubToken_returns400IdTokenInvalid() {
        AuthService service = authServiceWith(new StubFirebaseTokenVerifier());

        assertThatThrownBy(() -> service.loginWithOtp(new OtpLoginRequest("invalid-token")))
                .isInstanceOf(ApiException.class)
                .hasMessage("idToken không hợp lệ")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoLookupsOrSession();
    }

    /** UTCID07: blank idToken -> 400 "idToken không hợp lệ" (both verifier modes). */
    @Test
    void loginWithOtp_blankIdToken_returns400IdTokenInvalid() {
        for (FirebaseTokenVerifier verifier : List.of(
                new StubFirebaseTokenVerifier(), new FirebaseAdminTokenVerifier(mock(FirebaseAuth.class)))) {
            AuthService service = authServiceWith(verifier);

            assertThatThrownBy(() -> service.loginWithOtp(new OtpLoginRequest("   ")))
                    .isInstanceOf(ApiException.class)
                    .hasMessage("idToken không hợp lệ")
                    .extracting("status")
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
        verifyNoLookupsOrSession();
    }

    /** UTCID08: Firebase Admin rejects token ("invalid-token") -> 400 "idToken Firebase không hợp lệ hoặc đã hết hạn". */
    @Test
    void loginWithOtp_firebaseRejectsToken_returns400InvalidOrExpired() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        when(firebaseAuth.verifyIdToken("invalid-token")).thenThrow(mock(FirebaseAuthException.class));
        AuthService service = authServiceWith(new FirebaseAdminTokenVerifier(firebaseAuth));

        assertThatThrownBy(() -> service.loginWithOtp(new OtpLoginRequest("invalid-token")))
                .isInstanceOf(ApiException.class)
                .hasMessage("idToken Firebase không hợp lệ hoặc đã hết hạn")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoLookupsOrSession();
    }

    /** UTCID09: valid Firebase token without phone_number claim -> 400 "Token Firebase không có số điện thoại". */
    @Test
    void loginWithOtp_firebaseTokenWithoutPhone_returns400NoPhone() throws Exception {
        FirebaseAuth firebaseAuth = mock(FirebaseAuth.class);
        FirebaseToken decoded = mock(FirebaseToken.class);
        when(decoded.getClaims()).thenReturn(Map.of());
        when(firebaseAuth.verifyIdToken("no-phone-token")).thenReturn(decoded);
        AuthService service = authServiceWith(new FirebaseAdminTokenVerifier(firebaseAuth));

        assertThatThrownBy(() -> service.loginWithOtp(new OtpLoginRequest("no-phone-token")))
                .isInstanceOf(ApiException.class)
                .hasMessage("Token Firebase không có số điện thoại")
                .extracting("status")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoLookupsOrSession();
    }

    private AuthService authServiceWith(FirebaseTokenVerifier verifier) {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("x".repeat(32), "test", Duration.ofHours(1)),
                new SecurityProperties.Cors(List.of("http://localhost")),
                new SecurityProperties.Login(5, Duration.ofMinutes(15), true));
        return new AuthService(
                userRepository,
                authIdentityRepository,
                customerRepository,
                roleRepository,
                auditLogService,
                passwordEncoder,
                tokenService,
                loginAttempts,
                verifier,
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void verifyNoLookupsOrSession() {
        verify(authIdentityRepository, never()).findByProviderAndProviderUid(any(), any());
        verify(userRepository, never()).findWithRoleByPhone(any());
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
