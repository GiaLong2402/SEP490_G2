import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { ReturnableOrder, ReturnInput, ReturnListItem, ReturnStatus } from './types';

export interface ReturnQuery extends ListQuery {
  status?: string;
}

export const returnsApi = {
  list: (query: ReturnQuery, signal?: AbortSignal) => api.get<Page<ReturnListItem>>('/returns', query, signal),
  returnableOrders: (q?: string) => api.get<ReturnableOrder[]>('/returns/returnable-orders', { q }),
  create: (body: ReturnInput) => api.post<ReturnListItem>('/returns', body),
  setStatus: (id: ID, status: ReturnStatus) => api.patch<void>(`/returns/${id}/status`, { status }),
};
