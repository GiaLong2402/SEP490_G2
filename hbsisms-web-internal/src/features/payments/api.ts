import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { PaymentInput, PaymentListItem } from './types';

export interface PaymentQuery extends ListQuery {
  method?: string;
  direction?: string;
}

export const paymentsApi = {
  list: (query: PaymentQuery, signal?: AbortSignal) => api.get<Page<PaymentListItem>>('/payments', query, signal),
  create: (body: PaymentInput) => api.post<PaymentListItem>('/payments', body),
};
