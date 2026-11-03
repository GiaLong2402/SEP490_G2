import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { DamageInput, DamageListItem } from './types';

export interface DamageQuery extends ListQuery {
  status?: string;
}

export const damageApi = {
  list: (query: DamageQuery, signal?: AbortSignal) => api.get<Page<DamageListItem>>('/damage-reports', query, signal),
  create: (body: DamageInput) => api.post<DamageListItem>('/damage-reports', body),
};
