import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { ReviewStatus } from '@/shared/domain/enums';
import type { ReviewListItem, Voucher, VoucherInput } from './types';

export interface ReviewQuery extends ListQuery {
  status?: string;
  rating?: string;
}

export const marketingApi = {
  vouchers: (query: ListQuery, signal?: AbortSignal) => api.get<Page<Voucher>>('/vouchers', query, signal),
  createVoucher: (body: VoucherInput) => api.post<Voucher>('/vouchers', body),
  updateVoucher: (id: ID, body: VoucherInput) => api.put<Voucher>(`/vouchers/${id}`, body),
  reviews: (query: ReviewQuery, signal?: AbortSignal) => api.get<Page<ReviewListItem>>('/reviews', query, signal),
  setReviewStatus: (id: ID, status: ReviewStatus) => api.patch<void>(`/reviews/${id}/status`, { status }),
};
