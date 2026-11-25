import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ApiError, type ListQuery, type Page } from '@/shared/api/client';
import { inventoryApi, type InventoryQuery, type LedgerQuery } from './api';
import type { LotStockRow } from './types';

const list = { placeholderData: keepPreviousData };
export const useInventorySummary = () => useQuery({ queryKey: ['inventory-summary'], queryFn: inventoryApi.summary });
export const useInventoryAlerts = () => useQuery({ queryKey: ['inventory-alerts'], queryFn: inventoryApi.alerts });
export const useVariantStock = (q: InventoryQuery, enabled = true) => useQuery({ queryKey: ['inv-variants', q], queryFn: ({ signal }) => inventoryApi.variants(q, signal), enabled, ...list });
export const useLots = (q: InventoryQuery, enabled = true) => useQuery({ queryKey: ['inv-lots', q], queryFn: ({ signal }) => inventoryApi.lots(q, signal), enabled, ...list });
export const useNearExpiry = (q: ListQuery, enabled = true) => useQuery({ queryKey: ['inv-expiry', q], queryFn: ({ signal }) => inventoryApi.nearExpiry(q, signal), enabled, ...list });

function paginateLots(rows: LotStockRow[], q: ListQuery): Page<LotStockRow> {
  const needle = (q.q ?? '').trim().toLowerCase();
  const filtered = needle
    ? rows.filter((r) => [r.lotCode, r.sku, r.productName].some((v) => v.toLowerCase().includes(needle)))
    : rows;
  const page = Math.max(1, Number(q.page) || 1);
  const pageSize = Math.min(100, Math.max(1, Number(q.pageSize) || 20));
  const start = (page - 1) * pageSize;
  return { items: filtered.slice(start, start + pageSize), total: filtered.length, page, pageSize };
}

/** Task 9 — ưu tiên `/inventory/expired`; 404 thì lấy từ lots (API chưa restart). */
export const useExpiredLots = (q: ListQuery, enabled = true) =>
  useQuery({
    queryKey: ['inv-expired', q],
    queryFn: async ({ signal }) => {
      try {
        return await inventoryApi.expired(q, signal);
      } catch (err) {
        if (!(err instanceof ApiError) || err.status !== 404) throw err;
        const page = await inventoryApi.lots({ q: undefined, page: 1, pageSize: 100 }, signal);
        return paginateLots(
          page.items.filter((l) => l.expired > 0),
          q,
        );
      }
    },
    enabled,
    ...list,
  });

export const useExpiryWarnings = (enabled = true) => useQuery({ queryKey: ['inv-expiry-warnings'], queryFn: inventoryApi.expiryWarnings, enabled });
export const useLedger = (q: LedgerQuery, enabled = true) => useQuery({ queryKey: ['inv-ledger', q], queryFn: ({ signal }) => inventoryApi.ledger(q, signal), enabled, ...list });
export const useLocations = () => useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations });
