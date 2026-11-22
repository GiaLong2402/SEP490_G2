import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { receiptsApi, type RawMaterialQuery, type ReceiptQuery } from './api';

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
