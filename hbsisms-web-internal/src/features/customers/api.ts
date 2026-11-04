import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { CustomerAddress, ID } from '@/shared/domain/entities';
import type { AddressInput, CustomerDetail, CustomerInput, CustomerListItem, PriceProposal } from './types';

export interface CustomerQuery extends ListQuery {
  type?: string;
  isActive?: boolean;
}

export const customersApi = {
  list: (query: CustomerQuery, signal?: AbortSignal) => api.get<Page<CustomerListItem>>('/customers', query, signal),
  get: (id: ID) => api.get<CustomerDetail>(`/customers/${id}`),
  create: (body: CustomerInput) => api.post<CustomerDetail>('/customers', body),
  update: (id: ID, body: CustomerInput) => api.put<CustomerDetail>(`/customers/${id}`, body),
  setActive: (id: ID, isActive: boolean) => api.patch<CustomerDetail>(`/customers/${id}/active`, { isActive }),
  addresses: (id: ID) => api.get<CustomerAddress[]>(`/customers/${id}/addresses`),
  addAddress: (id: ID, body: AddressInput) => api.post<void>(`/customers/${id}/addresses`, body),
  updateAddress: (id: ID, addressId: ID, body: AddressInput) => api.put<void>(`/customers/${id}/addresses/${addressId}`, body),
  deleteAddress: (id: ID, addressId: ID) => api.delete<void>(`/customers/${id}/addresses/${addressId}`),
  proposePrice: (id: ID, body: PriceProposal) => api.post<void>(`/customers/${id}/prices`, body),
};
