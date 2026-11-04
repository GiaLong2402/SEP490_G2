import type { ID, Location, StockLedger } from '@/shared/domain/entities';

/** v_variant_stock + danh sách lô theo FEFO. */
export interface VariantStockRow {
  variantId: ID;
  sku: string;
  packLabel: string;
  productName: string;
  available: number;
  held: number;
  quarantine: number;
  expired: number;
  isLow: boolean;
  lots: LotStockRow[];
}

export interface LotStockRow {
  stockId: ID;
  lotId: ID;
  lotCode: string;
  variantId: ID;
  sku: string;
  productName: string;
  locationId: ID;
  locationCode: string;
  mfgDate: string | null;
  expDate: string | null;
  daysLeft: number | null;
  available: number;
  held: number;
  quarantine: number;
  expired: number;
}

export interface InventorySummary {
  available: number;
  held: number;
  quarantine: number;
  nearExpiryLots: number;
  lowStockVariants: number;
  lowStockThreshold: number;
  expiryWarningDays: number;
}

/** GET /inventory/alerts — tồn thấp + sắp hết hạn (task 32). */
export interface InventoryAlerts {
  available: number;
  lowStockThreshold: number;
  expiryWarningDays: number;
  lowStock: { sku: string; productName: string; available: number }[];
  nearExpiry: { lotCode: string; productName: string; daysLeft: number; qty: number }[];
}

export interface LedgerRow extends StockLedger {
  sku: string;
  productName: string;
  lotCode: string | null;
  locationCode: string | null;
  toLocationCode: string | null;
  creatorName: string | null;
}

export interface MoveInput {
  stockId: ID;
  toLocationId: ID;
  qty: number;
}

export interface AdjustInput {
  stockId: ID;
  qtyDelta: number;
  note: string;
}

/** Vị trí kèm nhãn sẵn để hiển thị trong ô chọn. */
export interface LocationOption extends Location {
  label: string;
}
