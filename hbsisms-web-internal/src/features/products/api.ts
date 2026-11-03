import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { CategoryInput, CategoryListItem, ProductDetail, ProductInput, ProductListItem, VariantInput, VariantOption } from './types';

export const productsApi = {
  list: (query: ListQuery & { categoryId?: string }, signal?: AbortSignal) => api.get<Page<ProductListItem>>('/products', query, signal),
  get: (id: ID) => api.get<ProductDetail>(`/products/${id}`),
  create: (body: ProductInput) => api.post<ProductDetail>('/products', body),
  update: (id: ID, body: ProductInput) => api.put<ProductDetail>(`/products/${id}`, body),
  addVariant: (productId: ID, body: VariantInput) => api.post<void>(`/products/${productId}/variants`, body),
  updateVariant: (variantId: ID, body: VariantInput) => api.put<void>(`/variants/${variantId}`, body),
  variantOptions: () => api.get<VariantOption[]>('/variants'),
  categories: () => api.get<CategoryListItem[]>('/categories'),
  createCategory: (body: CategoryInput) => api.post<CategoryListItem>('/categories', body),
  updateCategory: (id: ID, body: CategoryInput) => api.put<CategoryListItem>(`/categories/${id}`, body),
};
