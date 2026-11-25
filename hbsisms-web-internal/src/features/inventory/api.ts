import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { AdjustInput, ExpiryWarnings, InventoryAlerts, InventorySummary, LedgerRow, LocationOption, LotStockRow, MoveInput, VariantStockRow } from './types';

export interface InventoryQuery extends ListQuery {
  locationId?: string;
  lowOnly?: boolean;
}
export interface LedgerQuery extends ListQuery {
  entryType?: string;
  from?: string;
  to?: string;
}

export interface LotExpireJobResult {
  scanned: number;
  expired: number;
  failed: number;
  asOfDate: string;
}

export const inventoryApi = {
  summary: () => api.get<InventorySummary>('/inventory/summary'),
  alerts: () => api.get<InventoryAlerts>('/inventory/alerts'),
  variants: (query: InventoryQuery, signal?: AbortSignal) => api.get<Page<VariantStockRow>>('/inventory/variants', query, signal),
  nearExpiry: (query: ListQuery, signal?: AbortSignal) => api.get<Page<LotStockRow>>('/inventory/near-expiry', query, signal),
  expired: (query: ListQuery, signal?: AbortSignal) => api.get<Page<LotStockRow>>('/inventory/expired', query, signal),
  /** Task 9 — gộp sắp hết hạn + đã hết hạn kèm vị trí. */
  expiryWarnings: () => api.get<ExpiryWarnings>('/inventory/expiry-warnings'),
  lots: (query: InventoryQuery, signal?: AbortSignal) => api.get<Page<LotStockRow>>('/inventory/lots', query, signal),
  ledger: (query: LedgerQuery, signal?: AbortSignal) => api.get<Page<LedgerRow>>('/inventory/ledger', query, signal),
  locations: () => api.get<LocationOption[]>('/locations'),
  /** API returns 204 No Content. */
  move: (body: MoveInput) => api.post<void>('/inventory/moves', body),
  /** API returns 204 No Content; writes ledger + audit. */
  adjust: (body: AdjustInput) => api.post<void>('/inventory/adjusts', body),
  /** Manual trigger for the daily past-HSD job (task 38). */
  runExpireJob: () => api.post<LotExpireJobResult>('/inventory/jobs/expire-lots'),
};
