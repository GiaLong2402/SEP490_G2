import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ListQuery } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import { productsApi } from './api';

export const useProducts = (query: ListQuery & { categoryId?: string }) =>
  useQuery({ queryKey: ['products', query], queryFn: ({ signal }) => productsApi.list(query, signal), placeholderData: keepPreviousData });
export const useProduct = (id: ID) => useQuery({ queryKey: ['product', id], queryFn: () => productsApi.get(id) });
export const useVariantOptions = () => useQuery({ queryKey: ['variant-options'], queryFn: productsApi.variantOptions });
export const useCategories = () => useQuery({ queryKey: ['categories'], queryFn: productsApi.categories });
