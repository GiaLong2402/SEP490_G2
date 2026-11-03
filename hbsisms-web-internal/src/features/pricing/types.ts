import type { ID } from '@/shared/domain/entities';
import type { PriceBasis, SellUnit } from '@/shared/domain/enums';

export interface PricingLineInput {
  variantId: ID;
  unit: SellUnit;
  qty: number;
  /** Có giá trị khi nhân viên nhập tay, ghi đè giá hệ thống. */
  manualPrice?: number | null;
}

export interface PricedLine {
  variantId: ID;
  unit: SellUnit;
  qty: number;
  qtyKhay: number;
  unitPrice: number;
  priceBasis: PriceBasis;
  lineTotal: number;
  available: number;
}

export interface PricingRequest {
  customerId: ID | null;
  paymentMethod?: string;
  shippingFee?: number;
  discountTotal?: number;
  lines: PricingLineInput[];
}

export interface PricingResult {
  lines: PricedLine[];
  subtotal: number;
  total: number;
  isOverLimit: boolean;
  overLimitAmount: number;
}
