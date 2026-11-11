package com.hbsisms.feature.auth;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.feature.auth.dto.CurrentUserResponse;
import com.hbsisms.feature.auth.dto.MePhoneRequest;
import com.hbsisms.feature.auth.dto.MeProfileRequest;
import com.hbsisms.feature.auth.firebase.FirebaseTokenVerifier;
import com.hbsisms.feature.auth.firebase.VerifiedFirebaseToken;
import com.hbsisms.feature.customer.CustomerRepository;
import com.hbsisms.feature.user.AppUser;
import com.hbsisms.feature.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Inter 2 #26 — hồ sơ khách (/me/profile) và đổi SĐT sau OTP (/me/phone). */
@Service
@Transactional(readOnly = true)
public class MeProfileService {

    static final String PHONE_TAKEN = "Số điện thoại đã được dùng bởi tài khoản khác";
    static final String FIREBASE_TAKEN = "Tài khoản Firebase đã liên kết với người dùng khác";

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final FirebaseTokenVerifier firebaseTokenVerifier;
    private final Clock clock;

    public MeProfileService(
            UserRepository userRepository,
            CustomerRepository customerRepository,
            AuthIdentityRepository authIdentityRepository,
            FirebaseTokenVerifier firebaseTokenVerifier,
            Clock clock) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.firebaseTokenVerifier = firebaseTokenVerifier;
        this.clock = clock;
    }

    @Transactional
    public CurrentUserResponse updateProfile(Long userId, MeProfileRequest request) {
        AppUser user = requireUser(userId);
        String fullName = request.fullName().trim();
        String email = Strings.normalizeKey(request.email());

        if (email != null) {
            userRepository
                    .findByEmail(email)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> {
                        throw ApiException.conflict("Email " + email + " đã tồn tại");
                    });
        }

        user.setFullName(fullName);
        user.setEmail(email);
        userRepository.save(user);

        customerRepository.findByAccountUser_Id(userId).ifPresent(customer -> {
            customer.setName(fullName);
            if (email != null) {
                customer.setEmail(email);
            }
            customerRepository.save(customer);
        });

        return CurrentUserResponse.from(requireUser(userId));
    }

    @Transactional
    public CurrentUserResponse changePhone(Long userId, MePhoneRequest request) {
        VerifiedFirebaseToken verified = firebaseTokenVerifier.verify(request.idToken());
        String newPhone = PhoneNumbers.normalizeVnMobile(verified.phone());
        Instant now = clock.instant();

        AppUser user = requireUser(userId);
        if (newPhone.equals(user.getPhone())) {
            return CurrentUserResponse.from(user);
        }

        userRepository
                .findByPhone(newPhone)
                .filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw ApiException.conflict(PHONE_TAKEN);
                });

        authIdentityRepository
                .findByProviderAndProviderUid(AuthIdentity.PROVIDER_FIREBASE, verified.uid())
                .ifPresent(existing -> {
                    if (!existing.getUser().getId().equals(userId)) {
                        throw ApiException.conflict(FIREBASE_TAKEN);
                    }
                });

        AuthIdentity identity = authIdentityRepository
                .findByUser_IdAndProvider(userId, AuthIdentity.PROVIDER_FIREBASE)
                .orElseGet(() -> {
                    AuthIdentity created = new AuthIdentity();
                    created.setUser(user);
                    created.setProvider(AuthIdentity.PROVIDER_FIREBASE);
                    created.setSignInMethod("phone");
                    return created;
                });
        identity.setProviderUid(verified.uid());
        identity.setPhone(newPhone);
        identity.setLastLoginAt(now);
        authIdentityRepository.save(identity);

        user.setPhone(newPhone);
        user.setPhoneVerifiedAt(now);
        userRepository.save(user);

        customerRepository.findByAccountUser_Id(userId).ifPresent(customer -> {
            customer.setPhone(newPhone);
            customerRepository.save(customer);
        });

        return CurrentUserResponse.from(requireUser(userId));
    }

    private AppUser requireUser(Long userId) {
        return userRepository
                .findWithRoleById(userId)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
    }
}
