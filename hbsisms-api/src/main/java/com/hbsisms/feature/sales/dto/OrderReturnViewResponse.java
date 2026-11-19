package com.hbsisms.feature.sales.dto;

import com.hbsisms.feature.sales.ReturnItem;
import com.hbsisms.feature.sales.ReturnStatus;
import com.hbsisms.feature.sales.SalesReturn;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Phiếu trả trên chi tiết đơn (Inter 3 dòng 6). */
public record OrderReturnViewResponse(
        Long id,
        String code,
        Long orderId,
        Long customerId,
        String reason,
        BigDecimal amount,
        List<ReturnItem> items,
        ReturnStatus status,
        Long createdBy,
        Instant createdAt) {

    public static OrderReturnViewResponse from(SalesReturn ret) {
        return new OrderReturnViewResponse(
                ret.getId(),
                ret.getCode(),
                ret.getOrder() == null ? null : ret.getOrder().getId(),
                ret.getCustomer() == null ? null : ret.getCustomer().getId(),
                ret.getReason(),
                ret.getAmount(),
                ret.getItems(),
                ret.getStatus(),
                ret.getCreatedBy() == null ? null : ret.getCreatedBy().getId(),
                ret.getCreatedAt());
    }
}
