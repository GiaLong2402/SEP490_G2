package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.OrderCancelRequest;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.sales.dto.OrderListItemResponse;
import com.hbsisms.feature.sales.dto.OrderReceiptResponse;
import com.hbsisms.feature.sales.dto.OrderTransitionRequest;
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

/**
 * Orders API (Doanh Inter 2): list / detail / create / update / transition / cancel / receipt.
 */
@RestController
@RequestMapping("/orders")
@PreAuthorize("hasAuthority('orders')")
public class OrderController {

    private final SalesOrderService salesOrderService;
    private final CounterFulfillmentService counterFulfillmentService;

    public OrderController(
            SalesOrderService salesOrderService, CounterFulfillmentService counterFulfillmentService) {
        this.salesOrderService = salesOrderService;
        this.counterFulfillmentService = counterFulfillmentService;
    }

    @GetMapping
    public PageResponse<OrderListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String allocationStatus,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String placedFrom,
            @RequestParam(required = false) String placedTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return salesOrderService.search(
                q,
                state,
                orderType,
                paymentStatus,
                channel,
                allocationStatus,
                customerId,
                placedFrom,
                placedTo,
                page,
                pageSize);
    }

    @GetMapping("/{id}")
    public OrderDetailResponse get(@PathVariable Long id) {
        return salesOrderService.getDetail(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderDetailResponse create(
            @Valid @RequestBody OrderInputRequest request, @AuthenticationPrincipal AuthUser actor) {
        return salesOrderService.create(request, actor == null ? null : actor.id());
    }

    @PutMapping("/{id}")
    public OrderDetailResponse update(
            @PathVariable Long id,
            @Valid @RequestBody OrderInputRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return salesOrderService.update(id, request, actor == null ? null : actor.id());
    }

    @GetMapping("/{id}/receipt")
    public OrderReceiptResponse receipt(
            @PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return salesOrderService.receipt(id, actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/transition")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void transition(
            @PathVariable Long id,
            @Valid @RequestBody OrderTransitionRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        salesOrderService.transition(id, request.state(), actor == null ? null : actor.id());
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(
            @PathVariable Long id,
            @Valid @RequestBody OrderCancelRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        salesOrderService.cancel(id, request.reason(), actor == null ? null : actor.id());
    }

    /** Hoàn tất bàn giao tại quầy — OUT đúng lô HOLD, idempotent. */
    @PostMapping("/{id}/complete-at-counter")
    public OrderDetailResponse completeAtCounter(
            @PathVariable Long id, @AuthenticationPrincipal AuthUser actor) {
        return counterFulfillmentService.completeAtCounter(id, actor == null ? null : actor.id());
    }
}
