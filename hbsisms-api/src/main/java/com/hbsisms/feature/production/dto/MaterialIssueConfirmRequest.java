package com.hbsisms.feature.production.dto;

/**
 * Body xác nhận xuất NVL. Server luôn chia lô FEFO (bỏ lô hết hạn); không nhận allocations tay.
 */
public record MaterialIssueConfirmRequest(String note) {}
