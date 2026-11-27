import { api, type ListQuery, type Page, type Query } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { DiscardLotInput, FinishedReceiptCompleteInput, RawMaterial, RawMaterialInput, RawMaterialLot, RawReceiptCompleteInput, ReceiptInput, ReceiptListItem } from './types';

export interface ReceiptQuery extends ListQuery {
  receiptType?: string;
  /** pending | completed | cancelled */
  status?: string;
  /** asc = mã phiếu tăng dần; mặc định giảm dần */
  sort?: string;
}

export interface RawMaterialQuery extends Query {
  q?: string;
  /** true = đang dùng, false = ngừng dùng, omit = tất cả */
  activeOnly?: boolean;
  lowStock?: boolean;
  /** Lọc theo khu cất (Khu lạnh / Khu khô). */
  storageLocationId?: number;
}

export const receiptsApi = {
  list: (query: ReceiptQuery, signal?: AbortSignal) => api.get<Page<ReceiptListItem>>('/goods-receipts', query, signal),
  create: (body: ReceiptInput) => api.post<ReceiptListItem>('/goods-receipts', body),
  get: (id: ID, signal?: AbortSignal) => api.get<ReceiptListItem>(`/goods-receipts/${id}`, undefined, signal),
  /** Xác nhận nhập kho phiếu NVL chờ nhập → cộng tồn theo lô. */
  complete: (id: ID, body: RawReceiptCompleteInput) => api.post<ReceiptListItem>(`/goods-receipts/${id}/complete`, body),
  /**
   * Nhập kho phiếu thành phẩm sinh từ lệnh sản xuất (dòng 52, đã chốt 24/11/2026 — backend chưa nhận phiếu thành phẩm).
   * Xong thì lệnh tự chuyển Hoàn thành; giá vốn / khay lưu vào đơn giá dòng phiếu.
   */
  completeFinished: (id: ID, body: FinishedReceiptCompleteInput) => api.post<ReceiptListItem>(`/goods-receipts/${id}/complete`, body),
  cancel: (id: ID, reason: string | null) => api.post<ReceiptListItem>(`/goods-receipts/${id}/cancel`, { reason }),
  rawMaterialLots: (id: ID, availableOnly = false, signal?: AbortSignal) =>
    api.get<RawMaterialLot[]>(`/raw-materials/${id}/lots`, { availableOnly }, signal),
  /** ĐG bình quân NVL hiện hành (WAC) — dùng định giá xuất SX; chọn lô vẫn FEFO. */
  rawMaterialAvgUnitCost: (id: ID, signal?: AbortSignal) =>
    api.get<{ rawMaterialId: number; avgUnitCost: number | null; source: string }>(
      `/raw-materials/${id}/avg-unit-cost`,
      undefined,
      signal,
    ),
  /** Bỏ phần còn lại của lô NVL (hết hạn / hư) — đã chốt, backend chưa có. Thủ kho / Chủ, không cần duyệt. */
  discardRawLot: (lotId: ID, body: DiscardLotInput) => api.post<RawMaterialLot>(`/raw-material-lots/${lotId}/discard`, body),
  rawMaterials: (query: RawMaterialQuery = {}, signal?: AbortSignal) => api.get<RawMaterial[]>('/raw-materials', query, signal),
  getRawMaterial: (id: ID, signal?: AbortSignal) => api.get<RawMaterial>(`/raw-materials/${id}`, undefined, signal),
  createRawMaterial: (body: RawMaterialInput) => api.post<RawMaterial>('/raw-materials', body),
  updateRawMaterial: (id: ID, body: RawMaterialInput) => api.put<RawMaterial>(`/raw-materials/${id}`, body),
};
