import { api } from '@/shared/api/client';
import type { CostItemBalance, CostItemType, CostPeriod, CostRunningBalance } from './types';

export const costingApi = {
  list: () => api.get<CostPeriod[]>('/cost-periods'),
  open: () => api.get<CostPeriod>('/cost-periods/open'),
  get: (id: number) => api.get<CostPeriod>(`/cost-periods/${id}`),
  balances: (id: number, itemType?: CostItemType) =>
    api.get<CostItemBalance[]>(`/cost-periods/${id}/balances`, itemType ? { itemType } : undefined),
  runningBalances: (itemType?: CostItemType) =>
    api.get<CostRunningBalance[]>(
      '/cost-periods/running-balances',
      itemType ? { itemType } : undefined,
    ),
  openPeriod: (yearMonth: string) => api.post<CostPeriod>('/cost-periods', { yearMonth }),
  closePeriod: (id: number) => api.post<CostPeriod>(`/cost-periods/${id}/close`),
  /** Task 58 — mở lại kỳ đã khóa. */
  reopenPeriod: (id: number, reason: string) =>
    api.post<CostPeriod>(`/cost-periods/${id}/reopen`, { reason }),
  /** Hủy kỳ OPEN mở nhầm. */
  discardOpenPeriod: (id: number, reason: string) =>
    api.post<void>(`/cost-periods/${id}/discard`, { reason }),
};
