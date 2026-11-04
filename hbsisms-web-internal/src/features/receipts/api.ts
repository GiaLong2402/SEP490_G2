import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { RawMaterial, RawMaterialInput, ReceiptInput, ReceiptListItem } from './types';

export interface ReceiptQuery extends ListQuery {
  receiptType?: string;
}

export const receiptsApi = {
  list: (query: ReceiptQuery, signal?: AbortSignal) => api.get<Page<ReceiptListItem>>('/goods-receipts', query, signal),
  create: (body: ReceiptInput) => api.post<ReceiptListItem>('/goods-receipts', body),
  rawMaterials: () => api.get<RawMaterial[]>('/raw-materials'),
  createRawMaterial: (body: RawMaterialInput) => api.post<RawMaterial>('/raw-materials', body),
  updateRawMaterial: (id: ID, body: RawMaterialInput) => api.put<RawMaterial>(`/raw-materials/${id}`, body),
};
