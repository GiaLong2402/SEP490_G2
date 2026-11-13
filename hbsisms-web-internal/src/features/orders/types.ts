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

/**
 * Địa chỉ nhập nhanh khi khách vãng lai chọn giao hàng — BE tạo CustomerAddress.
 * Địa giới 2 cấp (sau sáp nhập 2025): xã/phường + tỉnh/thành phố. `district` deprecated.
 */
export interface ShipAddressInput {
  recipientName?: string | null;
  phone?: string | null;
  line1: string;
  ward?: string | null;
  /** @deprecated Bỏ cấp huyện sau sáp nhập 2025. */
  district?: string | null;
  province?: string | null;
  distanceKm?: number | null;
}

export interface OrderInput {
  /** Null = nhập nhanh vãng lai; BE find-or-create B2C từ phone + customerName. */
  customerId: ID | null;
  /** Tên khi khách vãng lai. */
  customerName?: string | null;
  /** SĐT bắt buộc khi customerId null. */
  phone?: string | null;
  orderType: OrderType;
  channel: SaleChannel;
  paymentMethod: PaymentMethod;
  fulfillmentMethod: FulfillmentMethod;
  shipAddressId: ID | null;
  /** Địa chỉ nhập nhanh (thay shipAddressId) khi vãng lai giao hàng. */
  shipAddress?: ShipAddressInput | null;
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
  channel?: string;
  allocationStatus?: string;
  customerId?: string | number;
  placedFrom?: string;
  placedTo?: string;
  [key: string]: string | number | undefined;
}
