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
  reason: string;
  items: { variantId: ID; qty: number }[];
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
