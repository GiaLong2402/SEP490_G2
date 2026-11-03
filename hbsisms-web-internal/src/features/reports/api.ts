import { api } from '@/shared/api/client';
import type { SalesReport } from './types';

export const reportsApi = {
  sales: (days: number) => api.get<SalesReport>('/reports/sales', { days }),
};
