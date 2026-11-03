import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { customersApi, type CustomerQuery } from './api';

export const useCustomers = (query: CustomerQuery) =>
  useQuery({ queryKey: ['customers', query], queryFn: ({ signal }) => customersApi.list(query, signal), placeholderData: keepPreviousData });
export const useCustomer = (id: ID | null) => useQuery({ queryKey: ['customer', id], queryFn: () => customersApi.get(id!), enabled: !!id });
/** Danh sách đầy đủ cho ô chọn khách. */
export const useCustomerOptions = () => useQuery({ queryKey: ['customer-options'], queryFn: () => customersApi.list({ pageSize: 500 }), select: (page) => page.items.filter((c) => c.isActive) });
