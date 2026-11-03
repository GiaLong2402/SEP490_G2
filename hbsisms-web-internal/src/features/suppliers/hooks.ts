import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { suppliersApi, type SupplierQuery } from './api';

export const useSuppliers = (query: SupplierQuery) =>
  useQuery({ queryKey: ['suppliers', query], queryFn: ({ signal }) => suppliersApi.list(query, signal), placeholderData: keepPreviousData });
/** Danh sách đầy đủ cho ô chọn và bộ lọc, gồm cả nhà cung cấp đã ngừng giao dịch. */
export const useSupplierOptions = () => useQuery({ queryKey: ['supplier-options'], queryFn: () => suppliersApi.list({ pageSize: 500 }), select: (page) => page.items });
