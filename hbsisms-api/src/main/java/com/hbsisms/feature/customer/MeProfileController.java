package com.hbsisms.feature.customer;

import com.hbsisms.feature.auth.MeProfileService;
import com.hbsisms.feature.auth.dto.CurrentUserResponse;
import com.hbsisms.feature.auth.dto.MePhoneRequest;
import com.hbsisms.feature.auth.dto.MeProfileRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Inter 2 #26 — hồ sơ & đổi SĐT của khách đang đăng nhập. */
@RestController
@RequestMapping("/me")
public class MeProfileController {

    private final MeProfileService meProfileService;

    public MeProfileController(MeProfileService meProfileService) {
        this.meProfileService = meProfileService;
    }

    @PatchMapping("/profile")
    public CurrentUserResponse updateProfile(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody MeProfileRequest request) {
        return meProfileService.updateProfile(user.id(), request);
    }

    @PostMapping("/phone")
    public CurrentUserResponse changePhone(
            @AuthenticationPrincipal AuthUser user, @Valid @RequestBody MePhoneRequest request) {
        return meProfileService.changePhone(user.id(), request);
    }
}
