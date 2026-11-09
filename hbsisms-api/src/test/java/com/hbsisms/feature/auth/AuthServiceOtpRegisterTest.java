package com.hbsisms.feature.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hbsisms.TestFixtures;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpRegisterRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
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
class AuthServiceOtpRegisterTest {

    private static final Instant NOW = Instant.parse("2026-01-01T08:00:00Z");
    private static final String UID = "firebase-uid-cust";
    private static final String PHONE = "0911222333";

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
    private AppRole customerRole;

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
        customerRole = TestFixtures.role((short) 5, DefaultRoles.CUSTOMER, Set.of());
        when(roleRepository.findByCode(DefaultRoles.CUSTOMER)).thenReturn(Optional.of(customerRole));
        when(firebaseTokenVerifier.verify("tok")).thenReturn(new VerifiedFirebaseToken(UID, PHONE));
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.empty());
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.empty());
        when(authIdentityRepository.save(any(AuthIdentity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(AppUser.class))).thenAnswer(inv -> {
            AppUser u = inv.getArgument(0);
            if (u.getId() == null) {
                ReflectionTestUtils.setField(u, "id", 50L);
            }
            return u;
        });
        when(userRepository.findWithRoleById(50L)).thenAnswer(inv -> {
            AppUser u = new AppUser();
            ReflectionTestUtils.setField(u, "id", 50L);
            u.setFullName("Nguyễn Văn A");
            u.setPhone(PHONE);
            u.setUserType(UserType.CUSTOMER);
            u.setRole(customerRole);
            return Optional.of(u);
        });
        when(tokenService.issue(any(AppUser.class))).thenReturn(new IssuedToken("jwt-cust", NOW.plusSeconds(60)));
    }

    @Test
    void registerWithOtp_newCustomer_createsUserAndB2c() {
        when(customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(PHONE))
                .thenReturn(Optional.empty());
        when(customerRepository.findCodesByPrefix("KH-")).thenReturn(List.of("KH-001"));
        when(customerRepository.save(any(Customer.class))).thenAnswer(inv -> {
            Customer c = inv.getArgument(0);
            ReflectionTestUtils.setField(c, "id", 90L);
            return c;
        });

        LoginResponse response =
                authService.registerWithOtp(new OtpRegisterRequest("tok", "Nguyễn Văn A", "a@mail.com"));

        assertThat(response.token()).isEqualTo("jwt-cust");
        assertThat(response.user().roleCode()).isEqualTo("customer");

        ArgumentCaptor<Customer> customerCaptor = ArgumentCaptor.forClass(Customer.class);
        verify(customerRepository).save(customerCaptor.capture());
        Customer created = customerCaptor.getValue();
        assertThat(created.getCode()).isEqualTo("KH-002");
        assertThat(created.getCustomerType()).isEqualTo(CustomerType.B2C);
        assertThat(created.getPhone()).isEqualTo(PHONE);
        assertThat(created.getAccountUser().getId()).isEqualTo(50L);

        verify(auditLogService).record(eq(50L), eq("register"), eq("customer"), eq(90L), any());
    }

    @Test
    void registerWithOtp_walkInCustomer_linksExistingProfile() {
        Customer walkIn = new Customer();
        ReflectionTestUtils.setField(walkIn, "id", 77L);
        walkIn.setCode("KH-010");
        walkIn.setCustomerType(CustomerType.B2C);
        walkIn.setName("Khách quầy");
        walkIn.setPhone(PHONE);
        when(customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(PHONE))
                .thenReturn(Optional.of(walkIn));
        when(customerRepository.save(walkIn)).thenReturn(walkIn);

        LoginResponse response = authService.registerWithOtp(new OtpRegisterRequest("tok", "Khách quầy", null));

        assertThat(response.token()).isEqualTo("jwt-cust");
        assertThat(walkIn.getAccountUser().getId()).isEqualTo(50L);
        verify(auditLogService).record(eq(50L), eq("link_account"), eq("customer"), eq(77L), any());
        verify(customerRepository, never()).findCodesByPrefix(any());
    }

    @Test
    void registerWithOtp_phoneAlreadyHasAccount_returns409() {
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(TestFixtures.staff(1L, "x", customerRole)));

        assertThatThrownBy(() -> authService.registerWithOtp(new OtpRegisterRequest("tok", "A", null)))
                .isInstanceOf(ApiException.class)
                .hasMessage(AuthService.PHONE_ALREADY_REGISTERED)
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerWithOtp_firebaseAlreadyLinked_returns409() {
        when(authIdentityRepository.findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, UID))
                .thenReturn(Optional.of(new AuthIdentity()));

        assertThatThrownBy(() -> authService.registerWithOtp(new OtpRegisterRequest("tok", "A", null)))
                .extracting("status")
                .isEqualTo(HttpStatus.CONFLICT);
        verify(userRepository, never()).save(any());
    }
}
