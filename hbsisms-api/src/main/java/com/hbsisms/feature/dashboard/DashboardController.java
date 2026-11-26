package com.hbsisms.feature.dashboard;

import com.hbsisms.feature.dashboard.dto.DashboardSummaryResponse;
import com.hbsisms.feature.dashboard.dto.DashboardTasksResponse;
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
    private final DashboardTaskService dashboardTaskService;

    public DashboardController(DashboardService dashboardService, DashboardTaskService dashboardTaskService) {
        this.dashboardService = dashboardService;
        this.dashboardTaskService = dashboardTaskService;
    }

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@AuthenticationPrincipal AuthUser actor) {
        return dashboardService.summary(actor);
    }

    /** Inter 4 dòng 10 — tồn thấp (TP + NVL) và việc chờ xử lý theo quyền người xem. */
    @GetMapping("/tasks")
    public DashboardTasksResponse tasks(@AuthenticationPrincipal AuthUser actor) {
        return dashboardTaskService.tasks(actor);
    }
}
