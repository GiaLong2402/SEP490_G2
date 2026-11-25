export interface DailyPoint {
  date: string;
  revenue: number;
  orders: number;
  profit?: number;
}

export interface RankRow {
  label: string;
  value: number;
}

export interface SalesReport {
  days: number;
  fromDate: string;
  toDate: string;
  revenue: number;
  cogs: number;
  profit: number;
  orderCount: number;
  avgOrder: number;
  revenueGrowthPct: number | null;
  profitGrowthPct: number | null;
  orderCountGrowthPct: number | null;
  daily: DailyPoint[];
  topProducts: RankRow[];
  topCustomers: RankRow[];
  byChannel: RankRow[];
  byOrderType: RankRow[];
}
