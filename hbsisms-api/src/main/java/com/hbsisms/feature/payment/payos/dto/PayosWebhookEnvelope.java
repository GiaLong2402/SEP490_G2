package com.hbsisms.feature.payment.payos.dto;

import java.util.Map;

/** PayOS webhook body (Excel #34). */
public record PayosWebhookEnvelope(
        String code, String desc, Boolean success, Map<String, Object> data, String signature) {}
