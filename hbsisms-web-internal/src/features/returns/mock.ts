import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, appendLedger, audit, customerName, postDebtEntry, userName, variantLabel } from '@/shared/mock/services';

function toItem(r: (typeof db.returns)[number]) {
  return {
    ...r,
    orderCode: db.orders.find((o) => o.id === r.orderId)?.code ?? null,
    customerName: customerName(r.customerId) ?? 'Khách lẻ',
    creatorName: userName(r.createdBy),
    items: (r.items ?? []).map((i) => ({ ...i, ...variantLabel(i.variantId) })),
  };
}

/** Số khay còn trả được = đã bán − các phiếu trả chưa bị từ chối. */
function returnableLines(orderId: number) {
  const returned = new Map<number, number>();
  db.returns.filter((r) => r.orderId === orderId && r.status !== 'rejected').flatMap((r) => r.items ?? []).forEach((i) => returned.set(i.variantId, (returned.get(i.variantId) ?? 0) + i.qty));
  return db.orderLines
    .filter((l) => l.orderId === orderId)
    .map((l) => {
      const label = variantLabel(l.variantId);
      return { variantId: l.variantId, sku: label.sku, productName: label.productName, packLabel: label.packLabel, unitPrice: l.unitPrice, maxQty: l.qtyKhay - (returned.get(l.variantId) ?? 0) };
    })
    .filter((l) => l.maxQty > 0);
}

mock('GET', '/returns/returnable-orders', () =>
  db.orders
    .filter((o) => ['delivered', 'completed', 'partial'].includes(o.state))
    .sort((a, b) => b.placedAt.localeCompare(a.placedAt))
    .slice(0, 100)
    .map((o) => ({ id: o.id, code: o.code, customerName: customerName(o.customerId) ?? 'Khách lẻ', lines: returnableLines(o.id) }))
    .filter((o) => o.lines.length > 0),
);

mock('GET', '/returns', ({ query }) => {
  const rows = db.returns.map(toItem).filter((r) => matches(query.q, r.code, r.orderCode, r.customerName)).filter((r) => !query.status || r.status === query.status).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return paginate(rows, query);
});

mock('POST', '/returns', ({ body, userId }) => {
  const order = db.orders.find((o) => o.id === body.orderId);
  if (!order) fail(400, 'Vui lòng chọn đơn hàng');
  const lines = returnableLines(order.id);
  const items = body.items.filter((i: any) => i.qty > 0).map((i: any) => {
    const line = lines.find((l) => l.variantId === i.variantId);
    if (!line || i.qty > line.maxQty) fail(409, 'Số lượng trả vượt quá số đã mua');
    return { variantId: i.variantId, lotId: null, qty: i.qty, amount: i.qty * line.unitPrice };
  });
  if (!items.length) fail(400, 'Phiếu trả phải có ít nhất một khay');
  const row = { id: nextId(db.returns), code: nextCode('TH', db.returns), orderId: order.id, customerId: order.customerId, reason: body.reason, amount: items.reduce((s: number, i: any) => s + i.amount, 0), items, status: 'pending' as const, createdBy: userId, createdAt: new Date().toISOString() };
  db.returns.push(row);
  addOrderEvent(order.id, 'return', `Tạo phiếu trả ${row.code}`, userId);
  return toItem(row);
});

const ALLOWED: Record<string, string[]> = { pending: ['received', 'rejected'], received: ['refunded'] };

mock('PATCH', '/returns/:id/status', ({ params, body, userId }) => {
  const row = db.returns.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy phiếu trả');
  if (!ALLOWED[row.status]?.includes(body.status)) fail(409, 'Không thể chuyển sang trạng thái này');
  row.status = body.status;
  if (body.status === 'received') {
    // Hàng trả về vào khu cách ly, không bán lại ngay.
    row.items?.forEach((item) => {
      const stock = db.stock.filter((s) => s.variantId === item.variantId).sort((a, b) => b.lotId - a.lotId)[0];
      if (!stock) return;
      stock.qtyQuarantine += item.qty;
      appendLedger({ variantId: item.variantId, lotId: stock.lotId, locationId: stock.locationId, toLocationId: null, entryType: 'adjust', qtyDelta: item.qty, balanceAfter: stock.qtyAvailable, refType: 'sales_return', refId: row.id, note: 'Hàng trả về, cách ly', createdBy: userId });
    });
  }
  if (body.status === 'refunded') {
    const order = db.orders.find((o) => o.id === row.orderId);
    const isCredit = order?.paymentMethod === 'credit_net';
    if (isCredit && row.customerId) {
      postDebtEntry(row.customerId, { orderId: row.orderId, entryType: 'adjust', amount: -row.amount, note: `Giảm nợ do trả hàng ${row.code}` });
    } else {
      const id = nextId(db.payments);
      db.payments.push({ id, code: nextCode('TT', db.payments), orderId: row.orderId, customerId: row.customerId, method: order?.paymentMethod ?? 'cash', direction: 'refund', amount: row.amount, status: 'success', reference: row.code, gateway: null, payosOrderCode: null, checkoutUrl: null, paidAt: new Date().toISOString(), receivedBy: userId, note: `Hoàn tiền ${row.code}` });
    }
  }
  if (row.orderId) addOrderEvent(row.orderId, 'return', `Phiếu trả ${row.code}: ${body.status === 'received' ? 'đã nhận hàng' : body.status === 'refunded' ? 'đã hoàn tiền' : 'từ chối'}`, userId);
  audit(userId, 'update', 'sales_return', row.id, { status: body.status });
});
