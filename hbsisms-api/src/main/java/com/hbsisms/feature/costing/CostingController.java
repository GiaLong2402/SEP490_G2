package com.hbsisms.feature.costing;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.costing.dto.CostItemBalanceResponse;
import com.hbsisms.feature.costing.dto.CostPeriodResponse;
import com.hbsisms.feature.costing.dto.DiscardCostPeriodRequest;
import com.hbsisms.feature.costing.dto.OpenCostPeriodRequest;
import com.hbsisms.feature.costing.dto.ReopenCostPeriodRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Task 55 — kỳ giá vốn & số dư theo mặt hàng. */
@RestController
@RequestMapping("/cost-periods")
@PreAuthorize("hasAnyAuthority('inventory', 'admin', 'reports')")
public class CostingController {

    private final CostingService costingService;

    public CostingController(CostingService costingService) {
        this.costingService = costingService;
    }

    @GetMapping
    public List<CostPeriodResponse> list() {
        return costingService.listPeriods();
    }

    @GetMapping("/open")
    public CostPeriodResponse open() {
        return costingService
                .findOpenPeriod()
                .orElseThrow(() -> ApiException.notFound("Chưa có kỳ giá vốn đang mở"));
    }

    @GetMapping("/{id}")
    public CostPeriodResponse get(@PathVariable Long id) {
        return costingService.getPeriod(id);
    }

    @GetMapping("/{id}/balances")
    public List<CostItemBalanceResponse> balances(
            @PathVariable Long id, @RequestParam(required = false) CostItemType itemType) {
        return costingService.listBalances(id, itemType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('admin', 'approve')")
    public CostPeriodResponse openPeriod(
            @Valid @RequestBody OpenCostPeriodRequest request, @AuthenticationPrincipal AuthUser actor) {
        return costingService.openPeriod(request, actor == null ? null : actor.id());
    }

    /** Task 57 — Chủ khóa sổ cuối kỳ. */
    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyAuthority('admin', 'approve')")
    public CostPeriodResponse closePeriod(
            @PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return costingService.closePeriod(id, actor == null ? null : actor.id());
    }

    /** Task 58 — mở lại kỳ đã khóa. */
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyAuthority('admin', 'approve')")
    public CostPeriodResponse reopenPeriod(
            @PathVariable Long id,
            @Valid @RequestBody ReopenCostPeriodRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return costingService.reopenPeriod(id, request, actor == null ? null : actor.id());
    }

    /** Hủy kỳ OPEN mở nhầm (kỳ tháng mới nhất). */
    @PostMapping("/{id}/discard")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyAuthority('admin', 'approve')")
    public void discardOpenPeriod(
            @PathVariable Long id,
            @Valid @RequestBody DiscardCostPeriodRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        costingService.discardOpenPeriod(id, request, actor == null ? null : actor.id());
    }
}
