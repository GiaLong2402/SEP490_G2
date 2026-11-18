package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.OrderCancelRequest;
import com.hbsisms.feature.sales.dto.OrderCountsResponse;
import com.hbsisms.feature.sales.dto.OrderDetailResponse;
import com.hbsisms.feature.sales.dto.OrderInputRequest;
import com.hbsisms.feature.sales.dto.OrderListItemResponse;
import com.hbsisms.feature.sales.dto.OrderReceiptResponse;
import com.hbsisms.feature.sales.dto.OrderTransitionRequest;
import com.hbsisms.feature.sales.dto.OwnerInboxResponse;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * Orders API: list / detail / create / update / transition / cancel / receipt.
 * Inter 3 dòng 5: filter + counts; dòng 12: export; dòng 11: owner-inbox.
 */
@RestController
@RequestMapping("/orders")
@PreAuthorize("hasAuthority('orders')")
public class OrderController {

    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter EXPORT_FILE_TS =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final SalesOrderService salesOrderService;
    private final CounterFulfillmentService counterFulfillmentService;
    private final OrderExportService orderExportService;
    private final OrderOwnerInboxService orderOwnerInboxService;

    public OrderController(
            SalesOrderService salesOrderService,
            CounterFulfillmentService counterFulfillmentService,
            OrderExportService orderExportService,
            OrderOwnerInboxService orderOwnerInboxService) {
        this.salesOrderService = salesOrderService;
        this.counterFulfillmentService = counterFulfillmentService;
        this.orderExportService = orderExportService;
        this.orderOwnerInboxService = orderOwnerInboxService;
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
            @RequestParam(required = false) Long placedBy,
            @RequestParam(required = false) String fulfillmentMethod,
            @RequestParam(required = false) String placedFrom,
            @RequestParam(required = false) String placedTo,
            @RequestParam(required = false) String deliveryFrom,
            @RequestParam(required = false) String deliveryTo,
            @RequestParam(required = false) String timeWindow,
            @RequestParam(required = false) String group,
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
                placedBy,
                fulfillmentMethod,
                placedFrom,
                placedTo,
                deliveryFrom,
                deliveryTo,
                timeWindow,
                group,
                page,
                pageSize);
    }

    /** Đếm theo tab — phải khai báo trước /{id}. */
    @GetMapping("/counts")
    public OrderCountsResponse counts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String allocationStatus,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long placedBy,
            @RequestParam(required = false) String fulfillmentMethod,
            @RequestParam(required = false) String placedFrom,
            @RequestParam(required = false) String placedTo,
            @RequestParam(required = false) String deliveryFrom,
            @RequestParam(required = false) String deliveryTo,
            @RequestParam(required = false) String timeWindow) {
        return salesOrderService.countGroups(
                q,
                orderType,
                paymentStatus,
                channel,
                allocationStatus,
                customerId,
                placedBy,
                fulfillmentMethod,
                placedFrom,
                placedTo,
                deliveryFrom,
                deliveryTo,
                timeWindow);
    }

    /** Việc cần Chủ — phải khai báo trước /{id} (Inter 3 dòng 11). */
    @GetMapping("/owner-inbox")
    @PreAuthorize("hasAuthority('approve')")
    public OwnerInboxResponse ownerInbox() {
        return orderOwnerInboxService.list();
    }

    /** Xuất Excel — phải khai báo trước /{id} (Inter 3 dòng 12). */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String paymentStatus,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String allocationStatus,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Long placedBy,
            @RequestParam(required = false) String fulfillmentMethod,
            @RequestParam(required = false) String placedFrom,
            @RequestParam(required = false) String placedTo,
            @RequestParam(required = false) String deliveryFrom,
            @RequestParam(required = false) String deliveryTo,
            @RequestParam(required = false) String timeWindow,
            @RequestParam(required = false) String group) {
        byte[] body = orderExportService.export(
                q,
                state,
                orderType,
                paymentStatus,
                channel,
                allocationStatus,
                customerId,
                placedBy,
                fulfillmentMethod,
                placedFrom,
                placedTo,
                deliveryFrom,
                deliveryTo,
                timeWindow,
                group);
        String filename = "don-hang-" + LocalDateTime.now().format(EXPORT_FILE_TS) + ".xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(XLSX)
                .body(body);
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
