package com.hbsisms.feature.auth;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.auth.dto.CurrentUserResponse;
import com.hbsisms.feature.auth.dto.LoginRequest;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpLoginRequest;
import com.hbsisms.feature.auth.dto.OtpRegisterRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.VerifiedFirebaseToken;
import com.hbsisms.feature.customer.Customer;
import com.hbsisms.feature.customer.CustomerCodeGenerator;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.customer.CustomerType;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.DefaultRoles;
import com.hbsisms.feature.role.RoleRepository;
import com.hbsisms.feature.system.AuditLogService;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.feature.user.UserType;
import com.hbsisms.security.PasswordPolicy;
import com.hbsisms.security.SecurityProperties;
import com.hbsisms.security.TokenService;
import com.hbsisms.security.TokenService.IssuedToken;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {

    /** 400, not 401: the frontend treats 401 as "session expired" and redirects. */
    static final String INVALID_CREDENTIALS = "Sai tên đăng nhập hoặc mật khẩu";
    static final String ACCOUNT_LOCKED = "Tài khoản đã bị khóa";
    /** Staff/customer phone not provisioned yet — do not auto-create accounts on OTP login (task 1). */
    static final String ACCOUNT_NOT_PROVISIONED =
            "Tài khoản chưa được cấp. Liên hệ Admin để gắn số điện thoại đăng nhập";
    /** Inter 2 task 4 — non-admin password login blocked when AUTH_PASSWORD_LOGIN_ALL_ROLES=false. */
    static final String PASSWORD_LOGIN_USE_OTP =
            "Tài khoản này phải đăng nhập bằng OTP. Dùng POST /auth/login/otp";
    static final String PHONE_ALREADY_REGISTERED = "SĐT đã có tài khoản. Hãy đăng nhập bằng OTP";
    static final String FIREBASE_ALREADY_LINKED = "Tài khoản Firebase đã đăng ký. Hãy đăng nhập bằng OTP";

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final AuditLogService auditLogService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final SecurityProperties securityProperties;
    private final Clock clock;
    /** Compared against when the username does not exist, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            CustomerRepository customerRepository,
            RoleRepository roleRepository,
            AuditLogService auditLogService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            LoginAttemptService loginAttempts,
            FirebaseTokenVerifier firebaseTokenVerifier,
            SecurityProperties securityProperties,
            Clock clock) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.customerRepository = customerRepository;
        this.roleRepository = roleRepository;
        this.auditLogService = auditLogService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.securityProperties = securityProperties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Runs without a surrounding transaction so the slow BCrypt check does not hold a DB connection,
     * and writes only {@code last_login_at} so it cannot overwrite a concurrent lock or logout.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public LoginResponse login(LoginRequest request) {
        String username = Strings.normalizeKey(request.username());
        if (!loginAttempts.tryAcquire(username)) {
            throw ApiException.tooManyRequests(
                    "Đăng nhập sai quá nhiều lần, vui lòng thử lại sau " + loginAttempts.lockMinutes() + " phút");
        }
        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (!passwordMatches(user, request.password())) {
            throw ApiException.badRequest(INVALID_CREDENTIALS);
        }
        if (!user.isActive()) {
            throw ApiException.forbidden(ACCOUNT_LOCKED);
        }
        if (!passwordLoginAllowed(user)) {
            throw ApiException.forbidden(PASSWORD_LOGIN_USE_OTP);
        }
        loginAttempts.recordSuccess(username);
        userRepository.updateLastLoginAt(user.getId(), clock.instant());
        IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), CurrentUserResponse.from(user));
    }

    /** Production: only admin. Dev: every role when {@code AUTH_PASSWORD_LOGIN_ALL_ROLES=true}. */
    private boolean passwordLoginAllowed(AppUser user) {
        if (securityProperties.login().passwordLoginAllRoles()) {
            return true;
        }
        return user.getRole() != null && DefaultRoles.ADMIN.equals(user.getRole().getCode());
    }

    /**
     * Task 1 Inter 2 — OTP login via Firebase (or stub) ID token.
     * Links {@code auth_identity(firebase, uid)} when missing; never creates a new {@link AppUser}.
     */
    @Transactional
    public LoginResponse loginWithOtp(OtpLoginRequest request) {
        VerifiedFirebaseToken verified = firebaseTokenVerifier.verify(request.idToken());
        String phone = PhoneNumbers.normalizeVnMobile(verified.phone());
        Instant now = clock.instant();

        AuthIdentity identity = authIdentityRepository
                .findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, verified.uid())
                .orElse(null);

        AppUser user;
        if (identity != null) {
            user = identity.getUser();
        } else {
            user = userRepository.findWithRoleByPhone(phone).orElse(null);
            if (user == null) {
                throw ApiException.forbidden(ACCOUNT_NOT_PROVISIONED);
            }
            if (authIdentityRepository.existsByUser_IdAndProvider(user.getId(), AuthIdentity.PROVIDER_FIREBASE)) {
                throw ApiException.conflict("Tài khoản đã liên kết Firebase khác. Liên hệ Admin");
            }
            identity = newAuthIdentity(user, verified.uid(), phone);
            identity = authIdentityRepository.save(identity);
        }

        if (!user.isActive()) {
            throw ApiException.forbidden(ACCOUNT_LOCKED);
        }

        return completeOtpSession(user, identity, phone, now);
    }

    /**
     * Task 2 Inter 2 — customer self-registration.
     * Creates {@code app_user} (role customer) + B2C customer, or links a walk-in customer with the same phone.
     */
    @Transactional
    public LoginResponse registerWithOtp(OtpRegisterRequest request) {
        VerifiedFirebaseToken verified = firebaseTokenVerifier.verify(request.idToken());
        String phone = PhoneNumbers.normalizeVnMobile(verified.phone());
        String fullName = request.fullName().trim();
        String email = Strings.normalizeKey(request.email());
        Instant now = clock.instant();

        if (authIdentityRepository
                .findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, verified.uid())
                .isPresent()) {
            throw ApiException.conflict(FIREBASE_ALREADY_LINKED);
        }
        if (userRepository.findByPhone(phone).isPresent()) {
            throw ApiException.conflict(PHONE_ALREADY_REGISTERED);
        }
        if (email != null && userRepository.findByEmail(email).isPresent()) {
            throw ApiException.conflict("Email " + email + " đã tồn tại");
        }

        AppRole customerRole = roleRepository
                .findByCode(DefaultRoles.CUSTOMER)
                .orElseThrow(() -> ApiException.badRequest("Thiếu role customer trong hệ thống"));

        AppUser user = new AppUser();
        user.setUserType(UserType.CUSTOMER);
        user.setFullName(fullName);
        user.setPhone(phone);
        user.setEmail(email);
        user.setRole(customerRole);
        user.setActive(true);
        user.setPhoneVerifiedAt(now);
        user.setLastLoginAt(now);
        user = userRepository.save(user);

        AuthIdentity identity = newAuthIdentity(user, verified.uid(), phone);
        identity.setLastLoginAt(now);
        identity = authIdentityRepository.save(identity);

        Customer walkIn = customerRepository.findFirstByPhoneAndAccountUserIsNullOrderByIdAsc(phone).orElse(null);
        if (walkIn != null) {
            walkIn.setAccountUser(user);
            customerRepository.save(walkIn);
            auditLogService.record(
                    user.getId(),
                    "link_account",
                    "customer",
                    walkIn.getId(),
                    Map.of("phone", phone, "via", "otp_register", "customerCode", walkIn.getCode()));
        } else {
            Customer created = new Customer();
            created.setCode(nextB2cCode());
            created.setCustomerType(CustomerType.B2C);
            created.setName(fullName);
            created.setPhone(phone);
            created.setEmail(email);
            created.setAccountUser(user);
            created.setCreditLimit(BigDecimal.ZERO);
            created.setNetDays((short) 0);
            created.setCurrentDebt(BigDecimal.ZERO);
            created.setActive(true);
            created = customerRepository.save(created);
            auditLogService.record(
                    user.getId(),
                    "register",
                    "customer",
                    created.getId(),
                    Map.of("phone", phone, "via", "otp_register", "customerCode", created.getCode()));
        }

        return completeOtpSession(user, identity, phone, now);
    }

    private String nextB2cCode() {
        String prefix = CustomerCodeGenerator.prefixFor(CustomerType.B2C);
        return CustomerCodeGenerator.nextCode(CustomerType.B2C, customerRepository.findCodesByPrefix(prefix));
    }

    private static AuthIdentity newAuthIdentity(AppUser user, String uid, String phone) {
        AuthIdentity identity = new AuthIdentity();
        identity.setUser(user);
        identity.setProvider(AuthIdentity.PROVIDER_FIREBASE);
        identity.setProviderUid(uid);
        identity.setSignInMethod("phone");
        identity.setPhone(phone);
        return identity;
    }

    private LoginResponse completeOtpSession(AppUser user, AuthIdentity identity, String phone, Instant now) {
        identity.setLastLoginAt(now);
        identity.setPhone(phone);
        identity.setSignInMethod("phone");
        authIdentityRepository.save(identity);

        userRepository.updateLastLoginAt(user.getId(), now);
        userRepository.updatePhoneVerifiedAt(user.getId(), now);

        AppUser withRole = userRepository.findWithRoleById(user.getId()).orElse(user);
        IssuedToken token = tokenService.issue(withRole);
        return new LoginResponse(token.value(), token.expiresAt(), CurrentUserResponse.from(withRole));
    }

    public CurrentUserResponse currentUser(Long userId) {
        return userRepository.findWithRoleById(userId)
                .map(CurrentUserResponse::from)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }

    /** Stateless JWTs cannot be deleted, so logout revokes every token of the user (all devices). */
    @Transactional
    public void logout(Long userId) {
        userRepository.incrementTokenVersion(userId);
    }

    private boolean passwordMatches(AppUser user, String rawPassword) {
        if (PasswordPolicy.exceedsMaxBytes(rawPassword)) {
            return false;
        }
        if (user == null || user.getPasswordHash() == null) {
            passwordEncoder.matches(rawPassword, dummyHash);
            return false;
        }
        return passwordEncoder.matches(rawPassword, user.getPasswordHash());
    }
}
