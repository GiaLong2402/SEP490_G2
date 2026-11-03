import type { ID } from '@/shared/domain/entities';
import type { SellUnit } from '@/shared/domain/enums';
import type { PricingLineInput } from '@/features/pricing';

export interface CatalogItem {
  variantId: ID;
  sku: string;
  barcode: string | null;
  productName: string;
  packLabel: string;
  categoryId: ID | null;
  categoryName: string | null;
  listPrice: number;
  packPerCase: number;
  /** Số khay bán được, đã loại lô quá hạn. */
  available: number;
}

export type PosPaymentMethod = 'cash' | 'bank_transfer' | 'credit_net';

export const posPaymentLabels: Record<PosPaymentMethod, string> = { cash: 'Tiền mặt', bank_transfer: 'Chuyển khoản', credit_net: 'Công nợ' };

/** Mỗi quy cách chỉ có một dòng; qty giữ dạng chuỗi để nhập dở vẫn hiển thị đúng. */
export interface PosLine {
  variantId: ID;
  unit: SellUnit;
  qty: string;
}

/** Một đơn đang mở, lưu nháp ở trình duyệt của nhân viên. */
export interface PosTab {
  id: string;
  number: number;
  customerId: ID | null;
  lines: PosLine[];
  discount: string;
  paymentMethod: PosPaymentMethod;
  tendered: string;
  reference: string;
  note: string;
}

export interface CheckoutInput {
  /** Khóa chống trùng: gửi lại cùng giá trị thì không tạo đơn lần hai. */
  clientRef: string;
  customerId: ID | null;
  paymentMethod: PosPaymentMethod;
  discountTotal: number;
  /** Tổng nhân viên đang thấy; máy chủ tính ra khác thì từ chối. */
  expectedTotal: number;
  tendered: number | null;
  reference: string | null;
  note: string | null;
  lines: PricingLineInput[];
}

export interface Receipt {
  orderId: ID;
  code: string;
  placedAt: string;
  customerName: string;
  cashierName: string;
  paymentMethod: PosPaymentMethod;
  lines: { variantId: ID; productName: string; packLabel: string; qtyLabel: string; unitPrice: number; lineTotal: number }[];
  subtotal: number;
  discountTotal: number;
  total: number;
  tendered: number | null;
  change: number | null;
  /** Nợ sau đơn này, chỉ có với đơn công nợ. */
  debtAfter: number | null;
}
