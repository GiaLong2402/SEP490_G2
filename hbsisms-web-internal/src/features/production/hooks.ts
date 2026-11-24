import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ID } from '@/shared/domain/entities';
import { productionApi, type ProductionOrderQuery } from './api';
import type { ProductionBomProduct } from './types';

export const useProductionOrders = (query: ProductionOrderQuery) =>
  useQuery({ queryKey: ['production-orders', query], queryFn: ({ signal }) => productionApi.list(query, signal), placeholderData: keepPreviousData });

export const useProductionOrder = (id: ID) =>
  useQuery({ queryKey: ['production-order', id], queryFn: ({ signal }) => productionApi.get(id, signal), enabled: Number.isFinite(id) && id > 0 });

export const useMaterialIssue = (id: ID | null) =>
  useQuery({ queryKey: ['material-issue', id], queryFn: ({ signal }) => productionApi.materialIssue(id as ID, signal), enabled: id !== null });

export interface BomProduct {
  id: ID;
  code: string;
  name: string;
  isProducible: boolean;
  /** Số dòng nguyên liệu trong định mức. */
  bomLines: number | null;
}

/**
 * Thành phẩm chọn được khi lập lệnh: chỉ SP đã có BOM.
 * Dùng GET /production-orders/bom-products (quyền inventory) — không cần quyền products.
 */
export function useBomProducts() {
  const query = useQuery({
    queryKey: ['production-bom-products'],
    queryFn: ({ signal }) => productionApi.bomProducts(signal),
    staleTime: 60_000,
  });
  const items: BomProduct[] = (query.data ?? []).map((p: ProductionBomProduct) => ({
    id: p.id,
    code: p.code,
    name: p.name,
    isProducible: p.isProducible,
    bomLines: p.bomLineCount,
  }));
  return {
    loading: query.isLoading,
    forbidden: false,
    error: query.error,
    items,
  };
}

export const useMaterialPreview = (productId: ID | null, plannedQty: number) =>
  useQuery({
    queryKey: ['production-material-preview', productId, plannedQty],
    queryFn: ({ signal }) => productionApi.materialPreview(productId as ID, plannedQty, signal),
    enabled: productId !== null && Number.isFinite(plannedQty) && plannedQty > 0,
  });

export const useProductVariants = (productId: ID | null) =>
  useQuery({
    queryKey: ['production-product', productId],
    queryFn: ({ signal }) => productionApi.product(productId as ID, signal),
    enabled: productId !== null,
    select: (p) => p.variants.filter((v) => v.isActive),
  });
