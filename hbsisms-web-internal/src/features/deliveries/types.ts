import type { Delivery, DeliveryItem, ID } from '@/shared/domain/entities';
import type { DeliveryState, FulfillmentMethod } from '@/shared/domain/enums';

export interface DeliveryListItem extends Delivery {
  orderCode: string;
  customerName: string | null;
  addressText: string | null;
  assigneeName: string | null;
  totalKhay: number;
}

export interface DeliveryInput {
  orderId: ID;
  legLabel: string;
  scheduledDate: string;
  timeWindow: string | null;
  fulfillmentMethod: FulfillmentMethod;
  addressId: ID | null;
  inRadius: boolean;
  distanceKm: number | null;
  assignedTo: ID | null;
  carrier: string | null;
  items: DeliveryItem[];
  note: string | null;
}

export interface DeliveryUpdate {
  state?: DeliveryState;
  assignedTo?: ID | null;
  note?: string;
}

export interface Courier {
  id: ID;
  fullName: string;
  position: string | null;
}
