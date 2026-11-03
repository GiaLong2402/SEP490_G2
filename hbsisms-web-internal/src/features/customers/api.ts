import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { CustomerAddress, ID } from '@/shared/domain/entities';
import type { AddressInput, CustomerDetail, CustomerInput, CustomerListItem, PriceProposal } from './types';

export interface CustomerQuery extends ListQuery {
  type?: string;
}

export const customersApi = {
  list: (query: CustomerQuery, signal?: AbortSignal) => api.get<Page<CustomerListItem>>('/customers', query, signal),
  get: (id: ID) => api.get<CustomerDetail>(`/customers/${id}`),
  create: (body: CustomerInput) => api.post<CustomerDetail>('/customers', body),
  update: (id: ID, body: CustomerInput) => api.put<CustomerDetail>(`/customers/${id}`, body),
  addresses: (id: ID) => api.get<CustomerAddress[]>(`/customers/${id}/addresses`),
  addAddress: (id: ID, body: AddressInput) => api.post<void>(`/customers/${id}/addresses`, body),
  proposePrice: (id: ID, body: PriceProposal) => api.post<void>(`/customers/${id}/prices`, body),
};
