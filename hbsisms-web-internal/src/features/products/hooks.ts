import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ListQuery } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import { productsApi } from './api';

export const useProducts = (query: ListQuery & { categoryId?: string }) =>
  useQuery({ queryKey: ['products', query], queryFn: ({ signal }) => productsApi.list(query, signal), placeholderData: keepPreviousData });
export const useProduct = (id: ID) => useQuery({ queryKey: ['product', id], queryFn: () => productsApi.get(id) });
export const useVariantOptions = () => useQuery({ queryKey: ['variant-options'], queryFn: productsApi.variantOptions });
export const useUnits = (query: { activeOnly?: boolean } = {}) =>
  useQuery({ queryKey: ['units', query], queryFn: ({ signal }) => productsApi.units(query, signal) });
export const useCategories = () => useQuery({ queryKey: ['categories'], queryFn: productsApi.categories });
export const useProductBom = (productId: ID, enabled = true) =>
  useQuery({
    queryKey: ['product-bom', productId],
    queryFn: ({ signal }) => productsApi.getBom(productId, signal),
    enabled: enabled && Number.isFinite(productId) && productId > 0,
  });
export const useBomRawMaterials = (enabled = true) =>
  useQuery({
    queryKey: ['bom-raw-materials'],
    queryFn: ({ signal }) => productsApi.bomRawMaterials(signal),
    enabled,
  });
