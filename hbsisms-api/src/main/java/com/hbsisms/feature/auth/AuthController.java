package com.hbsisms.feature.auth;

import com.hbsisms.feature.auth.dto.CurrentUserResponse;
import com.hbsisms.feature.auth.dto.LoginRequest;
import com.hbsisms.feature.auth.dto.LoginResponse;
import com.hbsisms.feature.auth.dto.OtpLoginRequest;
import com.hbsisms.feature.auth.dto.OtpPrecheckRequest;
import com.hbsisms.feature.auth.dto.OtpPrecheckResponse;
import com.hbsisms.feature.auth.dto.OtpRegisterRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final OtpSendRateLimitService otpSendRateLimitService;

    public AuthController(AuthService authService, OtpSendRateLimitService otpSendRateLimitService) {
        this.authService = authService;
        this.otpSendRateLimitService = otpSendRateLimitService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /**
     * Precheck trước khi client gọi Firebase gửi SMS OTP.
     * Quá 5 lần / 15 phút / SĐT → 429 khoá ~30 phút.
     */
    @PostMapping("/otp/precheck")
    public OtpPrecheckResponse otpPrecheck(@Valid @RequestBody OtpPrecheckRequest request) {
        String phone = PhoneNumbers.normalizeVnMobile(request.phone(), "Số điện thoại không hợp lệ");
        int remaining = otpSendRateLimitService.acquireOrThrow(phone);
        return new OtpPrecheckResponse(true, remaining, OtpSendRateLimitService.WINDOW_MINUTES * 60);
    }

    /** Inter 2 task 1 — phone OTP via Firebase ID token (stub verifier in local/dev). */
    @PostMapping("/login/otp")
    public LoginResponse loginOtp(@Valid @RequestBody OtpLoginRequest request) {
        return authService.loginWithOtp(request);
    }

    /** Inter 2 task 2 — customer self-register (create B2C or link walk-in customer by phone). */
    @PostMapping("/register/otp")
    @ResponseStatus(HttpStatus.CREATED)
    public LoginResponse registerOtp(@Valid @RequestBody OtpRegisterRequest request) {
        return authService.registerWithOtp(request);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(@AuthenticationPrincipal AuthUser user) {
        return authService.currentUser(user.id());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal AuthUser user) {
        authService.logout(user.id());
        return ResponseEntity.noContent().build();
    }
}
