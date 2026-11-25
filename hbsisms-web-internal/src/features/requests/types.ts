import type { ID, SalesRequest, SalesRequestLine } from '@/shared/domain/entities';
import type { RequestStatus, SaleChannel, SellUnit } from '@/shared/domain/enums';
import type { OrderInput } from '@/features/orders/types';

export type { SalesRequestLine };

export interface RequestListItem extends SalesRequest {
  handlerName: string | null;
}

export interface RequestLineInput {
  variantId: ID;
  unit: SellUnit;
  qty: number;
}

export interface RequestInput {
  customerId: ID | null;
  customerName: string | null;
  contactPhone: string | null;
  contactAddress: string | null;
  channel: SaleChannel;
  summary: string;
  neededDate?: string | null;
  requestedQty?: number | null;
  availableQty?: number | null;
  lines?: RequestLineInput[];
}

export interface RequestPatch {
  status: RequestStatus;
  note?: string;
}

/** Dòng SP Sales chọn khi ghi khả năng đáp ứng (prefill báo giá). */
export interface FeasibilityInput {
  feasible: boolean;
  fulfillableQty?: number | null;
  shortageQty?: number | null;
  expectedDate?: string | null;
  note?: string | null;
  customerAccepted: boolean;
  order?: OrderInput | null;
  lines?: RequestLineInput[];
}

export interface RejectInput {
  rejectReason: string;
}
