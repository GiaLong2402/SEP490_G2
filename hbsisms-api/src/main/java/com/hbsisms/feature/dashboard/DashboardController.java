package com.hbsisms.feature.dashboard;

import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse;
import com.hbsisms.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Tổng quan vận hành — task 8 KPI cards (+ khối FE đang dùng). Mọi user đã login. */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal AuthUser actor) {
        return dashboardService.summary(actor);
    }
}
