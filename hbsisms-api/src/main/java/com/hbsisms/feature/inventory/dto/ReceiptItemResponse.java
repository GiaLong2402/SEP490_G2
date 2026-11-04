package com.hbsisms.feature.inventory.dto;

/** Line summary on a receipt list/detail card. */
public record ReceiptItemResponse(String name, double qty, String unit) {
}
