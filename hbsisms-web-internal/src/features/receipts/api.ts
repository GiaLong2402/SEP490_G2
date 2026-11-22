import { api, type ListQuery, type Page, type Query } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { RawMaterial, RawMaterialInput, RawMaterialLot, RawReceiptCompleteInput, ReceiptInput, ReceiptListItem } from './types';

export interface ReceiptQuery extends ListQuery {
  receiptType?: string;
  /** pending | completed | cancelled */
  status?: string;
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
  get: (id: ID, signal?: AbortSignal) => api.get<ReceiptListItem>(`/goods-receipts/${id}`, undefined, signal),
  /** Xác nhận nhập kho phiếu NVL chờ nhập → cộng tồn theo lô. */
  complete: (id: ID, body: RawReceiptCompleteInput) => api.post<ReceiptListItem>(`/goods-receipts/${id}/complete`, body),
  cancel: (id: ID, reason: string | null) => api.post<ReceiptListItem>(`/goods-receipts/${id}/cancel`, { reason }),
  rawMaterialLots: (id: ID, availableOnly = false, signal?: AbortSignal) =>
    api.get<RawMaterialLot[]>(`/raw-materials/${id}/lots`, { availableOnly }, signal),
  rawMaterials: (query: RawMaterialQuery = {}, signal?: AbortSignal) => api.get<RawMaterial[]>('/raw-materials', query, signal),
  getRawMaterial: (id: ID, signal?: AbortSignal) => api.get<RawMaterial>(`/raw-materials/${id}`, undefined, signal),
  createRawMaterial: (body: RawMaterialInput) => api.post<RawMaterial>('/raw-materials', body),
  updateRawMaterial: (id: ID, body: RawMaterialInput) => api.put<RawMaterial>(`/raw-materials/${id}`, body),
};
