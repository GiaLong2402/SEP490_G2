package com.hbsisms.feature.inventory;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.ReceiptCancelRequest;
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

@RestController
@RequestMapping("/goods-receipts")
@PreAuthorize("hasAuthority('inventory')")
public class GoodsReceiptController {

    private final GoodsReceiptService goodsReceiptService;

    public GoodsReceiptController(GoodsReceiptService goodsReceiptService) {
        this.goodsReceiptService = goodsReceiptService;
    }

    @GetMapping
    public PageResponse<GoodsReceiptResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return goodsReceiptService.list(q, receiptType, status, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoodsReceiptResponse create(
            @Valid @RequestBody GoodsReceiptRequest request, @AuthenticationPrincipal AuthUser actor) {
        return goodsReceiptService.create(request, actor == null ? null : actor.id());
    }

    @GetMapping("/{id}")
    public GoodsReceiptResponse get(@PathVariable Long id) {
        return goodsReceiptService.get(id);
    }

    /** Inter 1 dòng 47: xác nhận nhập kho phiếu NVL đang chờ nhập (cộng tồn theo lô). */
    @PostMapping("/{id}/complete")
    public GoodsReceiptResponse complete(
            @PathVariable Long id,
            @Valid @RequestBody RawReceiptCompleteRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return goodsReceiptService.complete(id, request, actor == null ? null : actor.id());
    }

    /** Hủy phiếu còn chờ nhập. */
    @PostMapping("/{id}/cancel")
    public GoodsReceiptResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReceiptCancelRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return goodsReceiptService.cancel(id, request == null ? null : request.reason(), actor == null ? null : actor.id());
    }
}
