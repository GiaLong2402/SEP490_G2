import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { SupplierInput, SupplierListItem } from './types';

export interface SupplierQuery extends ListQuery {
  status?: string;
}

export const suppliersApi = {
  list: (query: SupplierQuery, signal?: AbortSignal) => api.get<Page<SupplierListItem>>('/suppliers', query, signal),
  create: (body: SupplierInput) => api.post<SupplierListItem>('/suppliers', body),
  update: (id: ID, body: SupplierInput) => api.put<SupplierListItem>(`/suppliers/${id}`, body),
};
