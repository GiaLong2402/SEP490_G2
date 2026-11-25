import { api } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import { todayISO } from '@/shared/lib/format';
import type { DailyPoint, RankRow, SalesReport } from './types';

type RevenueDto = {
  fromDate: string;
  toDate: string;
  revenue: number;
  cogs: number;
  profit: number;
  orderCount: number;
  revenueGrowthPct: number | null;
  profitGrowthPct: number | null;
  orderCountGrowthPct: number | null;
  series: { period: string; revenue: number; profit: number; orderCount: number }[];
};

type TopProductDto = {
  productName: string | null;
  sku: string;
  packLabel: string | null;
  revenue: number;
};

type TopCustomerDto = {
  customerName: string;
  orderType: string | null;
  revenue: number;
};

function rangeDays(days: number) {
  const n = Math.min(90, Math.max(1, days));
  const toDate = todayISO();
  const fromDate = todayISO(-(n - 1));
  return { days: n, fromDate, toDate };
}

function productLabel(p: TopProductDto) {
  const name = p.productName?.trim() || p.sku;
  return p.packLabel ? `${name} · ${p.packLabel}` : name;
}

function customerLabel(c: TopCustomerDto) {
  const type = c.orderType ? ` · ${c.orderType}` : '';
  return `${c.customerName}${type}`;
}

export const reportsApi = {
  async sales(days: number): Promise<SalesReport> {
    const range = rangeDays(days);
    const query = { fromDate: range.fromDate, toDate: range.toDate };
    const [rev, products, customers] = await Promise.all([
      api.get<RevenueDto>(API_PATHS.analytics.revenue, { ...query, groupBy: 'day' }),
      api.get<TopProductDto[]>(API_PATHS.analytics.topProducts, { ...query, limit: 8, sortBy: 'revenue' }),
      api.get<TopCustomerDto[]>(API_PATHS.analytics.topCustomers, { ...query, limit: 5, sortBy: 'revenue' }),
    ]);

    const daily: DailyPoint[] = (rev.series ?? []).map((p) => ({
      date: p.period,
      revenue: Number(p.revenue ?? 0),
      orders: Number(p.orderCount ?? 0),
      profit: Number(p.profit ?? 0),
    }));

    const topProducts: RankRow[] = (products ?? []).map((p) => ({
      label: productLabel(p),
      value: Number(p.revenue ?? 0),
    }));
    const topCustomers: RankRow[] = (customers ?? []).map((c) => ({
      label: customerLabel(c),
      value: Number(c.revenue ?? 0),
    }));

    const revenue = Number(rev.revenue ?? 0);
    const orderCount = Number(rev.orderCount ?? 0);

    return {
      days: range.days,
      fromDate: rev.fromDate ?? range.fromDate,
      toDate: rev.toDate ?? range.toDate,
      revenue,
      cogs: Number(rev.cogs ?? 0),
      profit: Number(rev.profit ?? 0),
      orderCount,
      avgOrder: orderCount ? Math.round(revenue / orderCount) : 0,
      revenueGrowthPct: rev.revenueGrowthPct == null ? null : Number(rev.revenueGrowthPct),
      profitGrowthPct: rev.profitGrowthPct == null ? null : Number(rev.profitGrowthPct),
      orderCountGrowthPct: rev.orderCountGrowthPct == null ? null : Number(rev.orderCountGrowthPct),
      daily,
      topProducts,
      topCustomers,
      byChannel: [],
      byOrderType: [],
    };
  },
};
