import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { returnsApi, type ReturnQuery } from './api';

export const useReturns = (query: ReturnQuery) =>
  useQuery({ queryKey: ['returns', query], queryFn: ({ signal }) => returnsApi.list(query, signal), placeholderData: keepPreviousData });
export const useReturnableOrders = (enabled: boolean) => useQuery({ queryKey: ['returnable-orders'], queryFn: () => returnsApi.returnableOrders(), enabled });
