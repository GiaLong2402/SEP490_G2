import { mock } from '@/shared/api/mockServer';
import { API_PATHS } from '@/shared/api/httpPaths';
import { orderTypeLabels, saleChannelLabels } from '@/shared/domain/enums';
import { db } from '@/shared/mock/db';
import { customerName, variantLabel } from '@/shared/mock/services';

const localDate = (d: Date) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const add = (map: Map<string, number>, key: string, value: number) => map.set(key, (map.get(key) ?? 0) + value);

function parseRange(query: Record<string, string | undefined>) {
  const toDate = String(query.toDate ?? localDate(new Date()));
  const fromDate = String(query.fromDate ?? toDate);
  return { fromDate, toDate };
}

function completedInRange(fromDate: string, toDate: string) {
  return db.orders.filter((o) => {
    if (o.state !== 'completed') return false;
    const day = localDate(new Date(o.placedAt));
    return day >= fromDate && day <= toDate;
  });
}

function growthPct(current: number, previous: number): number | null {
  if (previous === 0) return null;
  return Math.round(((current - previous) / previous) * 10000) / 100;
}

function shiftRange(fromDate: string, toDate: string) {
  const from = new Date(`${fromDate}T00:00:00`);
  const to = new Date(`${toDate}T00:00:00`);
  const span = Math.round((to.getTime() - from.getTime()) / 86_400_000);
  const prevTo = new Date(from);
  prevTo.setDate(prevTo.getDate() - 1);
  const prevFrom = new Date(prevTo);
  prevFrom.setDate(prevFrom.getDate() - span);
  return { previousFrom: localDate(prevFrom), previousTo: localDate(prevTo) };
}

function totals(orders: typeof db.orders) {
  const revenue = orders.reduce((s, o) => s + o.total, 0);
  let cogs = 0;
  for (const o of orders) {
    for (const l of db.orderLines.filter((x) => x.orderId === o.id)) {
      // Mock không có unitCost — coi COGS ≈ 40% line.
      cogs += Math.round(l.lineTotal * 0.4);
    }
  }
  return { revenue, cogs, profit: revenue - cogs, orderCount: orders.length };
}

mock('GET', API_PATHS.analytics.revenue, ({ query }) => {
  const { fromDate, toDate } = parseRange(query as Record<string, string | undefined>);
  const currentOrders = completedInRange(fromDate, toDate);
  const cur = totals(currentOrders);
  const { previousFrom, previousTo } = shiftRange(fromDate, toDate);
  const prev = totals(completedInRange(previousFrom, previousTo));

  const byDay = new Map<string, { revenue: number; profit: number; orderCount: number }>();
  for (const o of currentOrders) {
    const day = localDate(new Date(o.placedAt));
    const row = byDay.get(day) ?? { revenue: 0, profit: 0, orderCount: 0 };
    row.revenue += o.total;
    row.orderCount += 1;
    const lineCogs = db.orderLines
      .filter((l) => l.orderId === o.id)
      .reduce((s, l) => s + Math.round(l.lineTotal * 0.4), 0);
    row.profit += o.total - lineCogs;
    byDay.set(day, row);
  }
  const series = [...byDay.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([period, v]) => ({
      period,
      revenue: v.revenue,
      cogs: v.revenue - v.profit,
      profit: v.profit,
      marginPct: v.revenue ? Math.round((v.profit / v.revenue) * 10000) / 100 : 0,
      orderCount: v.orderCount,
    }));

  return {
    fromDate,
    toDate,
    groupBy: 'day',
    revenue: cur.revenue,
    cogs: cur.cogs,
    profit: cur.profit,
    marginPct: cur.revenue ? Math.round((cur.profit / cur.revenue) * 10000) / 100 : 0,
    orderCount: cur.orderCount,
    series,
    previousFrom,
    previousTo,
    previousRevenue: prev.revenue,
    previousProfit: prev.profit,
    previousOrderCount: prev.orderCount,
    revenueGrowthPct: growthPct(cur.revenue, prev.revenue),
    profitGrowthPct: growthPct(cur.profit, prev.profit),
    orderCountGrowthPct: growthPct(cur.orderCount, prev.orderCount),
  };
});

mock('GET', API_PATHS.analytics.topProducts, ({ query }) => {
  const { fromDate, toDate } = parseRange(query as Record<string, string | undefined>);
  const limit = Math.min(50, Math.max(1, Number(query.limit ?? 10)));
  const sortBy = String(query.sortBy ?? 'revenue');
  const map = new Map<number, { productName: string; sku: string; packLabel: string; qtyKhay: number; revenue: number; profit: number }>();
  for (const o of completedInRange(fromDate, toDate)) {
    for (const l of db.orderLines.filter((x) => x.orderId === o.id)) {
      const label = variantLabel(l.variantId);
      const row = map.get(l.variantId) ?? {
        productName: label.productName,
        sku: label.sku,
        packLabel: label.packLabel,
        qtyKhay: 0,
        revenue: 0,
        profit: 0,
      };
      row.qtyKhay += l.qtyKhay;
      row.revenue += l.lineTotal;
      row.profit += l.lineTotal - Math.round(l.lineTotal * 0.4);
      map.set(l.variantId, row);
    }
  }
  const rows = [...map.entries()].map(([variantId, v]) => ({ variantId, ...v }));
  rows.sort((a, b) => {
    if (sortBy === 'qty') return b.qtyKhay - a.qtyKhay;
    if (sortBy === 'profit') return b.profit - a.profit;
    return b.revenue - a.revenue;
  });
  return rows.slice(0, limit);
});

mock('GET', API_PATHS.analytics.topCustomers, ({ query }) => {
  const { fromDate, toDate } = parseRange(query as Record<string, string | undefined>);
  const limit = Math.min(50, Math.max(1, Number(query.limit ?? 10)));
  const sortBy = String(query.sortBy ?? 'revenue');
  const map = new Map<string, { customerId: number | null; customerName: string; orderType: string; orderCount: number; revenue: number; profit: number }>();
  for (const o of completedInRange(fromDate, toDate)) {
    const key = o.customerId == null ? 'walkin' : String(o.customerId);
    const row = map.get(key) ?? {
      customerId: o.customerId,
      customerName: customerName(o.customerId) ?? 'Khách vãng lai',
      orderType: o.orderType,
      orderCount: 0,
      revenue: 0,
      profit: 0,
    };
    row.orderCount += 1;
    row.revenue += o.total;
    const cogs = db.orderLines
      .filter((l) => l.orderId === o.id)
      .reduce((s, l) => s + Math.round(l.lineTotal * 0.4), 0);
    row.profit += o.total - cogs;
    map.set(key, row);
  }
  const rows = [...map.values()];
  rows.sort((a, b) => (sortBy === 'profit' ? b.profit - a.profit : b.revenue - a.revenue));
  return rows.slice(0, limit);
});

/** Giữ mock cũ cho channel / loại đơn nếu còn caller khác. */
mock('GET', '/reports/sales', ({ query }) => {
  const days = Math.min(90, Math.max(1, Number(query.days ?? 30)));
  const daily = Array.from({ length: days }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (days - 1 - i));
    return { date: localDate(d), revenue: 0, orders: 0 };
  });
  const inRange = db.orders.filter((o) => localDate(new Date(o.placedAt)) >= daily[0].date);
  const sold = inRange.filter((o) => o.state === 'completed');
  const channel = new Map<string, number>();
  const type = new Map<string, number>();
  sold.forEach((o) => {
    const point = daily.find((p) => p.date === localDate(new Date(o.placedAt)));
    if (point) {
      point.revenue += o.total;
      point.orders += 1;
    }
    add(channel, o.channel ? saleChannelLabels[o.channel] : 'Khác', o.total);
    add(type, orderTypeLabels[o.orderType], o.total);
  });
  const revenue = sold.reduce((sum, o) => sum + o.total, 0);
  const rank = (map: Map<string, number>, limit = 8) =>
    [...map.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value).slice(0, limit);
  return {
    days,
    revenue,
    orderCount: sold.length,
    avgOrder: sold.length ? Math.round(revenue / sold.length) : 0,
    cancelledCount: inRange.length - sold.length,
    daily,
    byChannel: rank(channel),
    byOrderType: rank(type),
    topProducts: [],
    topCustomers: [],
  };
});
