import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { ordersApi } from './api';
import type { OrderQuery } from './types';

export const useOrders = (query: OrderQuery) =>
  useQuery({ queryKey: ['orders', query], queryFn: ({ signal }) => ordersApi.list(query, signal), placeholderData: keepPreviousData });
export const useOrder = (id: ID) => useQuery({ queryKey: ['order', id], queryFn: () => ordersApi.get(id) });
