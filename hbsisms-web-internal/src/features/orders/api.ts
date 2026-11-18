import { ApiError, api, tokenStore, type Page } from '@/shared/api/client';
import { API_BASE_URL } from '@/shared/api/config';
import { API_PATHS } from '@/shared/api/httpPaths';
import type { ID } from '@/shared/domain/entities';
import type { OrderState } from '@/shared/domain/enums';
import type { Receipt } from '@/features/pos/types';
import type {
  OrderCounts,
  OrderDetail,
  OrderInput,
  OrderListItem,
  OrderQuery,
  OwnerInbox,
} from './types';

/** Tải xlsx (binary) — không đi qua transport JSON/mock. */
async function downloadOrdersExport(query: OrderQuery = {}): Promise<void> {
  const url = new URL(API_BASE_URL.replace(/\/$/, '') + API_PATHS.orders.export, window.location.origin);
  Object.entries(query).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
  });
  const token = tokenStore.get();
  const res = await fetch(url, {
    method: 'GET',
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  if (!res.ok) {
    const payload = await res.json().catch(() => null);
    throw new ApiError(res.status, payload?.message ?? `Lỗi máy chủ (${res.status})`);
  }
  const blob = await res.blob();
  const disposition = res.headers.get('Content-Disposition') ?? '';
  const match = /filename="?([^";]+)"?/i.exec(disposition);
  const filename = match?.[1] ?? 'don-hang.xlsx';
  const objectUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = objectUrl;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(objectUrl);
}

export const ordersApi = {
  list: (query: OrderQuery, signal?: AbortSignal) =>
    api.get<Page<OrderListItem>>(API_PATHS.orders.root, query, signal),
  counts: (query: OrderQuery = {}, signal?: AbortSignal) =>
    api.get<OrderCounts>(API_PATHS.orders.counts, query, signal),
  /** Inter 3 dòng 11 — việc cần Chủ (approve); panel UI = dòng 13. */
  ownerInbox: (signal?: AbortSignal) =>
    api.get<OwnerInbox>(API_PATHS.orders.ownerInbox, undefined, signal),
  /** Inter 3 dòng 12 — xuất theo filter; nút UI gắn ở dòng 13. */
  export: (query: OrderQuery = {}) => downloadOrdersExport(query),
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
