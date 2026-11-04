package com.hbsisms.feature.inventory;

import com.hbsisms.feature.inventory.dto.LotExpireJobResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** Manual trigger for the lot-expire job (task 38) — useful for local testing. */
@RestController
@PreAuthorize("hasAuthority('inventory')")
public class LotExpireController {

    private final LotExpireService lotExpireService;

    public LotExpireController(LotExpireService lotExpireService) {
        this.lotExpireService = lotExpireService;
    }

    @PostMapping("/inventory/jobs/expire-lots")
    public LotExpireJobResponse runExpireLots() {
        return lotExpireService.run();
    }
}
