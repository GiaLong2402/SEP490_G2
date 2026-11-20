import type { ID, SalesReturn } from '@/shared/domain/entities';
import type { ReturnStatus } from '@/shared/domain/enums';

export interface ReturnItemView {
  variantId: ID;
  lotId: ID | null;
  qty: number;
  amount: number;
  sku: string;
  productName: string;
}

export interface ReturnListItem extends Omit<SalesReturn, 'items'> {
  orderCode: string | null;
  customerName: string | null;
  creatorName: string | null;
  items: ReturnItemView[];
}

export interface ReturnInput {
  orderId: ID;
  /** Đợt giao thất bại — phiếu nhận hàng về (task 29). */
  deliveryId?: ID | null;
  reason: string;
  items: { variantId: ID; qty: number }[];
}

/** Một dòng nhận hàng khi PATCH status=received (task #30). */
export interface ReturnInspectLine {
  variantId: ID;
  lotId?: ID | null;
  locationId: ID;
  /** Giữ tương thích API — luôn true; BE bỏ qua (không khu hàng trả). */
  passed: boolean;
  qty: number;
}

/** GET /returns/{id}/inspect-defaults — gợi ý vị trí đã xuất. quarantine* luôn null. */
export interface ReturnInspectDefaults {
  quarantineLocationId: ID | null;
  quarantineLabel: string | null;
  lines: {
    variantId: ID;
    lotId: ID | null;
    shippedLocationId: ID | null;
    shippedLocationLabel: string | null;
    quarantineLocationId: ID | null;
    quarantineLocationLabel: string | null;
  }[];
}

export type { ReturnStatus };

/** Dòng còn có thể trả của một đơn. */
export interface ReturnableLine {
  variantId: ID;
  sku: string;
  productName: string;
  packLabel: string;
  unitPrice: number;
  maxQty: number;
}

export interface ReturnableOrder {
  id: ID;
  code: string;
  customerName: string | null;
  lines: ReturnableLine[];
}
