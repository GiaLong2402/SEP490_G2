import type { GoodsReceipt, ID, RawMaterial } from '@/shared/domain/entities';
import type { ReceiptType } from '@/shared/domain/enums';

export interface ReceiptItemView {
  name: string;
  qty: number;
  unit: string;
}

export interface ReceiptListItem extends GoodsReceipt {
  receiverName: string | null;
  /** Alias of source until Supplier module exists — API mirrors source here. */
  supplierName: string;
  items: ReceiptItemView[];
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
  qty: number;
}

export interface ReceiptInput {
  receiptType: ReceiptType;
  /** Free-text nguồn hàng (chưa có bảng Supplier). */
  source: string;
  note: string | null;
  finishedLines?: FinishedLineInput[];
  rawLines?: RawLineInput[];
}

export interface RawMaterialInput {
  code: string;
  name: string;
  unit: string;
  reorderLevel: number | null;
  isActive: boolean;
}

export type { RawMaterial };
