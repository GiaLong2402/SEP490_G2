import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useUnits } from '@/features/products';
import { receiptsApi, type RawMaterialQuery, type ReceiptQuery } from './api';

/** Ký hiệu đo lường giữ nguyên (kg quen hơn "Ki-lô-gam"); đơn vị đếm hiện tên trong danh mục (cai → Cái, cuon → Cuộn). */
const SYMBOL_UNITS = new Set(['kg', 'g', 'l', 'ml']);
export function useUnitLabel() {
  const units = useUnits();
  return (code: string | null | undefined) => {
    if (!code) return '';
    const key = code.trim().toLowerCase();
    if (SYMBOL_UNITS.has(key)) return code;
    return units.data?.find((u) => u.code.toLowerCase() === key)?.name ?? code;
  };
}

export const useReceipts = (query: ReceiptQuery) =>
  useQuery({ queryKey: ['receipts', query], queryFn: ({ signal }) => receiptsApi.list(query, signal), placeholderData: keepPreviousData });
export const useRawMaterials = (query: RawMaterialQuery = {}, enabled = true) =>
  useQuery({
    queryKey: ['raw-materials', query],
    queryFn: ({ signal }) => receiptsApi.rawMaterials(query, signal),
    placeholderData: keepPreviousData,
    enabled,
  });
export const useRawMaterialLots = (id: number | null) =>
  useQuery({
    queryKey: ['raw-material-lots', id],
    queryFn: ({ signal }) => receiptsApi.rawMaterialLots(id as number, false, signal),
    enabled: id !== null,
  });
