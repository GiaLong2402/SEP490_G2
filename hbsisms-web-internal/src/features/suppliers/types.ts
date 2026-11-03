import type { Supplier } from '@/shared/domain/entities';

export interface SupplierListItem extends Supplier {
  receiptCount: number;
  lastReceiptAt: string | null;
}

export interface SupplierInput {
  name: string;
  taxCode: string | null;
  contactName: string | null;
  phone: string | null;
  email: string | null;
  address: string | null;
  note: string | null;
  isActive: boolean;
}
