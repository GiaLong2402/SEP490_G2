import type { DamageReport, ID } from '@/shared/domain/entities';

export interface DamageListItem extends DamageReport {
  sku: string;
  productName: string;
  lotCode: string | null;
  locationId: ID | null;
  locationCode: string | null;
  reporterName: string | null;
  reviewerName: string | null;
}

export interface DamageInput {
  variantId: ID;
  lotId: ID;
  locationId: ID;
  qty: number;
  reason: string;
  note: string | null;
}
