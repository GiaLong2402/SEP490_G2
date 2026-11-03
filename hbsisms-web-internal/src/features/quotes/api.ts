import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { QuoteDetail, QuoteInput, QuoteListItem, QuoteStatus } from './types';

export interface QuoteQuery extends ListQuery {
  status?: string;
}

export const quotesApi = {
  list: (query: QuoteQuery, signal?: AbortSignal) => api.get<Page<QuoteListItem>>('/quotes', query, signal),
  get: (id: ID) => api.get<QuoteDetail>(`/quotes/${id}`),
  create: (body: QuoteInput) => api.post<QuoteDetail>('/quotes', body),
  setStatus: (id: ID, status: QuoteStatus) => api.patch<void>(`/quotes/${id}/status`, { status }),
  convert: (id: ID) => api.post<{ orderId: ID; orderCode: string }>(`/quotes/${id}/convert`),
};
