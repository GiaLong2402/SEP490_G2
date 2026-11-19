import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { returnsApi, type ReturnQuery } from './api';

export const useReturns = (query: ReturnQuery) =>
  useQuery({ queryKey: ['returns', query], queryFn: ({ signal }) => returnsApi.list(query, signal), placeholderData: keepPreviousData });
export const useReturnableOrders = (enabled: boolean, q?: string) =>
  useQuery({
    queryKey: ['returnable-orders', q ?? ''],
    queryFn: () => returnsApi.returnableOrders(q || undefined),
    enabled,
  });
