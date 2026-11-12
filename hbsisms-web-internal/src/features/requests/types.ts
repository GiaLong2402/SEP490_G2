import type { ID, SalesRequest } from '@/shared/domain/entities';
import type { RequestStatus, SaleChannel } from '@/shared/domain/enums';
import type { OrderInput } from '@/features/orders/types';

export interface RequestListItem extends SalesRequest {
  handlerName: string | null;
}

export interface RequestInput {
  customerId: ID | null;
  customerName: string | null;
  contactPhone: string | null;
  contactAddress: string | null;
  channel: SaleChannel;
  summary: string;
}

export interface RequestPatch {
  status: RequestStatus;
  note?: string;
}

export interface FeasibilityInput {
  feasible: boolean;
  fulfillableQty?: number | null;
  shortageQty?: number | null;
  expectedDate?: string | null;
  note?: string | null;
  customerAccepted: boolean;
  order?: OrderInput | null;
}

export interface RejectInput {
  rejectReason: string;
}
