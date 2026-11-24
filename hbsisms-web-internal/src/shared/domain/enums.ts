// Enum trong database/schema.sql kèm nhãn tiếng Việt và tông màu chip.
export type Tone = 'green' | 'amber' | 'red' | 'blue' | 'neutral';
type Meta<K extends string> = Record<K, { label: string; tone: Tone }>;

export type Permission = 'orders' | 'inventory' | 'customers' | 'products' | 'debt' | 'reports' | 'approve' | 'admin';
export const permissionLabels: Record<Permission, string> = {
  orders: 'Đơn hàng', inventory: 'Kho', customers: 'Khách hàng', products: 'Sản phẩm',
  debt: 'Công nợ', reports: 'Báo cáo', approve: 'Phê duyệt', admin: 'Quản trị',
};

export type UserType = 'staff' | 'customer';
export type AddressKind = 'shipping' | 'billing' | 'both';

export type CustomerType = 'b2c' | 'b2b';
export const customerTypeMeta: Meta<CustomerType> = { b2c: { label: 'B2C', tone: 'neutral' }, b2b: { label: 'B2B', tone: 'blue' } };

export type SaleChannel = 'web' | 'phone' | 'zalo' | 'facebook' | 'counter' | 'app';
export const saleChannelLabels: Record<SaleChannel, string> = {
  web: 'Website', phone: 'Điện thoại', zalo: 'Zalo', facebook: 'Facebook', counter: 'Tại quầy', app: 'Ứng dụng',
};
/** Kênh sale chọn tay khi tạo đơn / ghi yêu cầu — ẩn web/app (hệ thống tự gán). */
export const staffSaleChannels: SaleChannel[] = ['phone', 'zalo', 'facebook', 'counter'];
export const staffSaleChannelLabels: Record<string, string> = Object.fromEntries(
  staffSaleChannels.map((c) => [c, saleChannelLabels[c]]),
);
/** Filter danh sách đơn: có Website, bỏ app (chưa dùng trong hệ thống). */
export const orderListSaleChannels: SaleChannel[] = ['web', 'phone', 'zalo', 'facebook', 'counter'];
export const orderListSaleChannelLabels: Record<string, string> = Object.fromEntries(
  orderListSaleChannels.map((c) => [c, saleChannelLabels[c]]),
);

export type RequestStatus = 'new' | 'quoting' | 'ordered' | 'rejected' | 'closed';
export const requestStatusMeta: Meta<RequestStatus> = {
  new: { label: 'Mới', tone: 'blue' }, quoting: { label: 'Đang báo giá', tone: 'amber' },
  ordered: { label: 'Đã lên đơn', tone: 'green' }, rejected: { label: 'Từ chối', tone: 'red' },
  closed: { label: 'Đã đóng', tone: 'neutral' },
};

export type QuoteStatus = 'draft' | 'sent' | 'approved' | 'rejected' | 'expired';
export const quoteStatusMeta: Meta<QuoteStatus> = {
  draft: { label: 'Nháp', tone: 'neutral' }, sent: { label: 'Đã gửi', tone: 'blue' }, approved: { label: 'Đã chốt', tone: 'green' },
  rejected: { label: 'Từ chối', tone: 'red' }, expired: { label: 'Hết hạn', tone: 'amber' },
};

export type OrderType = 'retail' | 'b2b' | 'online_b2c';
export const orderTypeLabels: Record<OrderType, string> = { retail: 'Bán lẻ', b2b: 'B2B', online_b2c: 'Online B2C' };
/** Loại đơn nhân viên tạo tay — ẩn online_b2c (web checkout tự gán). */
export const staffOrderTypes: OrderType[] = ['retail', 'b2b'];
export const staffOrderTypeLabels: Record<string, string> = Object.fromEntries(
  staffOrderTypes.map((t) => [t, orderTypeLabels[t]]),
);

export type OrderState = 'pending' | 'confirmed' | 'preparing' | 'to_ship' | 'partial' | 'delivered' | 'completed' | 'cancelled' | 'expired';
export const orderStateMeta: Meta<OrderState> = {
  pending: { label: 'Chờ xác nhận', tone: 'amber' }, confirmed: { label: 'Đã xác nhận', tone: 'blue' }, preparing: { label: 'Đang chuẩn bị', tone: 'blue' },
  to_ship: { label: 'Chờ giao', tone: 'blue' },
  // Legacy: giao dở — UI không còn “giao một phần”, hiện gần Chờ giao.
  partial: { label: 'Chờ giao', tone: 'blue' },
  delivered: { label: 'Đã giao', tone: 'green' },
  completed: { label: 'Hoàn tất', tone: 'green' }, cancelled: { label: 'Đã hủy', tone: 'neutral' },
  expired: { label: 'Hết hạn giữ hàng', tone: 'neutral' },
};

export type PaymentMethod = 'cash' | 'payos' | 'bank_transfer' | 'cod' | 'credit_net';
export const paymentMethodLabels: Record<PaymentMethod, string> = {
  cash: 'Tiền mặt', payos: 'PayOS', bank_transfer: 'Chuyển khoản', cod: 'COD', credit_net: 'Công nợ (NET)',
};

export type PaymentStatus = 'unpaid' | 'waiting' | 'paid' | 'partial' | 'debt' | 'diff' | 'refund_pending' | 'refunded';
export const paymentStatusMeta: Meta<PaymentStatus> = {
  unpaid: { label: 'Chưa thu', tone: 'neutral' }, waiting: { label: 'Chờ thanh toán', tone: 'amber' }, paid: { label: 'Đã thu', tone: 'green' },
  // Legacy DB vẫn có partial; UI không còn “thu một phần” — hiện như chưa thu đủ.
  partial: { label: 'Chưa thu', tone: 'neutral' }, debt: { label: 'Công nợ', tone: 'blue' }, diff: { label: 'Lệch tiền', tone: 'red' },
  refund_pending: { label: 'Chờ hoàn tiền', tone: 'amber' }, refunded: { label: 'Đã hoàn', tone: 'neutral' },
};
/**
 * Filter danh sách đơn: bỏ `diff` (BE ít gán) và `partial` (không thu một phần nữa).
 * Giữ waiting/debt/refund_* vì vẫn phát sinh từ web, nợ cũ, hủy/hoàn.
 */
export const orderListPaymentStatuses: PaymentStatus[] = [
  'unpaid', 'waiting', 'paid', 'debt', 'refund_pending', 'refunded',
];
export const orderListPaymentStatusMeta: Record<string, { label: string; tone: Tone }> = Object.fromEntries(
  orderListPaymentStatuses.map((s) => [s, paymentStatusMeta[s]]),
);

/**
 * Filter trạng thái đơn: bỏ `partial` (giao một lần — không tách đợt).
 * Enum gốc vẫn giữ để hiển thị dữ liệu cũ nếu có.
 */
export const orderListOrderStates: OrderState[] = [
  'pending', 'confirmed', 'preparing', 'to_ship', 'delivered', 'completed', 'cancelled', 'expired',
];
export const orderListOrderStateMeta: Record<string, { label: string; tone: Tone }> = Object.fromEntries(
  orderListOrderStates.map((s) => [s, orderStateMeta[s]]),
);

export type AllocationStatus = 'none' | 'incoming' | 'held' | 'full';
export const allocationStatusMeta: Meta<AllocationStatus> = {
  none: { label: 'Chưa giữ', tone: 'neutral' }, incoming: { label: 'Chờ hàng về', tone: 'amber' }, held: { label: 'Giữ một phần', tone: 'amber' }, full: { label: 'Đã giữ đủ', tone: 'green' },
};
/**
 * Filter danh sách đơn: chỉ Chưa giữ / Đã giữ đủ.
 * Không bán thiếu — không dùng “Giữ một phần” / “Chờ hàng về” trên UI lọc
 * (enum vẫn giữ để hiển thị dữ liệu cũ nếu có).
 */
export const orderListAllocationStatuses: AllocationStatus[] = ['none', 'full'];
export const orderListAllocationStatusMeta: Record<string, { label: string; tone: Tone }> = Object.fromEntries(
  orderListAllocationStatuses.map((s) => [s, allocationStatusMeta[s]]),
);

export type FulfillmentMethod = 'shop_delivery' | 'pickup' | 'partner_delivery';
export const fulfillmentLabels: Record<FulfillmentMethod, string> = { shop_delivery: 'Shop tự giao', pickup: 'Khách đến lấy', partner_delivery: 'Đối tác giao' };
/** Nhãn cách giao (màn Giao hàng, chi tiết đơn): Shop tự giao = cửa hàng tự lo (người trong tiệm / gửi nhà xe khi xa); Đối tác giao = book đơn vị vận chuyển. */
export const fulfillmentMeta: Meta<FulfillmentMethod> = {
  shop_delivery: { label: 'Shop tự giao', tone: 'blue' }, pickup: { label: 'Khách đến lấy', tone: 'neutral' }, partner_delivery: { label: 'Đối tác giao', tone: 'amber' },
};

export type SellUnit = 'pack' | 'case';
export const sellUnitLabels: Record<SellUnit, string> = { pack: 'Khay', case: 'Thùng' };

export type PriceBasis = 'list' | 'customer' | 'tier' | 'quote' | 'manual';
export const priceBasisMeta: Meta<PriceBasis> = {
  list: { label: 'Niêm yết', tone: 'neutral' }, customer: { label: 'Giá riêng', tone: 'green' }, tier: { label: 'Bậc số lượng', tone: 'blue' },
  quote: { label: 'Báo giá', tone: 'blue' }, manual: { label: 'Nhập tay', tone: 'amber' },
};

export type DeliveryState = 'ready' | 'going' | 'delivered' | 'failed';
export const deliveryStateMeta: Meta<DeliveryState> = {
  ready: { label: 'Sẵn sàng', tone: 'neutral' }, going: { label: 'Đang giao', tone: 'blue' }, delivered: { label: 'Đã giao', tone: 'green' }, failed: { label: 'Thất bại', tone: 'red' },
};

export type ReturnStatus = 'pending' | 'received' | 'discarded' | 'refunded' | 'rejected';
export const returnStatusMeta: Meta<ReturnStatus> = {
  pending: { label: 'Chờ nhận', tone: 'amber' },
  received: { label: 'Đã nhận hàng', tone: 'blue' },
  discarded: { label: 'Đã bỏ hàng', tone: 'red' },
  refunded: { label: 'Đã hoàn tiền', tone: 'green' },
  rejected: { label: 'Đã hủy', tone: 'red' },
};

export type PayDirection = 'in' | 'refund';
export const payDirectionLabels: Record<PayDirection, string> = { in: 'Thu', refund: 'Hoàn' };

export type PayTxnStatus = 'pending' | 'success' | 'failed' | 'cancelled';
export const payTxnStatusMeta: Meta<PayTxnStatus> = {
  pending: { label: 'Đang xử lý', tone: 'amber' }, success: { label: 'Thành công', tone: 'green' }, failed: { label: 'Thất bại', tone: 'red' }, cancelled: { label: 'Đã hủy', tone: 'neutral' },
};

/** Cờ đối soát thủ công trên giao dịch (Excel #39). */
export type PaymentReconcileFlag = 'none' | 'short' | 'over' | 'duplicate';
export const paymentReconcileFlagLabels: Record<PaymentReconcileFlag, string> = {
  none: 'Không',
  short: 'Thiếu',
  over: 'Thừa',
  duplicate: 'Trùng',
};
export const paymentReconcileFlagMeta: Meta<PaymentReconcileFlag> = {
  none: { label: 'Không', tone: 'neutral' },
  short: { label: 'Thiếu', tone: 'amber' },
  over: { label: 'Thừa', tone: 'blue' },
  duplicate: { label: 'Trùng', tone: 'red' },
};

export type DebtEntryType = 'charge' | 'payment' | 'adjust' | 'writeoff';
export const debtEntryLabels: Record<DebtEntryType, string> = { charge: 'Phát sinh nợ', payment: 'Thanh toán', adjust: 'Điều chỉnh', writeoff: 'Xóa nợ' };

export type LedgerEntryType = 'in' | 'out' | 'hold' | 'release' | 'move' | 'adjust' | 'damage' | 'expire' | 'return';
export const ledgerEntryMeta: Meta<LedgerEntryType> = {
  in: { label: 'Nhập', tone: 'green' }, out: { label: 'Xuất', tone: 'blue' }, hold: { label: 'Giữ', tone: 'amber' }, release: { label: 'Nhả', tone: 'neutral' },
  move: { label: 'Chuyển', tone: 'neutral' }, adjust: { label: 'Điều chỉnh', tone: 'amber' }, damage: { label: 'Hỏng', tone: 'red' }, expire: { label: 'Hết hạn', tone: 'red' },
  return: { label: 'Trả hàng', tone: 'green' },
};

export type ReceiptType = 'finished' | 'raw';
export const receiptTypeLabels: Record<ReceiptType, string> = { finished: 'Thành phẩm', raw: 'Nguyên liệu' };
/** Phiếu NVL: tạo → Chờ nhập (chưa cộng tồn); xác nhận nhập kho → Hoàn thành; phiếu chờ có thể Hủy. */
export type ReceiptStatus = 'pending' | 'completed' | 'cancelled';
export const receiptStatusMeta: Meta<ReceiptStatus> = {
  pending: { label: 'Chờ nhập', tone: 'amber' }, completed: { label: 'Hoàn thành', tone: 'green' }, cancelled: { label: 'Đã hủy', tone: 'neutral' },
};

/**
 * Lệnh sản xuất (Inter 1 dòng 48–52): Nháp → Đã duyệt (tự tạo phiếu xuất NVL) → Đang sản xuất (đã xuất NVL)
 * → Chờ nhập kho (đã báo làm xong, phiếu nhập thành phẩm chờ) → Hoàn thành (tự chuyển khi nhập kho xong).
 * `completed` đã chốt nhưng backend chưa có (dòng 52).
 */
export type ProductionOrderStatus = 'draft' | 'approved' | 'in_progress' | 'awaiting_receipt' | 'completed' | 'cancelled';
export const productionOrderStatusMeta: Meta<ProductionOrderStatus> = {
  draft: { label: 'Nháp', tone: 'neutral' },
  approved: { label: 'Đã duyệt', tone: 'blue' },
  in_progress: { label: 'Đang sản xuất', tone: 'blue' },
  awaiting_receipt: { label: 'Chờ nhập kho', tone: 'amber' },
  completed: { label: 'Hoàn thành', tone: 'green' },
  cancelled: { label: 'Đã hủy', tone: 'red' },
};

export type StocktakeStatus = 'draft' | 'counting' | 'done' | 'cancelled';
export const stocktakeStatusMeta: Meta<StocktakeStatus> = {
  draft: { label: 'Nháp', tone: 'neutral' }, counting: { label: 'Đang đếm', tone: 'amber' }, done: { label: 'Hoàn tất', tone: 'green' }, cancelled: { label: 'Đã hủy', tone: 'neutral' },
};

export type ReviewStatus = 'approved' | 'pending' | 'rejected';
export const reviewStatusMeta: Meta<ReviewStatus> = { approved: { label: 'Đã duyệt', tone: 'green' }, pending: { label: 'Chờ duyệt', tone: 'amber' }, rejected: { label: 'Đã ẩn', tone: 'neutral' } };

export type ApprovalType = 'over_limit_order' | 'damage_report' | 'price_list' | 'stocktake' | 'other';
export const approvalTypeLabels: Record<ApprovalType, string> = {
  over_limit_order: 'Đơn vượt hạn mức', damage_report: 'Báo hỏng', price_list: 'Giá riêng', stocktake: 'Kiểm kê', other: 'Khác',
};

export type ApprovalStatus = 'pending' | 'approved' | 'rejected';
export const approvalStatusMeta: Meta<ApprovalStatus> = { pending: { label: 'Chờ duyệt', tone: 'amber' }, approved: { label: 'Đã duyệt', tone: 'green' }, rejected: { label: 'Từ chối', tone: 'red' } };

export type VoucherType = 'percent' | 'amount' | 'freeship';
export const voucherTypeLabels: Record<VoucherType, string> = { percent: 'Giảm %', amount: 'Giảm tiền', freeship: 'Miễn phí giao' };
