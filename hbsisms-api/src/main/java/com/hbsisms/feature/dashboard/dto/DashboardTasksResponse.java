package com.hbsisms.feature.dashboard.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Inter 4 dòng 10 — cảnh báo tồn thấp và việc chờ xử lý cho trang Tổng quan.
 * Khối null = người dùng không có quyền xem khối đó.
 */
public record DashboardTasksResponse(LowStockBlock lowStock, PendingBlock pending) {

    /** Thành phẩm dưới ngưỡng chung (app_config) và nguyên liệu dưới mức đặt lại của chính nó. */
    public record LowStockBlock(int threshold, List<FinishedLow> finished, List<RawLow> raw) {}

    public record FinishedLow(String sku, String productName, int available) {}

    public record RawLow(
            Long id,
            String code,
            String name,
            String unit,
            BigDecimal qtyOnHand,
            BigDecimal reorderLevel,
            String locationCode) {}

    /** Chỉ chứa nhóm có việc; {@code total} = tổng số việc của mọi nhóm. */
    public record PendingBlock(int total, int urgent, List<TaskGroup> groups) {}

    /**
     * Một nhóm việc. {@code items} là tối đa 5 việc treo lâu nhất; {@code count} / {@code urgentCount} tính trên cả nhóm.
     */
    public record TaskGroup(
            String key, String label, int count, int urgentCount, String link, List<TaskItem> items) {}

    /** {@code dueDate}: ngày dự kiến nhập / hạn hoàn thành lệnh (nếu có). */
    public record TaskItem(
            String code, String title, Instant createdAt, LocalDate dueDate, boolean urgent, String link) {}
}
