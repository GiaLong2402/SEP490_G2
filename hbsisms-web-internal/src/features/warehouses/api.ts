import { api } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { LocationInput, WarehouseDetail, WarehouseInput } from './types';

export const warehousesApi = {
  list: () => api.get<WarehouseDetail[]>('/warehouses'),
  create: (body: WarehouseInput) => api.post<WarehouseDetail>('/warehouses', body),
  update: (id: ID, body: WarehouseInput) => api.put<WarehouseDetail>(`/warehouses/${id}`, body),
  addLocation: (warehouseId: ID, body: LocationInput) => api.post<void>(`/warehouses/${warehouseId}/locations`, body),
  updateLocation: (id: ID, body: LocationInput) => api.put<void>(`/locations/${id}`, body),
  deleteLocation: (id: ID) => api.delete<void>(`/locations/${id}`),
};
