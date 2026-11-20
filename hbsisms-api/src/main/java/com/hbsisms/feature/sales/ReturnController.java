package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.ReturnInputRequest;
import com.hbsisms.feature.sales.dto.ReturnInspectDefaultsResponse;
import com.hbsisms.feature.sales.dto.ReturnListItemResponse;
import com.hbsisms.feature.sales.dto.ReturnStatusRequest;
import com.hbsisms.feature.sales.dto.ReturnableOrderResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.util.List;
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

/** Returns API — khớp hbsisms-web-internal {@code features/returns}. */
@RestController
@RequestMapping("/returns")
@PreAuthorize("hasAuthority('orders')")
public class ReturnController {

    private final SalesReturnService salesReturnService;

    public ReturnController(SalesReturnService salesReturnService) {
        this.salesReturnService = salesReturnService;
    }

    @GetMapping
    public PageResponse<ReturnListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return salesReturnService.search(q, status, page, pageSize);
    }

    @GetMapping("/returnable-orders")
    public List<ReturnableOrderResponse> returnableOrders(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long deliveryId) {
        return salesReturnService.returnableOrders(q, deliveryId);
    }

    @GetMapping("/{id}/inspect-defaults")
    public ReturnInspectDefaultsResponse inspectDefaults(@PathVariable Long id) {
        return salesReturnService.inspectDefaults(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReturnListItemResponse create(
            @Valid @RequestBody ReturnInputRequest request, @AuthenticationPrincipal AuthUser actor) {
        return salesReturnService.create(request, actor == null ? null : actor.id());
    }

    @PatchMapping("/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void setStatus(
            @PathVariable Long id,
            @Valid @RequestBody ReturnStatusRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        salesReturnService.setStatus(
                id, request.status(), request.inspections(), actor == null ? null : actor.id());
    }
}
