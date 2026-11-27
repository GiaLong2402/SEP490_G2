export type CostPeriodStatus = 'OPEN' | 'CLOSED';
export type CostItemType = 'RAW_MATERIAL' | 'FINISHED';

export interface CostPeriod {
  id: number;
  yearMonth: string;
  periodStart: string;
  status: CostPeriodStatus;
  openedAt: string;
  openedByUserId: number | null;
  closedAt: string | null;
  closedByUserId: number | null;
  reopenReason: string | null;
  balanceCount: number;
}

export interface CostItemBalance {
  id: number;
  periodId: number;
  itemType: CostItemType;
  itemId: number;
  itemCode: string;
  itemName: string;
  openingQty: number;
  openingValue: number;
  inboundQty: number;
  inboundValue: number;
  outboundQty: number;
  outboundValue: number;
  closingQty: number;
  closingValue: number;
  avgUnitCost: number | null;
}
