import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { ordersApi } from './api';
import type { OrderQuery } from './types';

/** Không keepPreviousData: đổi filter không được hiện list cũ như kết quả lọc. */
export const useOrders = (query: OrderQuery) =>
  useQuery({ queryKey: ['orders', query], queryFn: ({ signal }) => ordersApi.list(query, signal) });

/** Đếm tab quản lý (Inter 3 dòng 5). Giữ số cũ lúc refetch để badge không nháy. */
export const useOrderCounts = (query: OrderQuery) =>
  useQuery({
    queryKey: ['orders', 'counts', query],
    queryFn: ({ signal }) => ordersApi.counts(query, signal),
    placeholderData: keepPreviousData,
  });

export const useOrder = (id: ID, enabled = true) =>
  useQuery({ queryKey: ['order', id], queryFn: () => ordersApi.get(id), enabled: enabled && Number(id) > 0 });
