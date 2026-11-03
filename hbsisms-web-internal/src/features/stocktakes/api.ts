import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { StocktakeDetail, StocktakeInput, StocktakeListItem } from './types';

export interface StocktakeQuery extends ListQuery {
  status?: string;
}

export const stocktakesApi = {
  list: (query: StocktakeQuery, signal?: AbortSignal) => api.get<Page<StocktakeListItem>>('/stocktakes', query, signal),
  get: (id: ID) => api.get<StocktakeDetail>(`/stocktakes/${id}`),
  create: (body: StocktakeInput) => api.post<StocktakeDetail>('/stocktakes', body),
  saveCounts: (id: ID, counts: { lineId: ID; countedQty: number | null }[]) => api.put<void>(`/stocktakes/${id}/lines`, { counts }),
  complete: (id: ID) => api.post<{ needsApproval: boolean }>(`/stocktakes/${id}/complete`),
  cancel: (id: ID) => api.post<void>(`/stocktakes/${id}/cancel`),
};
