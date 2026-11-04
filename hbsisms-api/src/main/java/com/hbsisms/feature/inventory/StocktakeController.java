package com.hbsisms.feature.inventory;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.inventory.dto.StocktakeCompleteResponse;
import com.hbsisms.feature.inventory.dto.StocktakeDetailResponse;
import com.hbsisms.feature.inventory.dto.StocktakeInput;
import com.hbsisms.feature.inventory.dto.StocktakeListItemResponse;
import com.hbsisms.feature.inventory.dto.StocktakeSaveCountsRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/stocktakes")
@PreAuthorize("hasAuthority('inventory')")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    public StocktakeController(StocktakeService stocktakeService) {
        this.stocktakeService = stocktakeService;
    }

    @GetMapping
    public PageResponse<StocktakeListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return stocktakeService.list(q, status, page, pageSize);
    }

    @GetMapping("/{id}")
    public StocktakeDetailResponse get(@PathVariable Long id) {
        return stocktakeService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StocktakeDetailResponse create(
            @Valid @RequestBody StocktakeInput input, @AuthenticationPrincipal AuthUser actor) {
        return stocktakeService.create(input, actor == null ? null : actor.id());
    }

    @PutMapping("/{id}/lines")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveCounts(
            @PathVariable Long id,
            @Valid @RequestBody StocktakeSaveCountsRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        stocktakeService.saveCounts(id, request, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/complete")
    public StocktakeCompleteResponse complete(
            @PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return stocktakeService.complete(id, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        stocktakeService.cancel(id, actor == null ? null : actor.id());
    }
}
