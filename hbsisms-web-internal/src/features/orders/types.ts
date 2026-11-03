import type { Customer, CustomerAddress, Delivery, ID, OrderEvent, Payment, SalesOrder, SalesOrderLine } from '@/shared/domain/entities';
import type { FulfillmentMethod, OrderState, OrderType, PaymentMethod, SaleChannel } from '@/shared/domain/enums';
import type { PricingLineInput } from '@/features/pricing';

export interface OrderListItem extends SalesOrder {
  customerName: string | null;
  customerType: Customer['customerType'] | null;
}

export interface OrderLineView extends SalesOrderLine {
  sku: string;
  productName: string;
  packLabel: string;
  deliveredKhay: number;
}

export interface OrderDeliveryView extends Delivery {
  assigneeName: string | null;
}

export interface OrderEventView extends OrderEvent {
  actorName: string | null;
}

export interface OrderDetail extends OrderListItem {
  customer: Customer | null;
  address: CustomerAddress | null;
  lines: OrderLineView[];
  deliveries: OrderDeliveryView[];
  payments: Payment[];
  events: OrderEventView[];
  paidAmount: number;
  sourceQuoteCode: string | null;
  placedByName: string | null;
  approvedByName: string | null;
  /** Các trạng thái kế tiếp được phép chuyển bằng tay. */
  nextStates: OrderState[];
  canCancel: boolean;
}

export interface OrderInput {
  customerId: ID;
  orderType: OrderType;
  channel: SaleChannel;
  paymentMethod: PaymentMethod;
  fulfillmentMethod: FulfillmentMethod;
  shipAddressId: ID | null;
  shippingFee: number;
  discountTotal: number;
  voucherCode: string | null;
  note: string | null;
  lines: PricingLineInput[];
}

export interface OrderQuery {
  q?: string;
  page?: number;
  pageSize?: number;
  state?: string;
  orderType?: string;
  paymentStatus?: string;
  [key: string]: string | number | undefined;
}
