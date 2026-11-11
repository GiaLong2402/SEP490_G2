package com.hbsisms.feature.payment.payos;

import com.hbsisms.feature.payment.payos.dto.PayosWebhookAck;
import com.hbsisms.feature.payment.payos.dto.PayosWebhookEnvelope;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public PayOS webhook (Excel #34). No JWT — verified via HMAC signature. */
@RestController
@RequestMapping("/payments/payos")
public class PayosWebhookController {

    private final PayosWebhookService payosWebhookService;

    public PayosWebhookController(PayosWebhookService payosWebhookService) {
        this.payosWebhookService = payosWebhookService;
    }

    @PostMapping("/webhook")
    public PayosWebhookAck webhook(@RequestBody PayosWebhookEnvelope envelope) {
        return payosWebhookService.handle(envelope);
    }
}
