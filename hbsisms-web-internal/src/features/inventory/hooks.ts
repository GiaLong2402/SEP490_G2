import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ListQuery } from '@/shared/api/client';
import { inventoryApi, type InventoryQuery, type LedgerQuery } from './api';

const list = { placeholderData: keepPreviousData };
export const useInventorySummary = () => useQuery({ queryKey: ['inventory-summary'], queryFn: inventoryApi.summary });
export const useInventoryAlerts = () => useQuery({ queryKey: ['inventory-alerts'], queryFn: inventoryApi.alerts });
export const useVariantStock = (q: InventoryQuery, enabled = true) => useQuery({ queryKey: ['inv-variants', q], queryFn: ({ signal }) => inventoryApi.variants(q, signal), enabled, ...list });
export const useLots = (q: InventoryQuery, enabled = true) => useQuery({ queryKey: ['inv-lots', q], queryFn: ({ signal }) => inventoryApi.lots(q, signal), enabled, ...list });
export const useNearExpiry = (q: ListQuery, enabled = true) => useQuery({ queryKey: ['inv-expiry', q], queryFn: ({ signal }) => inventoryApi.nearExpiry(q, signal), enabled, ...list });
export const useLedger = (q: LedgerQuery, enabled = true) => useQuery({ queryKey: ['inv-ledger', q], queryFn: ({ signal }) => inventoryApi.ledger(q, signal), enabled, ...list });
export const useLocations = () => useQuery({ queryKey: ['locations'], queryFn: inventoryApi.locations });
