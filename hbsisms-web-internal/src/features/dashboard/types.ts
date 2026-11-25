import type { ID } from '@/shared/domain/entities';
import type { DeliveryState, OrderState } from '@/shared/domain/enums';

/** Máy chủ chỉ trả các khối mà vai trò được xem; khối không có quyền là null. */
export interface DashboardSummary {
  sales: {
    revenueToday: number;
    revenueYesterday: number;
    ordersToProcess: number;
    daily: { date: string; revenue: number }[];
    recentOrders: { id: ID; code: string; customerName: string; total: number; state: OrderState }[];
  } | null;
  debt: { overdue: number; total: number; overdueCustomers: { customerId: ID; name: string; overdue: number }[] } | null;
  approvals: { pending: number; items: { id: ID; title: string; link: string | null }[] } | null;
  inventory: {
    available: number;
    /** Tổng SL khả dụng × giá vốn lô (Chủ/Admin); null với role khác. */
    inventoryValue: number | null;
    /** Số lô sắp hết hạn (đủ count; list nearExpiry chỉ preview). */
    nearExpiryLots: number;
    /** Số lô còn qtyExpired > 0 chưa tiêu hủy (task 8). */
    expiredLots: number;
    nearExpiry: { lotCode: string; productName: string; daysLeft: number; qty: number }[];
    lowStock: { sku: string; productName: string; available: number }[];
  } | null;
  deliveries: { today: number; items: { id: ID; code: string | null; customerName: string; timeWindow: string | null; state: DeliveryState }[] } | null;
}
