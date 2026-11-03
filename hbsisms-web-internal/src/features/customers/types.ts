import type { Customer, CustomerAddress, CustomerPrice, ID } from '@/shared/domain/entities';
import type { AddressKind, CustomerType } from '@/shared/domain/enums';

export type CustomerListItem = Customer & { orderCount: number };

export interface CustomerPriceRow extends CustomerPrice {
  sku: string;
  productName: string;
  packLabel: string;
  listPrice: number;
  /** Giá đề xuất đang chờ duyệt; giá đang áp dụng vẫn là unitPrice. */
  pendingPrice: number | null;
  /** Ghi chú duyệt/từ chối gần nhất — staff xem trên tab Giá riêng. */
  decisionNote?: string | null;
}

export interface CustomerOrderRow {
  id: ID;
  code: string;
  placedAt: string;
  total: number;
  state: import('@/shared/domain/enums').OrderState;
}

export interface CustomerDetail extends Customer {
  addresses: CustomerAddress[];
  prices: CustomerPriceRow[];
  recentOrders: CustomerOrderRow[];
}

export interface CustomerInput {
  customerType: CustomerType;
  name: string;
  email: string | null;
  phone: string | null;
  taxCode: string | null;
  contactName: string | null;
  contactPhone: string | null;
  creditLimit: number;
  netDays: number;
  isActive: boolean;
  note: string | null;
}

export interface AddressInput {
  kind: AddressKind;
  recipientName: string | null;
  phone: string | null;
  line1: string;
  ward: string | null;
  district: string | null;
  province: string | null;
  distanceKm: number | null;
  isDefault: boolean;
}

export interface PriceProposal {
  variantId: ID;
  unitPrice: number;
  effectiveFrom: string | null;
  effectiveTo: string | null;
}
