import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { PaymentInput, PaymentListItem, PaymentReconcileFlag, PaymentRefundInput } from './types';

export interface PaymentQuery extends ListQuery {
  method?: string;
  direction?: string;
  status?: string;
  channel?: string;
  orderId?: number | string;
  reconcileFlag?: string;
}

export const paymentsApi = {
  list: (query: PaymentQuery, signal?: AbortSignal) => api.get<Page<PaymentListItem>>('/payments', query, signal),
  create: (body: PaymentInput) => api.post<PaymentListItem>('/payments', body),
  refund: (body: PaymentRefundInput) => api.post<PaymentListItem>('/payments/refund', body),
  markReconcile: (id: number | string, flag: PaymentReconcileFlag) =>
    api.patch<PaymentListItem>(`/payments/${id}/reconcile`, { flag }),
};
