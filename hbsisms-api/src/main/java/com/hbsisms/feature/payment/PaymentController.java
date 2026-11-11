package com.hbsisms.feature.payment;

import com.hbsisms.common.web.PageRequests;
import com.hbsisms.common.web.PageResponse;
import com.hbsisms.feature.payment.dto.PaymentInputRequest;
import com.hbsisms.feature.payment.dto.PaymentListItemResponse;
import com.hbsisms.feature.payment.dto.PaymentReconcileRequest;
import com.hbsisms.feature.payment.dto.PaymentRefundRequest;
import com.hbsisms.security.AuthUser;
import jakarta.validation.Valid;
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

/**
 * Payments API: list + thu + đối soát (#39) + ghi nhận hoàn (#40).
 */
@RestController
@RequestMapping("/payments")
@PreAuthorize("hasAnyAuthority('debt', 'orders')")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping
    public PageResponse<PaymentListItemResponse> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String method,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String reconcileFlag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "" + PageRequests.DEFAULT_PAGE_SIZE) int pageSize) {
        return paymentService.search(
                q, method, direction, status, channel, orderId, reconcileFlag, page, pageSize);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentListItemResponse create(
            @Valid @RequestBody PaymentInputRequest request, @AuthenticationPrincipal AuthUser actor) {
        return paymentService.create(request, actor == null ? null : actor.id());
    }

    @PostMapping("/refund")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentListItemResponse refund(
            @Valid @RequestBody PaymentRefundRequest request, @AuthenticationPrincipal AuthUser actor) {
        return paymentService.recordRefund(request, actor == null ? null : actor.id());
    }

    @PatchMapping("/{id}/reconcile")
    public PaymentListItemResponse reconcile(
            @PathVariable Long id,
            @Valid @RequestBody PaymentReconcileRequest request,
            @AuthenticationPrincipal AuthUser actor) {
        return paymentService.markReconcile(id, request.flag(), actor == null ? null : actor.id());
    }
}
