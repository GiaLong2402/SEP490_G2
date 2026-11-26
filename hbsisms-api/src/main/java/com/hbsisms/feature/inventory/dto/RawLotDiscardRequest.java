package com.hbsisms.feature.inventory.dto;

/** Bỏ lô nguyên liệu: {@code reason} = expired / damaged / other; {@code note} bắt buộc khi other. */
public record RawLotDiscardRequest(String reason, String note) {}
