package com.hbsisms.feature.payment.payos;

import com.hbsisms.common.exception.ApiException;
import com.hbsisms.feature.payment.payos.dto.PayosLinkResponse;
import com.hbsisms.security.AuthUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer PayOS link APIs (Excel #33 — recreate/reuse link; return status is #36). */
@RestController
@RequestMapping("/payments/payos")
public class PayosPaymentController {

    private final PayosLinkService payosLinkService;

    public PayosPaymentController(PayosLinkService payosLinkService) {
        this.payosLinkService = payosLinkService;
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
