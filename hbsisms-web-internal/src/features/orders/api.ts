import { api, type Page } from '@/shared/api/client';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { ID } from '@/shared/domain/entities';
import type { OrderState } from '@/shared/domain/enums';
import type { Receipt } from '@/features/pos/types';
import type { OrderDetail, OrderInput, OrderListItem, OrderQuery } from './types';

export const ordersApi = {
  list: (query: OrderQuery, signal?: AbortSignal) =>
    api.get<Page<OrderListItem>>(API_PATHS.orders.root, query, signal),
  get: (id: ID) => api.get<OrderDetail>(API_PATHS.orders.byId(id)),
  create: (body: OrderInput) => api.post<OrderDetail>(API_PATHS.orders.root, body),
  update: (id: ID, body: OrderInput) => api.put<OrderDetail>(API_PATHS.orders.byId(id), body),
  transition: (id: ID, state: OrderState) =>
    api.post<void>(API_PATHS.orders.transition(id), { state }),
  cancel: (id: ID, reason: string) => api.post<void>(API_PATHS.orders.cancel(id), { reason }),
  receipt: (id: ID) => api.get<Receipt>(API_PATHS.orders.receipt(id)),
  /** Hoàn tất bàn giao tại quầy — OUT đúng lô HOLD (idempotent). */
  completeAtCounter: (id: ID) => api.post<OrderDetail>(API_PATHS.orders.completeAtCounter(id)),
};
