import type { ID, SalesRequest } from '@/shared/domain/entities';
import type { RequestStatus, SaleChannel } from '@/shared/domain/enums';

export interface RequestListItem extends SalesRequest {
  handlerName: string | null;
}

export interface RequestInput {
  customerId: ID | null;
  customerName: string | null;
  channel: SaleChannel;
  summary: string;
}

export interface RequestPatch {
  status: RequestStatus;
  note?: string;
}
