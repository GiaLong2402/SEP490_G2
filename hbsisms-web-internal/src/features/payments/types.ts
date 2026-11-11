import type { ID, Payment } from '@/shared/domain/entities';
import type { PaymentMethod, PaymentReconcileFlag, PayTxnStatus, SaleChannel } from '@/shared/domain/enums';

export interface PaymentListItem extends Payment {
  orderCode: string | null;
  customerName: string | null;
  receiverName: string | null;
  channel: SaleChannel | null;
  reconcileFlag: PaymentReconcileFlag;
  needsRefund: boolean;
}

export interface PaymentInput {
  customerId: ID | null;
  orderId: ID | null;
  method: PaymentMethod;
  amount: number;
  reference: string | null;
  note: string | null;
}

/** Body of POST /payments/refund (Excel #40). */
export interface PaymentRefundInput {
  orderId: ID;
  sourcePaymentId: ID | null;
  method: PaymentMethod;
  amount: number;
  reference: string | null;
  note: string | null;
}

export type { PaymentReconcileFlag, PayTxnStatus };
