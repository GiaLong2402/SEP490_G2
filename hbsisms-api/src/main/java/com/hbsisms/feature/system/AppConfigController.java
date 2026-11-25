package com.hbsisms.feature.system;

import com.hbsisms.feature.system.dto.AppConfigResponse;
import com.hbsisms.feature.system.dto.UpdateAppConfigRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Màn Cấu hình — Admin sửa tham số app_config (không gồm cron yml). */
@RestController
@RequestMapping("/admin/config")
@PreAuthorize("hasAuthority('admin')")
public class AppConfigController {

    private final AppConfigService appConfigService;

    public AppConfigController(AppConfigService appConfigService) {
        this.appConfigService = appConfigService;
    }

    @GetMapping
    public List<AppConfigResponse> list() {
        return appConfigService.listAll();
    }

    @PutMapping("/{key}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable String key,
            @Valid @RequestBody UpdateAppConfigRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        appConfigService.update(key, request.value(), actor == null ? null : actor.id());
    }
}
