import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { RequestInput, RequestListItem, RequestPatch } from './types';

export interface RequestQuery extends ListQuery {
  status?: string;
  channel?: string;
}

export const requestsApi = {
  list: (query: RequestQuery, signal?: AbortSignal) => api.get<Page<RequestListItem>>('/sales-requests', query, signal),
  create: (body: RequestInput) => api.post<RequestListItem>('/sales-requests', body),
  update: (id: ID, body: RequestPatch) => api.patch<void>(`/sales-requests/${id}`, body),
};
