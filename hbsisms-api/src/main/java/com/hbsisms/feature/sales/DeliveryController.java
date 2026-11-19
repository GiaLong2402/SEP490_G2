package com.hbsisms.feature.sales;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.sales.dto.DeliveryListItemResponse;
import com.hbsisms.feature.sales.dto.DeliveryUpdateRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Delivery legs: danh sách, cập nhật trạng thái / phân công (FE {@code /deliveries}). */
@RestController
@RequestMapping("/deliveries")
@PreAuthorize("hasAnyAuthority('orders', 'inventory')")
public class DeliveryController {

    private final DeliveryService deliveryService;

    public DeliveryController(DeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * Danh sách đợt giao: lọc ngày, khung giờ, trạng thái, người giao, cách nhận; tìm mã đợt / mã đơn / tên khách.
     * {@code sort=asc} = ngày giao cũ nhất trước; mặc định mới nhất trước.
     */
    @GetMapping
    public PageResponse<DeliveryListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long assignedTo,
            @RequestParam(required = false) String timeWindow,
            @RequestParam(required = false) String fulfillmentMethod,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return deliveryService.search(
                q, state, date, assignedTo, timeWindow, fulfillmentMethod, sort, page, pageSize);
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable Long id,
            @Valid @RequestBody DeliveryUpdateRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        deliveryService.update(id, request, actor == null ? null : actor.id());
    }
}
