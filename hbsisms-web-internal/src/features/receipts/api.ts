import { api, type ListQuery, type Page, type Query } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { RawMaterial, RawMaterialInput, ReceiptInput, ReceiptListItem } from './types';

export interface ReceiptQuery extends ListQuery {
  receiptType?: string;
}

export interface RawMaterialQuery extends Query {
  q?: string;
  /** true = đang dùng, false = ngừng dùng, omit = tất cả */
  activeOnly?: boolean;
  lowStock?: boolean;
}

export const receiptsApi = {
  list: (query: ReceiptQuery, signal?: AbortSignal) => api.get<Page<ReceiptListItem>>('/goods-receipts', query, signal),
  create: (body: ReceiptInput) => api.post<ReceiptListItem>('/goods-receipts', body),
  rawMaterials: (query: RawMaterialQuery = {}, signal?: AbortSignal) => api.get<RawMaterial[]>('/raw-materials', query, signal),
  getRawMaterial: (id: ID, signal?: AbortSignal) => api.get<RawMaterial>(`/raw-materials/${id}`, undefined, signal),
  createRawMaterial: (body: RawMaterialInput) => api.post<RawMaterial>('/raw-materials', body),
  updateRawMaterial: (id: ID, body: RawMaterialInput) => api.put<RawMaterial>(`/raw-materials/${id}`, body),
};
