export interface DailyPoint {
  date: string;
  revenue: number;
  orders: number;
}

export interface RankRow {
  label: string;
  value: number;
}

export interface SalesReport {
  days: number;
  revenue: number;
  orderCount: number;
  avgOrder: number;
  cancelledCount: number;
  daily: DailyPoint[];
  byChannel: RankRow[];
  byOrderType: RankRow[];
  topProducts: RankRow[];
  topCustomers: RankRow[];
}
