import { api } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { DailyPickingSlip, OrderPickingSlip } from './types';

export const pickingApi = {
  orderSlip: (orderId: number) => api.get<OrderPickingSlip>(API_PATHS.orders.pickingSlip(orderId)),
  dailySlip: (date: string) => api.get<DailyPickingSlip>(API_PATHS.orders.pickingSlipDaily, { date }),
};
