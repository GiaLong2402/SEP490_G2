package com.hbsisms.feature.inventory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hbsisms.common.exception.ApiException;
import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.inventory.dto.FinishedReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptRequest;
import com.hbsisms.feature.inventory.dto.GoodsReceiptResponse;
import com.hbsisms.feature.inventory.dto.RawReceiptCompleteRequest;
import com.hbsisms.feature.inventory.dto.ReceiptCancelRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public GoodsReceiptController(
            GoodsReceiptService goodsReceiptService, ObjectMapper objectMapper, Validator validator) {
        this.goodsReceiptService = goodsReceiptService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @GetMapping
    public PageResponse<GoodsReceiptResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String receiptType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return goodsReceiptService.list(q, receiptType, status, sort, page, pageSize);
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

    /**
     * Xác nhận nhập kho phiếu đang chờ: NVL (dòng 47, body {@code lines[]}) hoặc TP từ lệnh SX (dòng 52,
     * body lô/vị trí/SL — khớp FE).
     */
    @PostMapping("/{id}/complete")
    public GoodsReceiptResponse complete(
            @PathVariable Long id,
            @RequestBody JsonNode body,
            @AuthenticationPrincipal AuthUser actor) {
        Long actorId = actor == null ? null : actor.id();
        ReceiptType type = goodsReceiptService.requireType(id);
        if (type == ReceiptType.RAW) {
            return goodsReceiptService.complete(id, read(body, RawReceiptCompleteRequest.class), actorId);
        }
        if (type == ReceiptType.FINISHED) {
            return goodsReceiptService.completeFinished(
                    id, read(body, FinishedReceiptCompleteRequest.class), actorId);
        }
        throw ApiException.conflict("Loại phiếu không hỗ trợ xác nhận nhập kho");
    }

    /** Hủy phiếu còn chờ nhập. */
    @PostMapping("/{id}/cancel")
    public GoodsReceiptResponse cancel(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ReceiptCancelRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return goodsReceiptService.cancel(
                id, request == null ? null : request.reason(), actor == null ? null : actor.id());
    }

    private <T> T read(JsonNode body, Class<T> type) {
        T request;
        try {
            request = objectMapper.treeToValue(body, type);
        } catch (Exception ex) {
            throw ApiException.badRequest("Dữ liệu không hợp lệ");
        }
        if (request == null) {
            throw ApiException.badRequest("Dữ liệu không hợp lệ");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .collect(Collectors.joining("; "));
            throw ApiException.badRequest(message);
        }
        return request;
    }
}
