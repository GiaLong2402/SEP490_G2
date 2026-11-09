package com.hbsisms.feature.auth;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.auth.dto.CurrentUserResponse;
import com.hbsisms.feature.auth.dto.LoginRequest;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpLoginRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.VerifiedFirebaseToken;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import com.hbsisms.security.PasswordPolicy;
import com.hbsisms.security.TokenService;
import com.hbsisms.security.TokenService.IssuedToken;
import java.time.Clock;
import java.time.Instant;
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

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttempts;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final Clock clock;
    /** Compared against when the username does not exist, so response time does not reveal valid usernames. */
    private final String dummyHash;

    public AuthService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            LoginAttemptService loginAttempts,
            FirebaseTokenVerifier firebaseTokenVerifier,
            Clock clock) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.loginAttempts = loginAttempts;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
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
        loginAttempts.recordSuccess(username);
        userRepository.updateLastLoginAt(user.getId(), clock.instant());
        IssuedToken token = tokenService.issue(user);
        return new LoginResponse(token.value(), token.expiresAt(), CurrentUserResponse.from(user));
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
            identity = new AuthIdentity();
            identity.setUser(user);
            identity.setProvider(AuthIdentity.PROVIDER_FIREBASE);
            identity.setProviderUid(verified.uid());
            identity.setSignInMethod("phone");
            identity.setPhone(phone);
            identity = authIdentityRepository.save(identity);
        }

        if (!user.isActive()) {
            throw ApiException.forbidden(ACCOUNT_LOCKED);
        }

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
