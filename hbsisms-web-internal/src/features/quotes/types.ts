import type { ID, Quote, QuoteLine } from '@/shared/domain/entities';
import type { QuoteStatus } from '@/shared/domain/enums';
import type { PricingLineInput } from '@/features/pricing';

export interface QuoteListItem extends Quote {
  customerName: string;
  creatorName: string | null;
  convertedOrderId: ID | null;
}

export interface QuoteLineView extends QuoteLine {
  sku: string;
  productName: string;
  packLabel: string;
}

export interface QuoteDetail extends QuoteListItem {
  lines: QuoteLineView[];
  requestCode: string | null;
  convertedOrderCode: string | null;
}

export interface QuoteInput {
  customerId: ID;
  requestId: ID | null;
  validUntil: string | null;
  note: string | null;
  lines: PricingLineInput[];
}

export type { QuoteStatus };
