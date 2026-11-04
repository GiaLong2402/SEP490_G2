import type { Category, ID, Product, ProductVariant, VolumeTier } from '@/shared/domain/entities';

export interface VariantWithTiers extends ProductVariant {
  tiers: VolumeTier[];
  available: number;
}

export interface ProductListItem extends Product {
  categoryName: string | null;
  variants: VariantWithTiers[];
}

export type ProductDetail = ProductListItem;

export interface ProductInput {
  code: string;
  name: string;
  categoryId: ID | null;
  description: string | null;
  storageNote: string | null;
  origin: string | null;
  imageUrl: string | null;
  /** Mỗi phần tử là một URL; gửi [] để xóa hết gallery. */
  gallery: string[];
  isPublished: boolean;
  isActive: boolean;
}

export interface VariantInput {
  sku: string;
  packLabel: string;
  unitWeightG: number | null;
  listPrice: number;
  packPerCase: number;
  barcode: string | null;
  isActive: boolean;
  tiers: { minQtyKhay: number; unitPrice: number }[];
}

/** Dòng rút gọn cho các ô chọn sản phẩm ở đơn, báo giá, nhập kho. */
export interface VariantOption {
  id: ID;
  sku: string;
  packLabel: string;
  productName: string;
  listPrice: number;
  packPerCase: number;
}

export interface CategoryListItem extends Category {
  parentName: string | null;
  productCount: number;
}

export interface CategoryInput {
  code: string;
  name: string;
  description: string | null;
  parentId: ID | null;
  sortOrder: number;
  isActive: boolean;
}
