import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { InventorySummary, LedgerRow, LocationOption, LotStockRow, MoveInput, VariantStockRow } from './types';

export interface InventoryQuery extends ListQuery {
  locationId?: string;
  lowOnly?: boolean;
}
export interface LedgerQuery extends ListQuery {
  entryType?: string;
}

export const inventoryApi = {
  summary: () => api.get<InventorySummary>('/inventory/summary'),
  variants: (query: InventoryQuery, signal?: AbortSignal) => api.get<Page<VariantStockRow>>('/inventory/variants', query, signal),
  nearExpiry: (query: ListQuery, signal?: AbortSignal) => api.get<Page<LotStockRow>>('/inventory/near-expiry', query, signal),
  lots: (query: InventoryQuery, signal?: AbortSignal) => api.get<Page<LotStockRow>>('/inventory/lots', query, signal),
  ledger: (query: LedgerQuery, signal?: AbortSignal) => api.get<Page<LedgerRow>>('/inventory/ledger', query, signal),
  locations: () => api.get<LocationOption[]>('/locations'),
  move: (body: MoveInput) => api.post<void>('/inventory/moves', body),
};
