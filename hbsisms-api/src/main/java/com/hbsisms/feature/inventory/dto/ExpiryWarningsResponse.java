package com.hbsisms.feature.inventory.dto;

import java.util.List;

/**
 * Task 9 — cảnh báo HSD kèm vị trí kho (FEFO / báo hỏng).
 *
 * <ul>
 *   <li>{@code nearExpiry}: qtyAvailable &gt; 0, HSD trong {@code expiryWarningDays} (hoặc đã quá
 *       hạn nhưng chưa chuyển sang qtyExpired)
 *   <li>{@code expired}: qtyExpired &gt; 0 — ưu tiên lập phiếu báo hỏng
 * </ul>
 */
public record ExpiryWarningsResponse(
        int expiryWarningDays, List<LotStockRowResponse> nearExpiry, List<LotStockRowResponse> expired) {}
