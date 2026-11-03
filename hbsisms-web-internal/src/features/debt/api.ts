import { api } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { DebtAgingResponse, DebtLedgerRow } from './types';

export const debtApi = {
  aging: (q?: string) => api.get<DebtAgingResponse>('/debt/aging', { q }),
  ledger: (customerId: ID) => api.get<DebtLedgerRow[]>(`/debt/${customerId}/ledger`),
};
