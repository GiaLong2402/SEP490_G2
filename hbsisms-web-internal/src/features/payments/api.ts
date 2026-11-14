import { api, type ListQuery, type Page } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { PaymentInput, PaymentListItem, PaymentReconcileFlag, PaymentRefundInput } from './types';

export interface PaymentQuery extends ListQuery {
  method?: string;
  direction?: string;
  status?: string;
  channel?: string;
  orderId?: number | string;
  reconcileFlag?: string;
  /** "asc" = cũ nhất trước; bỏ trống = mới nhất trước */
  sort?: string;
}

export const paymentsApi = {
  list: (query: PaymentQuery, signal?: AbortSignal) =>
    api.get<Page<PaymentListItem>>(API_PATHS.payments.root, query, signal),
  create: (body: PaymentInput) => api.post<PaymentListItem>(API_PATHS.payments.root, body),
  refund: (body: PaymentRefundInput) => api.post<PaymentListItem>(API_PATHS.payments.refund, body),
  markReconcile: (id: number | string, flag: PaymentReconcileFlag) =>
    api.patch<PaymentListItem>(API_PATHS.payments.reconcile(id), { flag }),
};
