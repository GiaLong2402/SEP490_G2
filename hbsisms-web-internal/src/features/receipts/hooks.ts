import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { receiptsApi, type ReceiptQuery } from './api';

export const useReceipts = (query: ReceiptQuery) =>
  useQuery({ queryKey: ['receipts', query], queryFn: ({ signal }) => receiptsApi.list(query, signal), placeholderData: keepPreviousData });
export const useRawMaterials = () => useQuery({ queryKey: ['raw-materials'], queryFn: receiptsApi.rawMaterials });
