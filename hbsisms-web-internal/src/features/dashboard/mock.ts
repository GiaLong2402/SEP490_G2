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
      return { lotCode: lot?.lotCode ?? '—', productName: variantLabel(s.variantId).productName, daysLeft, qty: s.qtyAvailable + s.qtyHeld };
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
      ? { available: stockRows.reduce((s, r) => s + r.available, 0), nearExpiry: nearExpiry.slice(0, 5), lowStock: stockRows.filter((r) => r.available < configInt('low_stock_threshold', 15)).map((r) => ({ sku: r.sku, productName: r.productName, available: r.available })) }
      : null,
    deliveries: can('orders') || can('inventory')
      ? { today: todayDeliveries.length, items: todayDeliveries.filter((d) => d.state === 'ready' || d.state === 'going').slice(0, 5).map((d) => ({ id: d.id, code: d.code, customerName: customerName(db.orders.find((o) => o.id === d.orderId)?.customerId) ?? 'Khách lẻ', timeWindow: d.timeWindow, state: d.state })) }
      : null,
  };
});
