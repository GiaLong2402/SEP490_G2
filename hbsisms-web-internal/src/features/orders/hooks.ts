import { useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { ordersApi } from './api';
import type { OrderQuery } from './types';

/** Không keepPreviousData: đổi filter không được hiện list cũ như kết quả lọc. */
export const useOrders = (query: OrderQuery) =>
  useQuery({ queryKey: ['orders', query], queryFn: ({ signal }) => ordersApi.list(query, signal) });
export const useOrder = (id: ID, enabled = true) =>
  useQuery({ queryKey: ['order', id], queryFn: () => ordersApi.get(id), enabled: enabled && Number(id) > 0 });
