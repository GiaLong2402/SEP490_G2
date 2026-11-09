package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.FeasibilityResultResponse;
import com.hbsisms.feature.sales.dto.SalesRequestCloseRequest;
import com.hbsisms.feature.sales.dto.SalesRequestFeasibilityRequest;
import com.hbsisms.feature.sales.dto.SalesRequestInputRequest;
import com.hbsisms.feature.sales.dto.SalesRequestRejectRequest;
import com.hbsisms.feature.sales.dto.SalesRequestResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sales-requests")
@PreAuthorize("hasAuthority('orders')")
public class SalesRequestController {

    private final SalesRequestService salesRequestService;

    public SalesRequestController(SalesRequestService salesRequestService) {
        this.salesRequestService = salesRequestService;
    }

    @GetMapping
    public PageResponse<SalesRequestResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String neededFrom,
            @RequestParam(required = false) String neededTo,
            @RequestParam(required = false) Long customerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return salesRequestService.search(
                q, status, channel, neededFrom, neededTo, customerId, page, pageSize);
    }

    @GetMapping("/{id}")
    public SalesRequestResponse get(@PathVariable Long id) {
        return salesRequestService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SalesRequestResponse create(
            @Valid @RequestBody SalesRequestInputRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return salesRequestService.create(request, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/feasibility")
    public FeasibilityResultResponse feasibility(
            @PathVariable Long id,
            @Valid @RequestBody SalesRequestFeasibilityRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return salesRequestService.recordFeasibility(id, request, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/reject")
    public SalesRequestResponse reject(
            @PathVariable Long id,
            @Valid @RequestBody SalesRequestRejectRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return salesRequestService.reject(id, request, actor == null ? null : actor.id());
    }

    @PatchMapping("/{id}")
    public SalesRequestResponse patch(
            @PathVariable Long id,
            @RequestBody SalesRequestCloseRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return salesRequestService.patch(id, request, actor == null ? null : actor.id());
    }
}
