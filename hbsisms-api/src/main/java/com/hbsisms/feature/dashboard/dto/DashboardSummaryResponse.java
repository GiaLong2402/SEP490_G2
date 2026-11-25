package com.hbsisms.feature.dashboard.dto;

import com.hbsisms.feature.sales.DeliveryState;
import com.hbsisms.feature.sales.OrderState;
import java.math.BigDecimal;
import java.util.List;

/**
 * Khớp FE {@code DashboardSummary}. Khối null = role không có quyền xem.
 * Task 8: KPI tồn ({@code inventoryValue}, low stock, near/expired lots) + công nợ quá hạn.
 */
public record DashboardSummaryResponse(
        SalesBlock sales,
        DebtBlock debt,
        ApprovalsBlock approvals,
        InventoryBlock inventory,
        DeliveriesBlock deliveries) {

    public record SalesBlock(
            BigDecimal revenueToday,
            BigDecimal revenueYesterday,
            int ordersToProcess,
            List<DailyRevenue> daily,
            List<RecentOrder> recentOrders) {}

    public record DailyRevenue(String date, BigDecimal revenue) {}

    public record RecentOrder(Long id, String code, String customerName, BigDecimal total, OrderState state) {}

    public record DebtBlock(
            BigDecimal overdue, BigDecimal total, List<OverdueCustomer> overdueCustomers) {}

    public record OverdueCustomer(Long customerId, String name, BigDecimal overdue) {}

    public record ApprovalsBlock(int pending, List<ApprovalItem> items) {}

    public record ApprovalItem(Long id, String title, String link) {}

    public record InventoryBlock(
            int available,
            /** Tổng SL khả dụng × giá vốn lô (Chủ/Admin); null với role khác. */
            BigDecimal inventoryValue,
            /** Số lô sắp hết hạn (đủ, không cắt preview). */
            int nearExpiryLots,
            /** Số lô còn qtyExpired &gt; 0 (chưa tiêu hủy). */
            int expiredLots,
            List<NearExpiryItem> nearExpiry,
            List<LowStockItem> lowStock) {}

    public record NearExpiryItem(String lotCode, String productName, int daysLeft, int qty) {}

    public record LowStockItem(String sku, String productName, int available) {}

    public record DeliveriesBlock(int today, List<DeliveryItem> items) {}

    public record DeliveryItem(
            Long id, String code, String customerName, String timeWindow, DeliveryState state) {}
}
