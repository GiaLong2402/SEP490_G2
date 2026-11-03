import type { CustomerDebtLedger, ID } from '@/shared/domain/entities';

/** v_debt_aging + thông tin hạn mức. */
export interface DebtAgingRow {
  customerId: ID;
  customer: string;
  code: string;
  contactName: string | null;
  phone: string | null;
  creditLimit: number;
  netDays: number;
  currentDebt: number;
  d0: number;
  d1_30: number;
  d31_60: number;
  d60: number;
  overdue: number;
}

export interface DebtTotals {
  currentDebt: number;
  d0: number;
  d1_30: number;
  d31_60: number;
  d60: number;
}

export interface DebtAgingResponse {
  rows: DebtAgingRow[];
  totals: DebtTotals;
}

export interface DebtLedgerRow extends CustomerDebtLedger {
  orderCode: string | null;
}
