import type { GoodsReceipt, ID, ISODate, ISODateTime, RawMaterial } from '@/shared/domain/entities';
import type { ReceiptStatus, ReceiptType } from '@/shared/domain/enums';

export interface ReceiptItemView {
  name: string;
  qty: number;
  unit: string;
}

/** Dòng phiếu NVL: SL dự kiến lúc tạo; SL thực nhận / lô / HSD / đơn giá điền khi xác nhận nhập kho. */
export interface ReceiptLineView {
  id: ID;
  rawMaterialId: ID;
  rawMaterialCode: string;
  rawMaterialName: string;
  unit: string;
  qtyExpected: number;
  qtyReceived: number | null;
  lotCode: string | null;
  mfgDate: ISODate | null;
  expDate: ISODate | null;
  unitPrice: number | null;
  /** SL thực nhận × đơn giá. */
  amount: number | null;
}

export interface ReceiptListItem extends GoodsReceipt {
  receiverName: string | null;
  /** Alias of source until Supplier module exists — API mirrors source here. */
  supplierName: string;
  items: ReceiptItemView[];
  status: ReceiptStatus;
  expectedDate: ISODate | null;
  completedAt: ISODateTime | null;
  /** Người xác nhận nhập kho / hủy phiếu. */
  completerName: string | null;
  /** Phiếu NVL đã nhập: tổng tiền theo thực nhận × đơn giá. */
  totalAmount: number | null;
  /** Phiếu NVL: các dòng; phiếu thành phẩm / phiếu NVL cũ: rỗng. */
  lines: ReceiptLineView[];
}

export interface FinishedLineInput {
  variantId: ID;
  lotCode: string | null;
  mfgDate: string | null;
  expDate: string;
  locationId: ID;
  qty: number;
}

export interface RawLineInput {
  rawMaterialId: ID;
  /** Số lượng dự kiến — chưa cộng tồn. */
  qty: number;
}

export interface ReceiptInput {
  receiptType: ReceiptType;
  /** Free-text nguồn hàng (chưa có bảng Supplier). */
  source: string;
  note: string | null;
  finishedLines?: FinishedLineInput[];
  rawLines?: RawLineInput[];
  /** Phiếu NVL: ngày hàng dự kiến về. */
  expectedDate?: ISODate | null;
}

export interface RawReceiptCompleteLineInput {
  lineId: ID;
  qtyReceived: number;
  lotCode: string | null;
  mfgDate: ISODate | null;
  expDate: ISODate | null;
  unitPrice: number | null;
}

export interface RawReceiptCompleteInput {
  lines: RawReceiptCompleteLineInput[];
  note: string | null;
}

/** Nhập kho phiếu thành phẩm chờ (từ lệnh sản xuất). Số khay điền sẵn = số báo đạt, sửa được. */
export interface FinishedReceiptCompleteInput {
  lotCode: string | null;
  mfgDate: string | null;
  expDate: string;
  locationId: ID;
  qty: number;
  note: string | null;
}

export type DiscardReason = 'expired' | 'damaged' | 'other';
export interface DiscardLotInput {
  reason: DiscardReason;
  note: string | null;
}

/** Lô NVL (FEFO: HSD gần nhất trước). */
export interface RawMaterialLot {
  id: ID;
  lotCode: string;
  mfgDate: ISODate | null;
  expDate: ISODate | null;
  /** Trống với lô tồn đầu kỳ. */
  unitPrice: number | null;
  qtyReceived: number;
  qtyRemaining: number;
  receiptId: ID | null;
  receiptCode: string | null;
  receivedAt: ISODateTime;
  note: string | null;
}

export interface RawMaterialInput {
  code: string;
  name: string;
  /** Đơn vị tính trong danh mục /units. */
  unitId: ID;
  reorderLevel: number | null;
  isActive: boolean;
  /** Khu cất trong kho nguyên liệu — bắt buộc khi tạo mới. */
  storageLocationId: ID | null;
}

export type { RawMaterial };
