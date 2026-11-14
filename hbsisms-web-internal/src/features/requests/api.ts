import { api, type ListQuery, type Page } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
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
  list: (query: RequestQuery, signal?: AbortSignal) =>
    api.get<Page<RequestListItem>>(API_PATHS.salesRequests.root, query, signal),
  get: (id: ID, signal?: AbortSignal) =>
    api.get<RequestListItem>(API_PATHS.salesRequests.byId(id), undefined, signal),
  create: (body: RequestInput) => api.post<RequestListItem>(API_PATHS.salesRequests.root, body),
  update: (id: ID, body: RequestPatch) =>
    api.patch<RequestListItem>(API_PATHS.salesRequests.byId(id), body),
  feasibility: (id: ID, body: FeasibilityInput & { order?: OrderInput | null }) =>
    api.post<FeasibilityResult>(API_PATHS.salesRequests.feasibility(id), body),
  reject: (id: ID, body: RejectInput) =>
    api.post<RequestListItem>(API_PATHS.salesRequests.reject(id), body),
};
