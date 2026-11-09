import { api, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { OrderState } from '@/shared/domain/enums';
import type { OrderDetail, OrderInput, OrderListItem, OrderQuery } from './types';

export const ordersApi = {
  list: (query: OrderQuery, signal?: AbortSignal) => api.get<Page<OrderListItem>>('/orders', query, signal),
  get: (id: ID) => api.get<OrderDetail>(`/orders/${id}`),
  create: (body: OrderInput) => api.post<OrderDetail>('/orders', body),
  transition: (id: ID, state: OrderState) => api.post<void>(`/orders/${id}/transition`, { state }),
  cancel: (id: ID, reason: string) => api.post<void>(`/orders/${id}/cancel`, { reason }),
  /** Hoàn tất bàn giao tại quầy — OUT đúng lô HOLD (idempotent). */
  completeAtCounter: (id: ID) => api.post<OrderDetail>(`/orders/${id}/complete-at-counter`),
};
