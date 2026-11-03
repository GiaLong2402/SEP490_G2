import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, audit, confirmOrder, creditCheck, createApproval, customerName, priceLines, userName, variantLabel } from '@/shared/mock/services';

const today = () => new Date().toISOString().slice(0, 10);

// Báo giá đã gửi mà quá hạn thì tự chuyển "Hết hạn"; backend làm bằng job định kỳ.
const expireQuotes = () => db.quotes.forEach((q) => { if (q.status === 'sent' && q.validUntil && q.validUntil < today()) q.status = 'expired'; });

const toItem = (q: (typeof db.quotes)[number]) => ({
  ...q, customerName: customerName(q.customerId) ?? '—', creatorName: userName(q.createdBy), convertedOrderId: db.orders.find((o) => o.sourceQuoteId === q.id)?.id ?? null,
});

function toDetail(id: number) {
  const quote = db.quotes.find((q) => q.id === id);
  if (!quote) fail(404, 'Không tìm thấy báo giá');
  return {
    ...toItem(quote),
    lines: db.quoteLines.filter((l) => l.quoteId === id).map((l) => ({ ...l, ...variantLabel(l.variantId) })),
    requestCode: db.requests.find((r) => r.id === quote.requestId)?.code ?? null,
    convertedOrderCode: db.orders.find((o) => o.sourceQuoteId === id)?.code ?? null,
  };
}

const ALLOWED: Record<string, string[]> = { draft: ['sent'], sent: ['approved', 'rejected'] };

mock('GET', '/quotes', ({ query }) => {
  expireQuotes();
  const rows = db.quotes
    .filter((q) => matches(query.q, q.code, customerName(q.customerId)))
    .filter((q) => !query.status || q.status === query.status)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/quotes/:id', ({ params }) => {
  expireQuotes();
  return toDetail(Number(params.id));
});

mock('POST', '/quotes', ({ body, userId }) => {
  if (!db.customers.some((c) => c.id === body.customerId)) fail(400, 'Vui lòng chọn khách hàng');
  const priced = priceLines(body.customerId, body.lines);
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const now = new Date().toISOString();
  const id = nextId(db.quotes);
  db.quotes.push({ id, code: nextCode('BG', db.quotes), customerId: body.customerId, requestId: body.requestId, status: 'draft', subtotal, total: subtotal, validUntil: body.validUntil, createdBy: userId, approvedBy: null, note: body.note, createdAt: now, updatedAt: now });
  priced.forEach(({ available: _a, discountPct: _d, ...line }) => db.quoteLines.push({ ...line, id: nextId(db.quoteLines), quoteId: id }));
  const request = db.requests.find((r) => r.id === body.requestId);
  if (request) request.status = 'quoting';
  audit(userId, 'create', 'quote', id);
  return toDetail(id);
});

mock('PATCH', '/quotes/:id/status', ({ params, body, userId }) => {
  const quote = db.quotes.find((q) => q.id === Number(params.id));
  if (!quote) fail(404, 'Không tìm thấy báo giá');
  if (!ALLOWED[quote.status]?.includes(body.status)) fail(409, 'Không thể chuyển sang trạng thái này');
  quote.status = body.status;
  if (body.status === 'approved') quote.approvedBy = userId;
  quote.updatedAt = new Date().toISOString();
});

mock('POST', '/quotes/:id/convert', ({ params, userId }) => {
  const quote = db.quotes.find((q) => q.id === Number(params.id));
  if (!quote) fail(404, 'Không tìm thấy báo giá');
  if (quote.status !== 'approved') fail(409, 'Chỉ báo giá đã chốt mới chuyển được thành đơn');
  if (db.orders.some((o) => o.sourceQuoteId === quote.id)) fail(409, 'Báo giá này đã có đơn hàng');
  const customer = db.customers.find((c) => c.id === quote.customerId)!;
  const isB2b = customer.customerType === 'b2b';
  const paymentMethod = isB2b ? ('credit_net' as const) : ('bank_transfer' as const);
  const now = new Date().toISOString();
  const id = nextId(db.orders);
  const address = db.addresses.find((a) => a.customerId === customer.id && a.isDefault);
  const check = creditCheck(customer.id, paymentMethod, quote.total);
  const order: (typeof db.orders)[number] = {
    id, code: nextCode('SO-2026', db.orders), customerId: customer.id, orderType: isB2b ? 'b2b' : 'retail', channel: 'phone', sourceQuoteId: quote.id, paymentMethod, paymentTermDays: isB2b ? customer.netDays : 0,
    paymentStatus: 'unpaid', allocationStatus: 'none', fulfillmentMethod: address?.inDeliveryRadius === false ? 'partner_delivery' : 'shop_delivery', shipAddressId: address?.id ?? null, state: 'pending', subtotal: quote.subtotal, discountTotal: 0, shippingFee: 0,
    total: quote.total, voucherCode: null, ...check, approvedBy: null, placedBy: userId, placedAt: now, note: `Từ báo giá ${quote.code}`, createdAt: now, updatedAt: now,
  };
  db.orders.push(order);
  // Giữ nguyên giá đã chốt trong báo giá (snapshot), nguồn giá = 'quote'.
  db.quoteLines.filter((l) => l.quoteId === quote.id).forEach((l) => db.orderLines.push({ id: nextId(db.orderLines), orderId: id, variantId: l.variantId, unit: l.unit, qty: l.qty, qtyKhay: l.qtyKhay, unitPrice: l.unitPrice, discountPct: 0, lineTotal: l.lineTotal, priceBasis: 'quote', note: null }));
  addOrderEvent(id, 'created', `Tạo đơn từ báo giá ${quote.code}`, userId);
  const request = db.requests.find((r) => r.id === quote.requestId);
  if (request) request.status = 'ordered';
  if (order.isOverLimit) {
    createApproval('over_limit_order', 'sales_order', id, `Đơn ${order.code} của ${customer.name} vượt hạn mức ${order.overLimitAmount.toLocaleString('vi-VN')} ₫`, userId);
  } else {
    confirmOrder(order, userId);
  }
  return { orderId: id, orderCode: order.code };
});
