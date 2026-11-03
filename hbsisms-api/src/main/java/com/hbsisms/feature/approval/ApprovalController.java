package com.hbsisms.feature.approval;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.approval.dto.ApprovalDecisionRequest;
import com.hbsisms.feature.approval.dto.ApprovalListItemResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
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

/**
 * Hub phê duyệt. Task 21: duyệt/từ chối giá B2B ({@code customer_price}).
 * FE: màn Approvals — quyền {@code approve} (owner/admin).
 */
@RestController
@RequestMapping("/approvals")
@PreAuthorize("hasAuthority('approve')")
public class ApprovalController {

    private final ApprovalService approvalService;

    public ApprovalController(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @GetMapping
    public PageResponse<ApprovalListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ApprovalStatus status,
            @RequestParam(required = false) ApprovalType approvalType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return approvalService.search(q, status, approvalType, page, pageSize);
    }

    @PostMapping("/{id}/decision")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decide(
            @PathVariable Long id,
            @Valid @RequestBody ApprovalDecisionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        approvalService.decide(id, request, actor.id());
    }
}
