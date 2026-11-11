package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.payos.dto.PayosLinkResponse;
import com.hbsisms.feature.payment.payos.dto.PayosReturnResponse;
import com.hbsisms.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Customer PayOS APIs — link (#33) + return status (#36). */
@RestController
@RequestMapping("/payments/payos")
public class PayosPaymentController {

    private final PayosLinkService payosLinkService;
    private final PayosReturnService payosReturnService;

    public PayosPaymentController(PayosLinkService payosLinkService, PayosReturnService payosReturnService) {
        this.payosLinkService = payosLinkService;
        this.payosReturnService = payosReturnService;
    }

    @GetMapping("/return")
    public PayosReturnResponse getReturn(
            @AuthenticationPrincipal AuthUser user, @RequestParam("orderCode") long orderCode) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        return payosReturnService.getReturn(user.id(), orderCode);
    }

    @PostMapping("/{payosOrderCode}/link")
    public PayosLinkResponse recreateLink(
            @AuthenticationPrincipal AuthUser user, @PathVariable long payosOrderCode) {
        if (user == null) {
            throw ApiException.forbidden("Chưa đăng nhập");
        }
        return payosLinkService.getActiveLink(payosOrderCode, user.id());
    }
}
