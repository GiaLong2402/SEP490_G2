import type { Stocktake, StocktakeLine } from '@/shared/domain/entities';

export interface StocktakeListItem extends Stocktake {
  creatorName: string | null;
  counterName: string | null;
  lineCount: number;
  countedCount: number;
  diffTotal: number;
  pendingApproval: boolean;
}

export interface StocktakeLineView extends StocktakeLine {
  sku: string;
  productName: string;
  lotCode: string | null;
  locationCode: string | null;
}

export interface StocktakeDetail extends StocktakeListItem {
  lines: StocktakeLineView[];
}

export interface StocktakeInput {
  /** Kho cần kiểm — nên chọn khi có nhiều kho. */
  warehouseId: number | null;
  /** Bỏ trống: toàn kho đã chọn. */
  zone: string | null;
  note: string | null;
}
