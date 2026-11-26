import { mock } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';
import { configInt, customerName, debtAging, requireUser, variantLabel } from '@/shared/mock/services';

const localDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
const dateOffset = (n: number) => {
  const d = new Date();
  d.setDate(d.getDate() + n);
  return localDate(d);
};
const orderDate = (o: { placedAt: string }) => localDate(new Date(o.placedAt));

mock('GET', '/dashboard/summary', ({ userId }) => {
  const user = requireUser(userId);
  const permissions = db.roles.find((r) => r.id === user.roleId)?.permissions ?? [];
  const can = (p: (typeof permissions)[number]) => permissions.includes(p);
  const active = db.orders.filter((o) => o.state !== 'cancelled');
  const revenueOn = (day: string) => active.filter((o) => orderDate(o) === day).reduce((s, o) => s + o.total, 0);

  const debtRows = db.customers.filter((c) => c.customerType === 'b2b').map((c) => {
    const b = debtAging(c.id);
    return { customerId: c.id, name: c.name, debt: c.currentDebt, overdue: b.d1_30 + b.d31_60 + b.d60 };
  });

  const stockRows = db.variants.map((v) => ({ ...variantLabel(v.id), available: db.stock.filter((s) => s.variantId === v.id).reduce((sum, s) => sum + s.qtyAvailable, 0) }));
  const expiryDays = configInt('expiry_warning_days', 5);
  const nearExpiry = db.stock
    .map((s) => {
      const lot = db.lots.find((l) => l.id === s.lotId);
      const daysLeft = lot?.expDate ? Math.round((new Date(`${lot.expDate}T00:00:00`).getTime() - new Date(`${dateOffset(0)}T00:00:00`).getTime()) / 86_400_000) : 999;
      return { lotCode: lot?.lotCode ?? '—', productName: variantLabel(s.variantId).productName, daysLeft, qty: s.qtyAvailable };
    })
    .filter((r) => r.daysLeft <= expiryDays && r.qty > 0)
    .sort((a, b) => a.daysLeft - b.daysLeft);

  const pendingApprovals = db.approvals.filter((a) => a.status === 'pending');
  const todayDeliveries = db.deliveries.filter((d) => d.scheduledDate === dateOffset(0));

  return {
    sales: can('orders')
      ? {
          revenueToday: revenueOn(dateOffset(0)),
          revenueYesterday: revenueOn(dateOffset(-1)),
          ordersToProcess: db.orders.filter((o) => ['pending', 'confirmed', 'preparing', 'to_ship'].includes(o.state)).length,
          daily: Array.from({ length: 14 }, (_, i) => ({ date: dateOffset(i - 13), revenue: revenueOn(dateOffset(i - 13)) })),
          recentOrders: [...db.orders].sort((a, b) => b.placedAt.localeCompare(a.placedAt)).slice(0, 6).map((o) => ({ id: o.id, code: o.code, customerName: customerName(o.customerId) ?? 'Khách lẻ', total: o.total, state: o.state })),
        }
      : null,
    debt: can('debt')
      ? { overdue: debtRows.reduce((s, r) => s + r.overdue, 0), total: debtRows.reduce((s, r) => s + r.debt, 0), overdueCustomers: debtRows.filter((r) => r.overdue > 0).sort((a, b) => b.overdue - a.overdue).slice(0, 3) }
      : null,
    approvals: can('approve')
      ? { pending: pendingApprovals.length, items: pendingApprovals.slice(0, 4).map((a) => ({ id: a.id, title: a.title, link: a.refType === 'sales_order' ? `/orders/${a.refId}` : '/approvals' })) }
      : null,
    inventory: can('inventory')
      ? (() => {
          const available = stockRows.reduce((s, r) => s + r.available, 0);
          const inventoryValue = db.stock.reduce((sum, s) => {
            const variant = db.variants.find((v) => v.id === s.variantId);
            // Mock: dùng listPrice làm proxy giá vốn; API thật lấy unitPrice phiếu nhập theo lotCode.
            const unitCost = variant?.listPrice ?? 0;
            return sum + s.qtyAvailable * unitCost;
          }, 0);
          const expiredLots = new Set(
            db.stock.filter((s) => s.qtyExpired > 0).map((s) => s.lotId),
          ).size;
          return {
            available,
            inventoryValue,
            nearExpiryLots: nearExpiry.length,
            expiredLots,
            nearExpiry: nearExpiry.slice(0, 5),
            lowStock: stockRows
              .filter((r) => r.available < configInt('low_stock_threshold', 15))
              .map((r) => ({ sku: r.sku, productName: r.productName, available: r.available })),
          };
        })()
      : null,
    deliveries: can('orders') || can('inventory')
      ? { today: todayDeliveries.length, items: todayDeliveries.filter((d) => d.state === 'ready' || d.state === 'going').slice(0, 5).map((d) => ({ id: d.id, code: d.code, customerName: customerName(db.orders.find((o) => o.id === d.orderId)?.customerId) ?? 'Khách lẻ', timeWindow: d.timeWindow, state: d.state })) }
      : null,
  };
});

/** Dòng 10 — bản giả của GET /dashboard/tasks (chỉ nhóm duyệt + tồn thấp; mock chưa có phiếu nhập / lệnh SX chờ). */
mock('GET', '/dashboard/tasks', ({ userId }) => {
  const user = requireUser(userId);
  const permissions = db.roles.find((r) => r.id === user.roleId)?.permissions ?? [];
  const can = (p: (typeof permissions)[number]) => permissions.includes(p);
  const threshold = configInt('low_stock_threshold', 15);
  const staleBefore = Date.now() - 24 * 3_600_000;

  const pendingApprovals = db.approvals
    .filter((a) => a.status === 'pending')
    .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const approvalGroup = (key: 'damage_report' | 'customer_price' | 'other_approval', label: string, link: string, rows: typeof pendingApprovals) => {
    const items = rows.map((a) => ({
      code: null,
      title: a.title,
      createdAt: a.createdAt,
      dueDate: null,
      urgent: new Date(a.createdAt).getTime() < staleBefore,
      link: a.refType === 'sales_order' ? `/orders/${a.refId}` : `/approvals?approvalType=${a.approvalType}`,
    }));
    return { key, label, count: items.length, urgentCount: items.filter((i) => i.urgent).length, link, items: items.slice(0, 5) };
  };
  const groups = can('approve')
    ? [
        approvalGroup('damage_report', 'Phiếu báo hỏng chờ duyệt', '/approvals?approvalType=damage_report', pendingApprovals.filter((a) => a.approvalType === 'damage_report')),
        approvalGroup('customer_price', 'Giá riêng B2B chờ duyệt', '/approvals?approvalType=price_list', pendingApprovals.filter((a) => a.approvalType === 'price_list')),
        approvalGroup('other_approval', 'Yêu cầu duyệt khác', '/approvals', pendingApprovals.filter((a) => a.approvalType !== 'damage_report' && a.approvalType !== 'price_list')),
      ].filter((g) => g.count > 0)
    : [];

  return {
    lowStock: can('inventory')
      ? {
          threshold,
          finished: db.variants
            .map((v) => ({ ...variantLabel(v.id), available: db.stock.filter((s) => s.variantId === v.id).reduce((sum, s) => sum + s.qtyAvailable, 0) }))
            .filter((r) => r.available < threshold)
            .sort((a, b) => a.available - b.available)
            .map((r) => ({ sku: r.sku, productName: r.productName, available: r.available })),
          raw: db.rawMaterials
            .filter((m) => m.isActive && m.reorderLevel !== null && m.reorderLevel > 0 && m.qtyOnHand < m.reorderLevel)
            .map((m) => ({ id: m.id, code: m.code, name: m.name, unit: m.unit, qtyOnHand: m.qtyOnHand, reorderLevel: m.reorderLevel as number, locationCode: m.storageLocationCode ?? null })),
        }
      : null,
    pending: can('approve') || can('inventory')
      ? { total: groups.reduce((s, g) => s + g.count, 0), urgent: groups.reduce((s, g) => s + g.urgentCount, 0), groups }
      : null,
  };
});
