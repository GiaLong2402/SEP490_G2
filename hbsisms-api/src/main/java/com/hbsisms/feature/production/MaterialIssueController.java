package com.hbsisms.feature.production;

import com.hbsisms.feature.production.dto.MaterialIssueConfirmRequest;
import com.hbsisms.feature.production.dto.MaterialIssueResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/material-issues")
@PreAuthorize("hasAuthority('inventory')")
public class MaterialIssueController {

    private final MaterialIssueService materialIssueService;

    public MaterialIssueController(MaterialIssueService materialIssueService) {
        this.materialIssueService = materialIssueService;
    }

    @GetMapping("/{id}")
    public MaterialIssueResponse get(@PathVariable Long id) {
        return materialIssueService.get(id);
    }

    /**
     * Xác nhận xuất NVL (FEFO). Sơ đồ Inter bước duyệt xuất — admin/owner;
     * warehouse xem phiếu nhưng không confirm.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAuthority('inventory') and @roles.ownerOrAdmin()")
    public MaterialIssueResponse confirm(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) MaterialIssueConfirmRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return materialIssueService.confirm(id, request, actor == null ? null : actor.id());
    }
}
