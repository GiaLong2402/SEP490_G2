import { useQuery } from '@tanstack/react-query';
import { pickingApi } from './api';

export const useOrderPickingSlip = (orderId: number | null) =>
  useQuery({
    queryKey: ['picking', 'order', orderId],
    queryFn: () => pickingApi.orderSlip(orderId!),
    enabled: orderId != null && orderId > 0,
  });

export const useDailyPickingSlip = (date: string | null) =>
  useQuery({
    queryKey: ['picking', 'daily', date],
    queryFn: () => pickingApi.dailySlip(date!),
    enabled: !!date,
  });
