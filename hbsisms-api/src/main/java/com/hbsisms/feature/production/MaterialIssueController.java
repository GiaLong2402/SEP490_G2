package com.hbsisms.feature.production;

import com.hbsisms.feature.production.dto.MaterialIssueResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
