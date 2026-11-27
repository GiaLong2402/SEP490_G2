import type { ID, ISODate, ISODateTime } from '@/shared/domain/entities';
import type { ProductionOrderStatus } from '@/shared/domain/enums';

/** Một dòng nguyên liệu của lệnh — snapshot định mức (BOM) lúc lập / sửa lệnh (Duy, dòng 48). */
export interface ProductionOrderMaterialLine {
  id: ID;
  rawMaterialId: ID;
  rawMaterialCode: string;
  rawMaterialName: string;
  unit: string;
  /** Định mức cho 1 khay của lệnh (dòng cân ký đã nhân theo cân nặng khay: khay 0,5 kg = nửa định mức 1 kg). */
  bomQtyPerUnit: number;
  wastePercent: number;
  /** = định mức / khay × số khay ÷ (1 − hao hụt%). */
  requiredQty: number;
  onHandQty: number;
  sufficient: boolean;
  stockStatus: string;
  /** Tạm tính theo giá lô FEFO. */
  lineEstimatedCost: number | null;
}

export interface ProductionOrder {
  id: ID;
  code: string;
  status: ProductionOrderStatus;
  productId: ID;
  productCode: string;
  productName: string;
  plannedQty: number;
  plannedDueDate: ISODate | null;
  note: string | null;
  estimatedProductionCost: number | null;
  estimatedMaterialCost: number | null;
  /** Số khay đạt khi báo làm xong. */
  actualQty: number | null;
  actualProductionCost: number | null;
  actualMaterialCost: number | null;
  /** Loại khay chọn lúc lập lệnh (báo làm xong dùng luôn loại này). */
  finishedVariantId: ID | null;
  finishedVariantSku: string | null;
  /** Cân nặng 1 khay thành phẩm (kg), vd. 0.5 — null nếu lệnh chưa chọn khay / API cũ. */
  finishedTrayKg?: number | null;
  finishedReceiptId: ID | null;
  finishedReceiptCode: string | null;
  allMaterialsSufficient: boolean;
  materialLineCount: number;
  materialIssueId: ID | null;
  materialIssueCode: string | null;
  creatorName: string | null;
  createdAt: ISODateTime;
  approvedAt: ISODateTime | null;
  approverName: string | null;
  materials: ProductionOrderMaterialLine[];
  // --- Đã chốt 24/11/2026, backend chưa trả (dòng 51–52). Trống thì giao diện tự ẩn. ---
  /** Số khay hỏng lúc làm (không vào kho, không qua Báo hỏng). */
  defectQty?: number | null;
  defectReason?: string | null;
  /** Số khay thật nhập kho (bước 4, có thể khác actualQty). */
  receivedQty?: number | null;
  /** Nguyên liệu dư đã trả lại kho: đồ khu lạnh → lô -TL mới, đồ khu khô → cộng lại lô gốc. */
  leftovers?: ProductionLeftover[] | null;
  completedAt?: ISODateTime | null;
  finishedDate?: ISODate | null;
  /** Giá vốn / khay sau nhập TP (null nếu chưa hoàn thành). */
  unitCost?: number | null;
  /** % markup từ app_config `sell_price_markup_percent`. */
  markupPercent?: number | null;
  /** Giá bán 1 kg = (unitCost ÷ cân nặng khay) × (1 + markup/100), tròn 1.000 ₫ — khay 0,5 kg = nửa. */
  suggestedSellPrice?: number | null;
  /** Giá niêm yết hiện tại của quy cách thành phẩm. */
  currentListPrice?: number | null;
  /** Giá đang bán / giá gợi ý của từng khay (lệnh đã hoàn thành) — hệ thống không tự đổi giá đang bán. */
  trayPrices?: ProductionTrayPrice[] | null;
  /** Lô / HSD / vị trí thành phẩm đã nhập kho (null nếu chưa nhập). */
  finishedPutAway?: FinishedPutAway | null;
}

export interface FinishedPutAway {
  lotCode: string | null;
  expDate: ISODate | null;
  locationCode: string | null;
  locationZone: string | null;
  warehouseName: string | null;
}

export interface ProductionLeftover {
  rawMaterialId: ID;
  rawMaterialName: string;
  unit: string;
  qty: number;
  lotCode: string;
  /** HSD lô nhận phần dư (lô gốc đồ khô có thể không có HSD). */
  expDate: ISODate | null;
  amount: number;
}

export type MaterialIssueStatus = 'pending' | 'completed' | 'cancelled';

export interface MaterialIssueLine {
  id: ID;
  rawMaterialId: ID;
  rawMaterialCode: string;
  rawMaterialName: string;
  unit: string;
  qtyRequired: number;
  qtyIssued: number | null;
  /** Trống khi phiếu còn chờ xuất; sau khi xuất mỗi lô một dòng. */
  rawMaterialLotId: ID | null;
  lotCode: string | null;
}

export interface MaterialIssue {
  id: ID;
  code: string;
  status: MaterialIssueStatus;
  productionOrderId: ID;
  productionOrderCode: string;
  productId: ID;
  productCode: string;
  productName: string;
  creatorName: string | null;
  createdAt: ISODateTime;
  completedAt: ISODateTime | null;
  completerName: string | null;
  note: string | null;
  lines: MaterialIssueLine[];
}

export interface ProductionOrderInput {
  productId: ID;
  plannedQty: number;
  plannedDueDate: ISODate | null;
  note: string | null;
  estimatedProductionCost: number | null;
  /** Loại khay làm ra (0,5 kg / 1 kg). Trống → khay 1 kg. */
  variantId: ID | null;
}

export type ProductionOrderUpdateInput = Omit<ProductionOrderInput, 'productId'>;

export interface LeftoverInput {
  rawMaterialId: ID;
  qty: number;
  /** Đồ khu lạnh (lô -TL): mặc định hôm nay + 1 ngày, không muộn hơn hạn lô gốc. Đồ khu khô trả về lô gốc → null. */
  expDate: ISODate | null;
}

/** POST /production-orders/{id}/complete. `defectQty`, `defectReason`, `leftovers` đã chốt nhưng backend chưa nhận. */
export interface ProductionCompleteInput {
  actualQty: number;
  actualProductionCost: number | null;
  variantId: ID | null;
  /** Ngày làm xong → ngày sản xuất của lô thành phẩm (đã chốt, backend chưa nhận). */
  finishedDate: ISODate | null;
  defectQty: number | null;
  defectReason: string | null;
  leftovers: LeftoverInput[];
}

/** GET /production-orders/bom-products — quyền inventory. */
export interface ProductionBomProduct {
  id: ID;
  code: string;
  name: string;
  isProducible: boolean;
  bomLineCount: number;
  /** Loại khay đang bán — khay 1 kg đứng đầu. */
  variants: ProductionTrayOption[];
}

export interface ProductionTrayPrice {
  variantId: ID;
  sku: string;
  packLabel: string | null;
  unitWeightG: number | null;
  active: boolean;
  /** Khay vừa làm trong lệnh này. */
  produced: boolean;
  listPrice: number | null;
  suggestedPrice: number | null;
}

export interface ProductionTrayOption {
  id: ID;
  sku: string;
  packLabel: string | null;
  unitWeightG: number | null;
}

/** GET /production-orders/material-preview — tồn dùng được (bỏ lô hết hạn). */
export interface ProductionMaterialNeed {
  rawMaterialId: ID;
  rawMaterialCode: string;
  rawMaterialName: string;
  unit: string;
  bomQtyPerUnit: number;
  wastePercent: number | null;
  requiredQty: number;
  onHandQty: number;
  expiredQty: number;
  sufficient: boolean;
  stockStatus: string;
  lineEstimatedCost: number | null;
}

export interface ProductionMaterialPreview {
  productId: ID;
  productCode: string;
  productName: string;
  plannedQty: number;
  estimatedMaterialCost: number;
  allMaterialsSufficient: boolean;
  materials: ProductionMaterialNeed[];
}
