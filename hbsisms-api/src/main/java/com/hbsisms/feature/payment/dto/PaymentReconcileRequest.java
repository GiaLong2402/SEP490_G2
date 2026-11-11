package com.hbsisms.feature.payment.dto;

import jakarta.validation.constraints.NotBlank;

/** Body for {@code PATCH /payments/{id}/reconcile}. */
public record PaymentReconcileRequest(@NotBlank String flag) {}
