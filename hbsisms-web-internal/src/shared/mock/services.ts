// Quy tắc nghiệp vụ mà backend thật phải thực thi; mock dùng lại cho nhiều feature.
import type { ID, SalesOrder } from '@/shared/domain/entities';
import type { PriceBasis } from '@/shared/domain/enums';
import { fail } from '@/shared/api/mockServer';
import { db, nextId } from './db';

export function requireUser(userId: number | null) {
  const user = db.users.find((u) => u.id === userId);
  if (!user) fail(401, 'Phiên đăng nhập đã hết hạn');
  return user;
}

export const userName = (id: ID | null | undefined) => db.users.find((u) => u.id === id)?.fullName ?? null;
export const customerName = (id: ID | null | undefined) => db.customers.find((c) => c.id === id)?.name ?? null;
export const productOfVariant = (variantId: ID) => {
  const variant = db.variants.find((v) => v.id === variantId)!;
  return { variant, product: db.products.find((p) => p.id === variant.productId)! };
};
export const variantLabel = (variantId: ID) => {
  const { variant, product } = productOfVariant(variantId);
  return { sku: variant.sku, productName: product.name, packLabel: variant.packLabel };
};
/** Mã vị trí chỉ duy nhất trong một kho, nên khi có nhiều kho phải kèm mã kho để khỏi nhầm. */
export function locationCode(id: ID | null | undefined) {
  const location = db.locations.find((l) => l.id === id);
  if (!location) return null;
  return db.warehouses.length > 1 ? `${db.warehouses.find((w) => w.id === location.warehouseId)?.code}/${location.code}` : location.code;
}
export const configInt = (key: string, fallback: number) => {
  const n = Number(db.config.find((c) => c.key === key)?.value);
  return Number.isFinite(n) ? n : fallback;
};
// Theo giờ địa phương: toISOString là UTC nên trước 7 giờ sáng ở Việt Nam sẽ ra ngày hôm qua.
const today = () => {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};

/** Giá: giá riêng đã duyệt còn hiệu lực → bậc số lượng theo khay → niêm yết. */
export function resolvePrice(customerId: ID | null, variantId: ID, qtyKhay: number): { unitPrice: number; priceBasis: PriceBasis } {
  const variant = db.variants.find((v) => v.id === variantId);
  if (!variant) fail(400, 'Quy cách không tồn tại');
  const special = db.customerPrices.find(
    (p) => p.customerId === customerId && p.variantId === variantId && p.approvedAt !== null && (!p.effectiveFrom || p.effectiveFrom <= today()) && (!p.effectiveTo || p.effectiveTo >= today()),
  );
  if (special) return { unitPrice: special.unitPrice, priceBasis: 'customer' };
  const tier = db.tiers.filter((t) => t.variantId === variantId && t.minQtyKhay <= qtyKhay).sort((a, b) => b.minQtyKhay - a.minQtyKhay)[0];
  if (tier) return { unitPrice: tier.unitPrice, priceBasis: 'tier' };
  return { unitPrice: variant.listPrice, priceBasis: 'list' };
}

/** Số khay bán được: không tính lô đã quá hạn, khớp với cách giữ hàng FEFO. */
export function availableQty(variantId: ID) {
  return db.stock
    .filter((s) => s.variantId === variantId && (db.lots.find((l) => l.id === s.lotId)?.expDate ?? '9999-12-31') >= today())
    .reduce((sum, s) => sum + s.qtyAvailable, 0);
}

function appendLedger(entry: Omit<(typeof db.ledger)[number], 'id' | 'createdAt'>) {
  db.ledger.push({ ...entry, id: nextId(db.ledger), createdAt: new Date().toISOString() });
}
export { appendLedger };

/** Giữ hàng FEFO: lấy lô có HSD sớm nhất và chưa hết hạn trước. Trả về số khay giữ được. */
export function holdFefo(variantId: ID, qty: number, refId: ID, userId: ID | null) {
  const candidates = db.stock
    .filter((s) => s.variantId === variantId && s.qtyAvailable > 0)
    .map((s) => ({ s, exp: db.lots.find((l) => l.id === s.lotId)?.expDate ?? '9999-12-31' }))
    .filter(({ exp }) => exp >= today())
    .sort((a, b) => a.exp.localeCompare(b.exp));
  let remaining = qty;
  for (const { s } of candidates) {
    if (remaining <= 0) break;
    const take = Math.min(remaining, s.qtyAvailable);
    s.qtyAvailable -= take;
    s.qtyHeld += take;
    remaining -= take;
    appendLedger({ variantId, lotId: s.lotId, locationId: s.locationId, toLocationId: null, entryType: 'hold', qtyDelta: -take, balanceAfter: s.qtyAvailable, refType: 'sales_order_line', refId, note: 'Giữ hàng theo FEFO', createdBy: userId });
  }
  return qty - remaining;
}

/** Bán tại quầy: xuất thẳng khỏi tồn khả dụng theo lô có HSD sớm nhất, không qua bước giữ hàng. */
export function sellFefo(variantId: ID, qty: number, refId: ID, userId: ID | null) {
  const rows = db.stock
    .filter((s) => s.variantId === variantId && s.qtyAvailable > 0)
    .map((s) => ({ s, exp: db.lots.find((l) => l.id === s.lotId)?.expDate ?? '9999-12-31' }))
    .filter(({ exp }) => exp >= today())
    .sort((a, b) => a.exp.localeCompare(b.exp));
  let remaining = qty;
  for (const { s } of rows) {
    if (remaining <= 0) break;
    const take = Math.min(remaining, s.qtyAvailable);
    s.qtyAvailable -= take;
    remaining -= take;
    appendLedger({ variantId, lotId: s.lotId, locationId: s.locationId, toLocationId: null, entryType: 'out', qtyDelta: -take, balanceAfter: s.qtyAvailable, refType: 'sales_order_line', refId, note: 'Bán tại quầy', createdBy: userId });
  }
}

export function allocateOrder(order: SalesOrder, userId: ID | null) {
  const lines = db.orderLines.filter((l) => l.orderId === order.id);
  let wanted = 0;
  let held = 0;
  lines.forEach((line) => {
    wanted += line.qtyKhay;
    held += holdFefo(line.variantId, line.qtyKhay, line.id, userId);
  });
  order.allocationStatus = held === 0 ? 'incoming' : held < wanted ? 'held' : 'full';
}

/** Nhả toàn bộ hàng đang giữ của đơn khi hủy. */
export function releaseOrder(order: SalesOrder, userId: ID | null) {
  const lineIds = new Set(db.orderLines.filter((l) => l.orderId === order.id).map((l) => l.id));
  db.ledger
    .filter((e) => e.entryType === 'hold' && e.refType === 'sales_order_line' && e.refId !== null && lineIds.has(e.refId))
    .forEach((hold) => {
      const stock = db.stock.find((s) => s.lotId === hold.lotId && s.locationId === hold.locationId);
      if (!stock) return;
      const qty = Math.min(-hold.qtyDelta, stock.qtyHeld);
      stock.qtyHeld -= qty;
      stock.qtyAvailable += qty;
      appendLedger({ variantId: hold.variantId, lotId: hold.lotId, locationId: hold.locationId, toLocationId: null, entryType: 'release', qtyDelta: qty, balanceAfter: stock.qtyAvailable, refType: hold.refType, refId: hold.refId, note: 'Nhả hàng do hủy đơn', createdBy: userId });
    });
  order.allocationStatus = 'none';
}

export function postDebtEntry(customerId: ID, entry: { orderId?: ID | null; paymentId?: ID | null; entryType: 'charge' | 'payment' | 'adjust' | 'writeoff'; amount: number; dueDate?: string | null; note: string }) {
  const customer = db.customers.find((c) => c.id === customerId);
  if (!customer) fail(400, 'Khách hàng không tồn tại');
  customer.currentDebt += entry.amount;
  db.debtLedger.push({
    id: nextId(db.debtLedger), customerId, orderId: entry.orderId ?? null, paymentId: entry.paymentId ?? null, entryType: entry.entryType, amount: entry.amount,
    balanceAfter: customer.currentDebt, dueDate: entry.dueDate ?? null, createdAt: new Date().toISOString(), note: entry.note,
  });
}

export function addOrderEvent(orderId: ID, eventType: string, description: string, createdBy: ID | null) {
  db.orderEvents.push({ id: nextId(db.orderEvents), orderId, eventType, description, createdBy, createdAt: new Date().toISOString() });
}

export function audit(userId: ID | null, action: string, entityType: string, entityId: ID | null, detail: unknown = null) {
  db.auditLog.push({ id: nextId(db.auditLog), userId, action, entityType, entityId, detail, ipAddress: '127.0.0.1', createdAt: new Date().toISOString() });
}

export function notify(permission: 'approve' | 'orders' | 'inventory', title: string, body: string, link: string) {
  db.users
    .filter((u) => u.isActive && db.roles.find((r) => r.id === u.roleId)?.permissions.includes(permission))
    .forEach((u) => {
      db.notifications.push({ id: nextId(db.notifications), userId: u.id, type: permission, title, body, link, isRead: false, createdAt: new Date().toISOString() });
    });
}

export function createApproval(approvalType: (typeof db.approvals)[number]['approvalType'], refType: string, refId: ID, title: string, requestedBy: ID | null) {
  db.approvals.push({ id: nextId(db.approvals), approvalType, refType, refId, title, status: 'pending', requestedBy, decidedBy: null, decidedAt: null, note: null, createdAt: new Date().toISOString() });
  notify('approve', 'Có việc mới cần duyệt', title, '/approvals');
}

/** Trừ một lượng khỏi tồn có sẵn của lô để ghi nhận hỏng, hết hạn hoặc chênh lệch. */
export function deductStock(variantId: ID, lotId: ID | null, locationId: ID | null, qty: number) {
  const row = db.stock.find((s) => s.variantId === variantId && (lotId === null || s.lotId === lotId) && (locationId === null || s.locationId === locationId));
  if (!row) return null;
  let left = qty;
  for (const key of ['qtyQuarantine', 'qtyAvailable', 'qtyExpired'] as const) {
    const take = Math.min(left, row[key]);
    row[key] -= take;
    left -= take;
  }
  return row;
}

export interface LineInput {
  variantId: ID;
  unit: 'pack' | 'case';
  qty: number;
  manualPrice?: number | null;
  discountPct?: number;
}

/** Quy đổi thùng → khay, áp giá và tính thành tiền cho dòng đơn/báo giá. */
export function priceLines(customerId: ID | null, lines: LineInput[]) {
  return lines.map((line) => {
    const variant = db.variants.find((v) => v.id === Number(line.variantId));
    if (!variant) fail(400, 'Quy cách không tồn tại');
    const qty = Number(line.qty);
    if (!Number.isInteger(qty) || qty <= 0) fail(400, 'Số lượng phải là số nguyên dương');
    const qtyKhay = line.unit === 'case' ? qty * variant.packPerCase : qty;
    const resolved = resolvePrice(customerId, variant.id, qtyKhay);
    const manual = line.manualPrice !== null && line.manualPrice !== undefined && Number(line.manualPrice) >= 0;
    const unitPrice = manual ? Number(line.manualPrice) : resolved.unitPrice;
    const discountPct = Number(line.discountPct ?? 0);
    return {
      variantId: variant.id, unit: line.unit, qty, qtyKhay, unitPrice, discountPct, priceBasis: (manual ? 'manual' : resolved.priceBasis) as PriceBasis,
      lineTotal: Math.round(unitPrice * qtyKhay * (1 - discountPct / 100)), available: availableQty(variant.id),
    };
  });
}

/** Vượt hạn mức khi nợ hiện tại + giá trị đơn công nợ > hạn mức. */
export function creditCheck(customerId: ID | null, paymentMethod: string, total: number) {
  const customer = db.customers.find((c) => c.id === customerId);
  if (!customer || paymentMethod !== 'credit_net') return { isOverLimit: false, overLimitAmount: 0 };
  const overLimitAmount = Math.max(0, customer.currentDebt + total - customer.creditLimit);
  return { isOverLimit: overLimitAmount > 0, overLimitAmount };
}

/** Xuất kho khi giao: trừ phần đang giữ theo lô HSD sớm nhất. */
export function shipHeld(variantId: ID, qty: number, refId: ID, userId: ID | null) {
  const rows = db.stock
    .filter((s) => s.variantId === variantId && s.qtyHeld > 0)
    .sort((a, b) => (db.lots.find((l) => l.id === a.lotId)?.expDate ?? '').localeCompare(db.lots.find((l) => l.id === b.lotId)?.expDate ?? ''));
  let remaining = qty;
  for (const row of rows) {
    if (remaining <= 0) break;
    const take = Math.min(remaining, row.qtyHeld);
    row.qtyHeld -= take;
    remaining -= take;
    appendLedger({ variantId, lotId: row.lotId, locationId: row.locationId, toLocationId: null, entryType: 'out', qtyDelta: -take, balanceAfter: row.qtyAvailable, refType: 'delivery', refId, note: 'Xuất giao hàng', createdBy: userId });
  }
}

/** Xác nhận đơn: giữ hàng FEFO và ghi nợ nếu mua công nợ. */
export function confirmOrder(order: SalesOrder, userId: ID | null) {
  order.state = 'confirmed';
  allocateOrder(order, userId);
  if (order.paymentMethod === 'credit_net' && order.customerId) {
    const due = new Date();
    due.setDate(due.getDate() + order.paymentTermDays);
    postDebtEntry(order.customerId, { orderId: order.id, entryType: 'charge', amount: order.total, dueDate: due.toISOString().slice(0, 10), note: `Đơn ${order.code}` });
    order.paymentStatus = 'debt';
  }
  addOrderEvent(order.id, 'confirmed', order.allocationStatus === 'full' ? 'Xác nhận đơn, đã giữ đủ hàng theo FEFO' : 'Xác nhận đơn, chưa giữ đủ hàng', userId);
  order.updatedAt = new Date().toISOString();
}

/** Tuổi nợ: phân bổ phần đã trả vào khoản đến hạn sớm nhất rồi mới chia nhóm (sửa lỗi view v_debt_aging, spec mục 5 #1-2). */
export function debtAging(customerId: ID) {
  const rows = db.debtLedger.filter((d) => d.customerId === customerId);
  let credit = -rows.filter((d) => d.amount < 0).reduce((sum, d) => sum + d.amount, 0);
  const buckets = { d0: 0, d1_30: 0, d31_60: 0, d60: 0 };
  const now = new Date(today()).getTime();
  rows
    .filter((d) => d.amount > 0)
    .sort((a, b) => (a.dueDate ?? '').localeCompare(b.dueDate ?? ''))
    .forEach((charge) => {
      const applied = Math.min(credit, charge.amount);
      credit -= applied;
      const open = charge.amount - applied;
      if (open <= 0) return;
      const overdue = charge.dueDate ? Math.round((now - new Date(charge.dueDate).getTime()) / 86_400_000) : 0;
      if (overdue <= 0) buckets.d0 += open;
      else if (overdue <= 30) buckets.d1_30 += open;
      else if (overdue <= 60) buckets.d31_60 += open;
      else buckets.d60 += open;
    });
  return buckets;
}
