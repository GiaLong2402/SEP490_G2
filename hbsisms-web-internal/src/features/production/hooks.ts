import { keepPreviousData, useQueries, useQuery } from '@tanstack/react-query';
import { productsApi } from '@/features/products/api';
import { ApiError } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import { productionApi, type ProductionOrderQuery } from './api';

export const useProductionOrders = (query: ProductionOrderQuery) =>
  useQuery({ queryKey: ['production-orders', query], queryFn: ({ signal }) => productionApi.list(query, signal), placeholderData: keepPreviousData });

export const useProductionOrder = (id: ID) =>
  useQuery({ queryKey: ['production-order', id], queryFn: ({ signal }) => productionApi.get(id, signal), enabled: Number.isFinite(id) && id > 0 });

export const useMaterialIssue = (id: ID | null) =>
  useQuery({ queryKey: ['material-issue', id], queryFn: ({ signal }) => productionApi.materialIssue(id as ID, signal), enabled: id !== null });

export const useProducibleProducts = () =>
  useQuery({
    queryKey: ['production-products'],
    queryFn: ({ signal }) => productionApi.products(signal),
    // Không lọc theo cờ isProducible: DB đang để mặc định false cho mọi sản phẩm (kể cả Mực Lá đã có BOM).
    // Backend tự chặn khi sản phẩm chưa có định mức; màn chọn hiện "chưa có định mức" ngay khi chọn.
    // Sản phẩm đánh dấu "có thể sản xuất" xếp lên đầu.
    select: (page) =>
      page.items
        .filter((p) => p.isActive && !p.isRawMaterial)
        .sort((a, b) => Number(b.isProducible) - Number(a.isProducible) || a.name.localeCompare(b.name, 'vi')),
  });

export interface BomProduct {
  id: ID;
  code: string;
  name: string;
  isProducible: boolean;
  /** Số dòng nguyên liệu trong định mức; null = không xem được (thiếu quyền `products`). */
  bomLines: number | null;
}

/**
 * Thành phẩm chọn được khi lập lệnh: chỉ sản phẩm đã có định mức (BOM).
 * API danh sách sản phẩm chưa cho biết có BOM hay không, nên hỏi BOM từng sản phẩm (có cache).
 * GET /products/{id}/bom cần quyền `products`: tài khoản chỉ có `inventory` (thủ kho) bị 403 → `forbidden`,
 * khi đó hiện tất cả sản phẩm và để backend chặn lúc lưu. Nên có endpoint riêng phía sản xuất (quyền inventory).
 */
export function useBomProducts() {
  const products = useProducibleProducts();
  const items = products.data ?? [];
  const boms = useQueries({
    queries: items.map((p) => ({
      queryKey: ['product-bom', Number(p.id)],
      queryFn: ({ signal }: { signal: AbortSignal }) => productsApi.getBom(p.id, signal),
      retry: false,
      staleTime: 60_000,
    })),
  });
  const forbidden = boms.some((q) => q.error instanceof ApiError && q.error.status === 403);
  const loading = products.isLoading || boms.some((q) => q.isLoading);
  const list: BomProduct[] = items.map((p, i) => {
    const lines = boms[i]?.data?.lines.filter((l) => l.rawMaterialId !== null).length;
    return { id: p.id, code: p.code, name: p.name, isProducible: p.isProducible, bomLines: lines ?? null };
  });
  return {
    loading,
    forbidden,
    error: products.error,
    items: forbidden ? list : list.filter((p) => (p.bomLines ?? 0) > 0),
  };
}

export const useProductVariants = (productId: ID | null) =>
  useQuery({
    queryKey: ['production-product', productId],
    queryFn: ({ signal }) => productionApi.product(productId as ID, signal),
    enabled: productId !== null,
    select: (p) => p.variants.filter((v) => v.isActive),
  });
