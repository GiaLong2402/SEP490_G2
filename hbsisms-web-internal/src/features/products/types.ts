import type { Category, ID, Product, ProductVariant, UnitOfMeasure, VolumeTier } from '@/shared/domain/entities';

export interface VariantWithTiers extends ProductVariant {
  tiers: VolumeTier[];
  available: number;
}

/** Lô tồn trên chi tiết sản phẩm (gom theo lotId, không theo vị trí). */
export interface ProductLotRow {
  lotId: ID;
  lotCode: string;
  variantId: ID;
  sku: string;
  mfgDate: string | null;
  expDate: string | null;
  daysLeft: number | null;
  available: number;
}

export interface ProductListItem extends Product {
  categoryName: string | null;
  unitCode: string | null;
  unitName: string | null;
  variants: VariantWithTiers[];
  /** Tổng qtyAvailable mọi variant — list/detail. */
  totalStockQty: number;
  /** Chỉ đầy đủ ở GET /products/:id (staff); list thường []). */
  lots: ProductLotRow[];
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
  /** Giữ field API/DB; tạm thời SP luôn false/true/false — NVL quản lý riêng. */
  isRawMaterial: boolean;
  isSellable: boolean;
  isProducible: boolean;
  /** Giữ field API/DB; SP bán theo quy cách — tạm không gắn ĐVT. */
  unitId: ID | null;
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
  /** Số khay khả dụng — dropdown đơn/POS. */
  available?: number;
}

export interface CategoryListItem extends Category {
  parentName: string | null;
  productCount: number;
}

export interface CategoryInput {
  code: string;
  name: string;
  description: string | null;
  /** Ảnh danh mục — Home storefront dùng cho loại cha. */
  imageUrl: string | null;
  parentId: ID | null;
  sortOrder: number;
  isActive: boolean;
}

export type { UnitOfMeasure };

export interface UnitInput {
  code: string;
  name: string;
  isActive: boolean;
}

/** Một dòng định mức NVL / SP cấu thành cho 1 đơn vị thành phẩm. */
export interface BomLine {
  id: ID;
  rawMaterialId: ID | null;
  rawMaterialCode: string | null;
  rawMaterialName: string | null;
  componentProductId: ID | null;
  componentProductCode: string | null;
  componentProductName: string | null;
  qty: number;
  unitId: ID;
  unitCode: string;
  unitName: string;
  wastePercent: number | null;
  sortOrder: number;
}

export interface BomDetail {
  productId: ID;
  productCode: string;
  productName: string;
  lines: BomLine[];
}

export interface BomLineInput {
  rawMaterialId: ID | null;
  componentProductId: ID | null;
  qty: number;
  unitId: ID;
  wastePercent: number | null;
}

export interface BomReplaceInput {
  lines: BomLineInput[];
}

/** Task 59 — nguồn giá vốn gợi ý giá bán. */
export type SellPriceCostSource = 'WAC' | 'PRODUCTION';

export interface SellPriceSuggestion {
  variantId: ID;
  sku: string;
  packLabel: string;
  currentListPrice: number;
  costSource: SellPriceCostSource;
  unitCost: number;
  costSourceLabel: string;
  productionOrderId: ID | null;
  productionOrderCode: string | null;
  markupPercent: number;
  markupSource: string;
  suggestedListPrice: number;
}

export interface ApplySellPriceInput {
  costSource: SellPriceCostSource;
}

/** Task 59 — một giai đoạn giá bán (sau mỗi lần Áp dụng). */
export interface SellPriceHistoryRow {
  id: ID;
  variantId: ID;
  sku: string;
  packLabel: string;
  listPrice: number;
  previousListPrice: number | null;
  unitCost: number | null;
  costSource: SellPriceCostSource;
  markupPercent: number;
  markupSource: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  current: boolean;
  createdByUserId: ID | null;
  createdByName: string | null;
}

