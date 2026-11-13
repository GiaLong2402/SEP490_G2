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

export type OrderState = 'pending' | 'confirmed' | 'preparing' | 'to_ship' | 'partial' | 'delivered' | 'completed' | 'cancelled';
export const orderStateMeta: Meta<OrderState> = {
  pending: { label: 'Chờ xác nhận', tone: 'amber' }, confirmed: { label: 'Đã xác nhận', tone: 'blue' }, preparing: { label: 'Đang chuẩn bị', tone: 'blue' },
  to_ship: { label: 'Chờ giao', tone: 'blue' }, partial: { label: 'Giao một phần', tone: 'amber' }, delivered: { label: 'Đã giao', tone: 'green' },
  completed: { label: 'Hoàn tất', tone: 'green' }, cancelled: { label: 'Đã hủy', tone: 'neutral' },
};

export type PaymentMethod = 'cash' | 'payos' | 'bank_transfer' | 'cod' | 'credit_net';
export const paymentMethodLabels: Record<PaymentMethod, string> = {
  cash: 'Tiền mặt', payos: 'PayOS', bank_transfer: 'Chuyển khoản', cod: 'COD', credit_net: 'Công nợ (NET)',
};

export type PaymentStatus = 'unpaid' | 'waiting' | 'paid' | 'partial' | 'debt' | 'diff' | 'refund_pending' | 'refunded';
export const paymentStatusMeta: Meta<PaymentStatus> = {
  unpaid: { label: 'Chưa thu', tone: 'neutral' }, waiting: { label: 'Chờ thanh toán', tone: 'amber' }, paid: { label: 'Đã thu', tone: 'green' },
  partial: { label: 'Thu một phần', tone: 'amber' }, debt: { label: 'Công nợ', tone: 'blue' }, diff: { label: 'Lệch tiền', tone: 'red' },
  refund_pending: { label: 'Chờ hoàn tiền', tone: 'amber' }, refunded: { label: 'Đã hoàn', tone: 'neutral' },
};
/**
 * Filter danh sách đơn: bỏ `diff` (enum có sẵn nhưng BE hiện không gán).
 * Giữ waiting/debt/refund_* vì vẫn phát sinh từ web, POS công nợ, hủy/hoàn.
 */
export const orderListPaymentStatuses: PaymentStatus[] = [
  'unpaid', 'waiting', 'paid', 'partial', 'debt', 'refund_pending', 'refunded',
];
export const orderListPaymentStatusMeta: Record<string, { label: string; tone: Tone }> = Object.fromEntries(
  orderListPaymentStatuses.map((s) => [s, paymentStatusMeta[s]]),
);

export type AllocationStatus = 'none' | 'incoming' | 'held' | 'full';
export const allocationStatusMeta: Meta<AllocationStatus> = {
  none: { label: 'Chưa giữ', tone: 'neutral' }, incoming: { label: 'Chờ hàng về', tone: 'amber' }, held: { label: 'Giữ một phần', tone: 'amber' }, full: { label: 'Đã giữ đủ', tone: 'green' },
};

export type FulfillmentMethod = 'shop_delivery' | 'pickup' | 'partner_delivery';
export const fulfillmentLabels: Record<FulfillmentMethod, string> = { shop_delivery: 'Shop tự giao', pickup: 'Khách đến lấy', partner_delivery: 'Đối tác giao' };

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

export type ReturnStatus = 'pending' | 'received' | 'refunded' | 'rejected';
export const returnStatusMeta: Meta<ReturnStatus> = {
  pending: { label: 'Chờ nhận', tone: 'amber' }, received: { label: 'Đã nhận hàng', tone: 'blue' }, refunded: { label: 'Đã hoàn tiền', tone: 'green' }, rejected: { label: 'Từ chối', tone: 'red' },
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

export type LedgerEntryType = 'in' | 'out' | 'hold' | 'release' | 'move' | 'adjust' | 'damage' | 'expire';
export const ledgerEntryMeta: Meta<LedgerEntryType> = {
  in: { label: 'Nhập', tone: 'green' }, out: { label: 'Xuất', tone: 'blue' }, hold: { label: 'Giữ', tone: 'amber' }, release: { label: 'Nhả', tone: 'neutral' },
  move: { label: 'Chuyển', tone: 'neutral' }, adjust: { label: 'Điều chỉnh', tone: 'amber' }, damage: { label: 'Hỏng', tone: 'red' }, expire: { label: 'Hết hạn', tone: 'red' },
};

export type ReceiptType = 'finished' | 'raw';
export const receiptTypeLabels: Record<ReceiptType, string> = { finished: 'Thành phẩm', raw: 'Nguyên liệu' };

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
