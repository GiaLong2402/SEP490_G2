import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { ProductDetail } from '@/features/products/types';
import type {
  MaterialIssue,
  ProductionBomProduct,
  ProductionCompleteInput,
  ProductionMaterialPreview,
  ProductionOrder,
  ProductionOrderInput,
  ProductionOrderUpdateInput,
} from './types';

export interface ProductionOrderQuery extends ListQuery {
  status?: string;
}

export const productionApi = {
  // Dòng 48–49 (Duy).
  list: (query: ProductionOrderQuery, signal?: AbortSignal) => api.get<Page<ProductionOrder>>('/production-orders', query, signal),
  get: (id: ID, signal?: AbortSignal) => api.get<ProductionOrder>(`/production-orders/${id}`, undefined, signal),
  create: (body: ProductionOrderInput) => api.post<ProductionOrder>('/production-orders', body),
  update: (id: ID, body: ProductionOrderUpdateInput) => api.put<ProductionOrder>(`/production-orders/${id}`, body),
  approve: (id: ID) => api.post<ProductionOrder>(`/production-orders/${id}/approve`),
  cancel: (id: ID) => api.post<ProductionOrder>(`/production-orders/${id}/cancel`),
  /** Inter1 #48 note: SP đã có BOM — quyền inventory. */
  bomProducts: (signal?: AbortSignal) => api.get<ProductionBomProduct[]>('/production-orders/bom-products', undefined, signal),
  /** Inter1 #48 note: xem trước NVL (tồn dùng được, bỏ lô hết hạn). */
  materialPreview: (productId: ID, plannedQty: number, variantId: ID | null, signal?: AbortSignal) =>
    api.get<ProductionMaterialPreview>(
      '/production-orders/material-preview',
      variantId ? { productId, plannedQty, variantId } : { productId, plannedQty },
      signal,
    ),
  /** VT đóng gói (xốp, túi…) theo số khay vật lý lúc báo hoàn thành. */
  packagingPreview: (productId: ID, physicalTrayCount: number, signal?: AbortSignal) =>
    api.get<ProductionMaterialPreview>(
      '/production-orders/packaging-preview',
      { productId, physicalTrayCount },
      signal,
    ),

  // Dòng 51 (Cường) — đã có; defectQty / defectReason / leftovers cần Cường nhận thêm.
  complete: (id: ID, body: ProductionCompleteInput) => api.post<ProductionOrder>(`/production-orders/${id}/complete`, body),

  // Dòng 50 (Cường) — đã có. Đã chốt: không gửi allocations, máy tự chia FEFO (bỏ qua lô hết hạn).
  materialIssue: (id: ID, signal?: AbortSignal) => api.get<MaterialIssue>(`/material-issues/${id}`, undefined, signal),
  confirmIssue: (id: ID, note: string | null = null) => api.post<MaterialIssue>(`/material-issues/${id}/confirm`, { note }),

  /** Variant để chọn quy cách khi báo làm xong. */
  product: (id: ID, signal?: AbortSignal) => api.get<ProductDetail>(`/products/${id}`, undefined, signal),
};
