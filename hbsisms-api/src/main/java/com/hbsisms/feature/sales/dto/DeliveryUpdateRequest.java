package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.DeliveryState;

/** Body {@code PATCH /deliveries/{id}} — khớp FE {@code DeliveryUpdate}: đổi trạng thái và/hoặc ghi chú (lý do thất bại). */
public record DeliveryUpdateRequest(DeliveryState state, String note) {}
