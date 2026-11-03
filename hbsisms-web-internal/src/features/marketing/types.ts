import type { ProductReview, Voucher } from '@/shared/domain/entities';
import type { VoucherType } from '@/shared/domain/enums';

export interface ReviewListItem extends ProductReview {
  productName: string;
  customerName: string | null;
}

export interface VoucherInput {
  code: string;
  name: string | null;
  voucherType: VoucherType;
  value: number;
  minOrder: number;
  maxDiscount: number | null;
  startsAt: string | null;
  endsAt: string | null;
  usageLimit: number | null;
  perCustomerLimit: number | null;
  isActive: boolean;
}

export type { Voucher };
