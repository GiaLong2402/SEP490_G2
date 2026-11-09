package com.hbsisms.feature.user;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.text.Strings;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.role.AppRole;
import com.hbsisms.feature.role.RoleService;
import com.hbsisms.feature.user.dto.UserRequest;
import com.hbsisms.feature.user.dto.UserResponse;
import com.hbsisms.security.PasswordPolicy;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff account management (admin screen). */
@Service
@Transactional(readOnly = true)
public class UserService {

    private static final Sort DEFAULT_SORT = Sort.by(Sort.Direction.ASC, "id");
    private static final Pattern VN_MOBILE = Pattern.compile("^0\\d{9}$");
    static final String PHONE_REQUIRED = "Số điện thoại là bắt buộc với tài khoản nội bộ";
    static final String PHONE_INVALID = "Số điện thoại không hợp lệ";

    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleService roleService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    public PageResponse<UserResponse> searchStaff(String keyword, Short roleId, int page, int pageSize) {
        return PageResponse.from(userRepository
                .findAll(UserSpecifications.staffSearch(keyword, roleId), PageRequests.of(page, pageSize, DEFAULT_SORT))
                .map(UserResponse::from));
    }

    @Transactional
    public UserResponse createStaff(UserRequest request) {
        if (Strings.trimToNull(request.password()) == null) {
            throw ApiException.badRequest("Mật khẩu là bắt buộc khi tạo tài khoản");
        }
        if (PasswordPolicy.exceedsMaxBytes(request.password())) {
            throw ApiException.badRequest("Mật khẩu quá dài (tối đa " + PasswordPolicy.MAX_BYTES + " byte)");
        }
        AppUser user = new AppUser();
        user.setUserType(UserType.STAFF);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        applyProfile(user, request, null);
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse updateStaff(Long id, UserRequest request, Long actorId) {
        AppUser user = userRepository.findWithRoleById(id)
                .filter(found -> found.getUserType() == UserType.STAFF)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy người dùng"));
        boolean deactivating = user.isActive() && !request.isActive();
        if (id.equals(actorId) && (deactivating || !Objects.equals(roleIdOf(user), request.roleId()))) {
            throw ApiException.conflict("Không thể tự khóa hoặc đổi vai trò của chính mình");
        }
        applyProfile(user, request, id);
        AppUser saved = userRepository.saveAndFlush(user);
        if (deactivating) {
            userRepository.incrementTokenVersion(id);
        }
        return UserResponse.from(saved);
    }

    private void applyProfile(AppUser user, UserRequest request, Long currentId) {
        String username = Strings.normalizeKey(request.username());
        String email = Strings.normalizeKey(request.email());
        String phone = requireStaffPhone(request.phone());
        String employeeCode = Strings.trimToNull(request.employeeCode());
        AppRole role = roleService.getAssignableStaffRole(request.roleId());

        ensureUnique(username, userRepository::findByUsername, currentId, "Tên đăng nhập " + username + " đã tồn tại");
        ensureUnique(email, userRepository::findByEmail, currentId, "Email " + email + " đã tồn tại");
        ensureUnique(phone, userRepository::findByPhone, currentId, "Số điện thoại " + phone + " đã tồn tại");
        ensureUnique(employeeCode, userRepository::findByEmployeeCode, currentId,
                "Mã nhân viên " + employeeCode + " đã tồn tại");

        boolean phoneChanged = !phone.equals(user.getPhone());
        user.setUsername(username);
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPhone(phone);
        if (phoneChanged) {
            // New login phone must be re-verified via OTP (task 1).
            user.setPhoneVerifiedAt(null);
        }
        user.setPosition(Strings.trimToNull(request.position()));
        user.setEmployeeCode(employeeCode);
        user.setRole(role);
        user.setActive(request.isActive());
    }

    /** Inter 2 task 3 — staff accounts always have a VN mobile used for OTP login. */
    static String requireStaffPhone(String raw) {
        String value = Strings.trimToNull(raw);
        if (value == null) {
            throw ApiException.badRequest(PHONE_REQUIRED);
        }
        String digits = value.replaceAll("[\\s.-]", "");
        if (digits.startsWith("+84")) {
            digits = "0" + digits.substring(3);
        } else if (digits.startsWith("84") && digits.length() == 11) {
            digits = "0" + digits.substring(2);
        }
        if (!VN_MOBILE.matcher(digits).matches()) {
            throw ApiException.badRequest(PHONE_INVALID);
        }
        return digits;
    }

    private static void ensureUnique(
            String value, Function<String, Optional<AppUser>> finder, Long currentId, String message) {
        if (value == null) {
            return;
        }
        boolean takenByOther = finder.apply(value).filter(other -> !other.getId().equals(currentId)).isPresent();
        if (takenByOther) {
            throw ApiException.conflict(message);
        }
    }

    private static Short roleIdOf(AppUser user) {
        return user.getRole() == null ? null : user.getRole().getId();
    }
}
