/** Khớp `PickingSlipLineResponse` (BE). */
export interface PickingSlipLine {
  variantId: number;
  productName: string | null;
  sku: string;
  packLabel: string | null;
  lotId: number | null;
  lotCode: string | null;
  expDate: string | null;
  warehouseId: number | null;
  warehouseCode: string | null;
  locationId: number | null;
  locationCode: string | null;
  zone: string | null;
  qtyKhay: number;
}

/** Khớp `PickingSlipResponse`. */
export interface OrderPickingSlip {
  orderId: number;
  orderCode: string;
  customerName: string | null;
  generatedAt: string;
  lines: PickingSlipLine[];
}

/** Khớp `DailyPickingLineResponse`. */
export interface DailyPickingLine {
  orderId: number;
  orderCode: string;
  variantId: number;
  productName: string | null;
  sku: string;
  packLabel: string | null;
  lotId: number | null;
  lotCode: string | null;
  expDate: string | null;
  qtyKhay: number;
}

/** Khớp `DailyPickingGroupResponse`. */
export interface DailyPickingGroup {
  warehouseId: number | null;
  warehouseCode: string | null;
  locationId: number | null;
  locationCode: string | null;
  zone: string | null;
  totalKhay: number;
  lines: DailyPickingLine[];
}

/** Khớp `DailyPickingSlipResponse`. */
export interface DailyPickingSlip {
  date: string;
  generatedAt: string;
  groups: DailyPickingGroup[];
}
