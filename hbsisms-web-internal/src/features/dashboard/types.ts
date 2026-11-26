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

/** Inter 4 dòng 10 — GET /dashboard/tasks. Khối null = người xem không có quyền. */
export interface DashboardTasks {
  lowStock: {
    /** Ngưỡng chung cho thành phẩm (Cấu hình hệ thống). */
    threshold: number;
    finished: { sku: string; productName: string; available: number }[];
    /** Nguyên liệu đang dùng, tồn dưới mức đặt lại của chính nó. */
    raw: { id: ID; code: string; name: string; unit: string | null; qtyOnHand: number; reorderLevel: number; locationCode: string | null }[];
  } | null;
  pending: {
    total: number;
    urgent: number;
    groups: PendingTaskGroup[];
  } | null;
}

export interface PendingTaskGroup {
  key: 'damage_report' | 'customer_price' | 'other_approval' | 'production_approval' | 'raw_receipt' | 'material_issue' | 'finished_receipt';
  label: string;
  count: number;
  urgentCount: number;
  link: string;
  /** Tối đa 5 việc treo lâu nhất. */
  items: { code: string | null; title: string; createdAt: string | null; dueDate: string | null; urgent: boolean; link: string }[];
}
