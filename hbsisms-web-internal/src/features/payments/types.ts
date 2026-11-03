import type { ID, Payment } from '@/shared/domain/entities';
import type { PaymentMethod } from '@/shared/domain/enums';

export interface PaymentListItem extends Payment {
  orderCode: string | null;
  customerName: string | null;
  receiverName: string | null;
}

export interface PaymentInput {
  customerId: ID | null;
  orderId: ID | null;
  method: PaymentMethod;
  amount: number;
  reference: string | null;
  note: string | null;
}
