import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type {
  ReturnableOrder,
  ReturnInput,
  ReturnInspectDefaults,
  ReturnInspectLine,
  ReturnListItem,
  ReturnStatus,
} from './types';

export interface ReturnQuery extends ListQuery {
  status?: string;
}

export const returnsApi = {
  list: (query: ReturnQuery, signal?: AbortSignal) => api.get<Page<ReturnListItem>>('/returns', query, signal),
  returnableOrders: (q?: string, deliveryId?: ID) =>
    api.get<ReturnableOrder[]>('/returns/returnable-orders', {
      q,
      deliveryId: deliveryId ?? undefined,
    }),
  inspectDefaults: (id: ID) => api.get<ReturnInspectDefaults>(`/returns/${id}/inspect-defaults`),
  create: (body: ReturnInput) => api.post<ReturnListItem>('/returns', body),
  setStatus: (id: ID, status: ReturnStatus, inspections?: ReturnInspectLine[] | null) =>
    api.patch<void>(`/returns/${id}/status`, {
      status,
      ...(inspections != null ? { inspections } : {}),
    }),
};
