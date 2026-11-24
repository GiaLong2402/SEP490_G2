import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { ProductDetail } from '@/features/products/types';
import type { MaterialIssue, ProductionCompleteInput, ProductionOrder, ProductionOrderInput, ProductionOrderUpdateInput } from './types';

export interface ProductionOrderQuery extends ListQuery {
  status?: string;
}

export const productionApi = {
  // Dòng 48–49 (Duy) — đã có trên main.
  list: (query: ProductionOrderQuery, signal?: AbortSignal) => api.get<Page<ProductionOrder>>('/production-orders', query, signal),
  get: (id: ID, signal?: AbortSignal) => api.get<ProductionOrder>(`/production-orders/${id}`, undefined, signal),
  create: (body: ProductionOrderInput) => api.post<ProductionOrder>('/production-orders', body),
  update: (id: ID, body: ProductionOrderUpdateInput) => api.put<ProductionOrder>(`/production-orders/${id}`, body),
  approve: (id: ID) => api.post<ProductionOrder>(`/production-orders/${id}/approve`),
  cancel: (id: ID) => api.post<ProductionOrder>(`/production-orders/${id}/cancel`),
  // Dòng 51 (Cường) — đã có; defectQty / defectReason / leftovers cần Cường nhận thêm.
  complete: (id: ID, body: ProductionCompleteInput) => api.post<ProductionOrder>(`/production-orders/${id}/complete`, body),

  // Dòng 50 (Cường) — đã có. Đã chốt: không gửi allocations, máy tự chia FEFO (bỏ qua lô hết hạn).
  materialIssue: (id: ID, signal?: AbortSignal) => api.get<MaterialIssue>(`/material-issues/${id}`, undefined, signal),
  confirmIssue: (id: ID, note: string | null = null) => api.post<MaterialIssue>(`/material-issues/${id}/confirm`, { note }),

  /** Thành phẩm cần làm: chỉ sản phẩm có thể sản xuất; variant để chọn quy cách khi báo làm xong. */
  products: (signal?: AbortSignal) => api.get<Page<ProductDetail>>('/products', { page: 1, pageSize: 100 }, signal),
  product: (id: ID, signal?: AbortSignal) => api.get<ProductDetail>(`/products/${id}`, undefined, signal),
};
