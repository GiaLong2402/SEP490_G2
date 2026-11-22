import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type {
  BomDetail,
  BomReplaceInput,
  CategoryInput,
  CategoryListItem,
  ProductDetail,
  ProductInput,
  ProductListItem,
  UnitInput,
  UnitOfMeasure,
  VariantInput,
  VariantOption,
} from './types';

/** NVL rút gọn cho dropdown BOM — gọi thẳng /raw-materials (tránh import vòng receipts). */
export interface BomRawMaterialOption {
  id: ID;
  code: string;
  name: string;
  unit: string;
  unitId: ID | null;
  isActive: boolean;
}

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
  units: (query: { activeOnly?: boolean } = {}, signal?: AbortSignal) => api.get<UnitOfMeasure[]>('/units', query, signal),
  createUnit: (body: UnitInput) => api.post<UnitOfMeasure>('/units', body),
  updateUnit: (id: ID, body: UnitInput) => api.put<UnitOfMeasure>(`/units/${id}`, body),
  getBom: (productId: ID, signal?: AbortSignal) => api.get<BomDetail>(`/products/${productId}/bom`, undefined, signal),
  replaceBom: (productId: ID, body: BomReplaceInput) => api.put<BomDetail>(`/products/${productId}/bom`, body),
  bomRawMaterials: (signal?: AbortSignal) =>
    api.get<BomRawMaterialOption[]>('/raw-materials', { activeOnly: true }, signal),
};
