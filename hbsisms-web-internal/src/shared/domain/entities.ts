// Ánh xạ 1-1 bảng trong database/schema.sql sang JSON camelCase (mặc định của Jackson/Spring Boot).
// numeric(14,2) -> number; date -> 'YYYY-MM-DD'; timestamptz -> ISO 8601.
import type * as E from './enums';

export type ID = number;
export type ISODate = string;
export type ISODateTime = string;

export interface AppRole { id: ID; code: string; name: string; permissions: E.Permission[] }

export interface AppUser {
  id: ID; username: string | null; email: string | null; phone: string | null; fullName: string; avatarUrl: string | null;
  userType: E.UserType; roleId: ID | null; employeeCode: string | null; position: string | null; hiredAt: ISODate | null;
  isActive: boolean; lastLoginAt: ISODateTime | null; createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface Customer {
  id: ID; code: string; customerType: E.CustomerType; name: string; email: string | null; phone: string | null;
  taxCode: string | null; contactName: string | null; contactPhone: string | null; accountUserId: ID | null;
  creditLimit: number; netDays: number; currentDebt: number; isActive: boolean; note: string | null;
  createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface CustomerAddress {
  id: ID; customerId: ID; kind: E.AddressKind; recipientName: string | null; phone: string | null; line1: string;
  ward: string | null; district: string | null; province: string | null; latitude: number | null; longitude: number | null;
  inDeliveryRadius: boolean; distanceKm: number | null; isDefault: boolean; createdAt: ISODateTime;
}

export interface Category {
  id: ID; code: string; name: string; description: string | null; imageUrl: string | null; parentId: ID | null; sortOrder: number; isActive: boolean;
}

export interface Product {
  id: ID; code: string; name: string; slug: string | null; categoryId: ID | null; description: string | null; storageNote: string | null;
  origin: string | null; imageUrl: string | null; gallery: string[] | null; isPublished: boolean; isActive: boolean;
  createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface ProductVariant {
  id: ID; productId: ID; sku: string; packLabel: string; unitWeightG: number | null; listPrice: number; packPerCase: number;
  barcode: string | null; isActive: boolean; createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface VolumeTier { id: ID; variantId: ID; minQtyKhay: number; unitPrice: number }

export interface CustomerPrice {
  id: ID; customerId: ID; variantId: ID; unitPrice: number; status: E.ApprovalStatus; effectiveFrom: ISODate | null; effectiveTo: ISODate | null;
  approvedBy: ID | null; approvedAt: ISODateTime | null; updatedAt: ISODateTime;
}

export interface Warehouse { id: ID; code: string; name: string; address: string | null; capacity: number; isActive: boolean }
export interface Location { id: ID; warehouseId: ID; code: string; zone: string | null; capacity: number; note: string | null }
export interface RawMaterial { id: ID; code: string; name: string; unit: string; qtyOnHand: number; reorderLevel: number | null; isActive: boolean }
export interface Lot { id: ID; lotCode: string; variantId: ID; mfgDate: ISODate | null; expDate: ISODate | null; receivedAt: ISODateTime | null; note: string | null }

export interface Stock {
  id: ID; variantId: ID; lotId: ID; locationId: ID; qtyAvailable: number; qtyHeld: number; qtyQuarantine: number; qtyExpired: number; updatedAt: ISODateTime;
}

export interface StockLedger {
  id: ID; variantId: ID; lotId: ID | null; locationId: ID | null; toLocationId: ID | null; entryType: E.LedgerEntryType; qtyDelta: number;
  balanceAfter: number | null; refType: string | null; refId: ID | null; note: string | null; createdBy: ID | null; createdAt: ISODateTime;
}

/** Chưa có trong database/schema.sql, xem đề xuất DDL ở spec mục 5 #12. */
export interface Supplier {
  id: ID; code: string; name: string; taxCode: string | null; contactName: string | null; phone: string | null; email: string | null;
  address: string | null; note: string | null; isActive: boolean; createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface GoodsReceipt {
  id: ID; code: string; receiptType: E.ReceiptType; source: string | null; receivedBy: ID | null; receivedAt: ISODateTime; note: string | null;
}

export interface Stocktake {
  id: ID; code: string; scope: string | null; status: E.StocktakeStatus; createdBy: ID | null; countedBy: ID | null;
  startedAt: ISODateTime | null; closedAt: ISODateTime | null; note: string | null;
}

export interface StocktakeLine {
  id: ID; stocktakeId: ID; variantId: ID; lotId: ID | null; locationId: ID | null; systemQty: number; countedQty: number | null; diff: number;
}

export interface DamageReport {
  id: ID; code: string; variantId: ID; lotId: ID | null; locationId: ID | null; qty: number; reason: string | null; status: E.ApprovalStatus;
  reportedBy: ID | null; reviewedBy: ID | null; reportedAt: ISODateTime; reviewedAt: ISODateTime | null; note: string | null;
}

export interface SalesRequest {
  id: ID; code: string; customerId: ID | null; customerName: string | null; channel: E.SaleChannel; summary: string | null;
  status: E.RequestStatus; receivedAt: ISODateTime; handledBy: ID | null; note: string | null;
  /** Kết quả khả thi: true=Có, false=Không, null=chưa đánh giá. */
  feasible: boolean | null;
  fulfillableQty: number | null;
  shortageQty: number | null;
  expectedDate: ISODate | null;
  rejectReason: string | null;
}

export interface Quote {
  id: ID; code: string; customerId: ID; requestId: ID | null; status: E.QuoteStatus; subtotal: number; total: number; validUntil: ISODate | null;
  createdBy: ID | null; approvedBy: ID | null; note: string | null; createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface QuoteLine {
  id: ID; quoteId: ID; variantId: ID; unit: E.SellUnit; qty: number; qtyKhay: number; unitPrice: number; lineTotal: number; priceBasis: E.PriceBasis;
}

export interface SalesOrder {
  id: ID; code: string; customerId: ID | null; customerName?: string | null; orderType: E.OrderType; channel: E.SaleChannel | null; sourceQuoteId: ID | null;
  paymentMethod: E.PaymentMethod; paymentTermDays: number; paymentStatus: E.PaymentStatus; allocationStatus: E.AllocationStatus;
  fulfillmentMethod: E.FulfillmentMethod; shipAddressId: ID | null; state: E.OrderState; subtotal: number; discountTotal: number;
  shippingFee: number; total: number; voucherCode: string | null; isOverLimit: boolean; overLimitAmount: number; approvedBy: ID | null;
  placedBy: ID | null; placedAt: ISODateTime; holdExpiresAt: ISODateTime | null; note: string | null;
  createdAt: ISODateTime; updatedAt: ISODateTime;
}

export interface SalesOrderLine {
  id: ID; orderId: ID; variantId: ID; unit: E.SellUnit; qty: number; qtyKhay: number; unitPrice: number; discountPct: number;
  lineTotal: number; priceBasis: E.PriceBasis; outstandingQty: number; note: string | null;
}

export interface DeliveryItem { orderLineId: ID; qtyKhay: number }

export interface Delivery {
  id: ID; code: string | null; orderId: ID; legLabel: string | null; scheduledDate: ISODate | null; timeWindow: string | null;
  fulfillmentMethod: E.FulfillmentMethod; addressId: ID | null; inRadius: boolean; distanceKm: number | null; assignedTo: ID | null;
  carrier: string | null; items: DeliveryItem[] | null; state: E.DeliveryState; deliveredAt: ISODateTime | null; note: string | null;
}

export interface ReturnItem { variantId: ID; lotId: ID | null; qty: number; amount: number }

export interface SalesReturn {
  id: ID; code: string; orderId: ID | null; customerId: ID | null; reason: string | null; amount: number; items: ReturnItem[] | null;
  status: E.ReturnStatus; createdBy: ID | null; createdAt: ISODateTime;
}

export interface OrderEvent { id: ID; orderId: ID; eventType: string; description: string | null; createdBy: ID | null; createdAt: ISODateTime }

export interface Payment {
  id: ID; code: string | null; orderId: ID | null; customerId: ID | null; method: E.PaymentMethod; direction: E.PayDirection; amount: number;
  status: E.PayTxnStatus; reference: string | null; gateway: string | null; payosOrderCode: number | null; checkoutUrl: string | null;
  paidAt: ISODateTime; receivedBy: ID | null; note: string | null;
  channel?: E.SaleChannel | null;
  reconcileFlag?: E.PaymentReconcileFlag;
  needsRefund?: boolean;
}

export interface CustomerDebtLedger {
  id: ID; customerId: ID; orderId: ID | null; paymentId: ID | null; entryType: E.DebtEntryType; amount: number; balanceAfter: number | null;
  dueDate: ISODate | null; createdAt: ISODateTime; note: string | null;
}

export interface ProductReview {
  id: ID; productId: ID; customerId: ID | null; orderId: ID | null; rating: number; title: string | null; body: string | null;
  status: E.ReviewStatus; createdAt: ISODateTime;
}

export interface Voucher {
  id: ID; code: string; name: string | null; voucherType: E.VoucherType; value: number; minOrder: number; maxDiscount: number | null;
  startsAt: ISODateTime | null; endsAt: ISODateTime | null; usageLimit: number | null; perCustomerLimit: number | null; usedCount: number; isActive: boolean;
}

export interface ApprovalTask {
  id: ID; approvalType: E.ApprovalType; refType: string; refId: ID; title: string; status: E.ApprovalStatus; requestedBy: ID | null;
  decidedBy: ID | null; decidedAt: ISODateTime | null; note: string | null; createdAt: ISODateTime;
}

export interface Notification {
  id: ID; userId: ID; type: string | null; title: string; body: string | null; link: string | null; isRead: boolean; createdAt: ISODateTime;
}

export interface AppConfig { key: string; value: string | null; valueType: 'string' | 'int' | 'bool' | 'json'; description: string | null; updatedBy: ID | null; updatedAt: ISODateTime }

export interface AuditLog { id: ID; userId: ID | null; action: string; entityType: string | null; entityId: ID | null; detail: unknown; ipAddress: string | null; createdAt: ISODateTime }
