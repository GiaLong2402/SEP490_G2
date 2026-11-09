import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { OrderDetail, OrderInput } from '@/features/orders/types';
import type { FeasibilityInput, RejectInput, RequestInput, RequestListItem, RequestPatch } from './types';

export interface RequestQuery extends ListQuery {
  status?: string;
  channel?: string;
  neededFrom?: string;
  neededTo?: string;
  customerId?: number;
}

export interface FeasibilityResult {
  request: RequestListItem;
  order: OrderDetail | null;
}

export const requestsApi = {
  list: (query: RequestQuery, signal?: AbortSignal) => api.get<Page<RequestListItem>>('/sales-requests', query, signal),
  get: (id: ID, signal?: AbortSignal) => api.get<RequestListItem>(`/sales-requests/${id}`, undefined, signal),
  create: (body: RequestInput) => api.post<RequestListItem>('/sales-requests', body),
  update: (id: ID, body: RequestPatch) => api.patch<RequestListItem>(`/sales-requests/${id}`, body),
  feasibility: (id: ID, body: FeasibilityInput & { order?: OrderInput | null }) =>
    api.post<FeasibilityResult>(`/sales-requests/${id}/feasibility`, body),
  reject: (id: ID, body: RejectInput) => api.post<RequestListItem>(`/sales-requests/${id}/reject`, body),
};
