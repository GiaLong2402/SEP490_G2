import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { Courier, DeliveryInput, DeliveryListItem, DeliveryUpdate } from './types';

export interface DeliveryQuery extends ListQuery {
  state?: string;
  assignedTo?: string;
  date?: string;
}

export const deliveriesApi = {
  list: (query: DeliveryQuery, signal?: AbortSignal) => api.get<Page<DeliveryListItem>>('/deliveries', query, signal),
  create: (body: DeliveryInput) => api.post<DeliveryListItem>('/deliveries', body),
  update: (id: ID, body: DeliveryUpdate) => api.patch<void>(`/deliveries/${id}`, body),
  couriers: () => api.get<Courier[]>('/deliveries/couriers'),
};
