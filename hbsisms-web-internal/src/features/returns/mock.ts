import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, appendLedger, audit, customerName, locationCode, postDebtEntry, userName, variantLabel } from '@/shared/mock/services';

function toItem(r: (typeof db.returns)[number]) {
  return {
    ...r,
    orderCode: db.orders.find((o) => o.id === r.orderId)?.code ?? null,
    customerName: customerName(r.customerId) ?? 'Khách lẻ',
    creatorName: userName(r.createdBy),
    items: (r.items ?? []).map((i) => ({ ...i, ...variantLabel(i.variantId) })),
  };
}

/** Số khay còn trả được (khách trả sau khi nhận) = đã bán − phiếu trả chưa từ chối (không tính phiếu nhận về). */
function returnableLines(orderId: number) {
  const returned = new Map<number, number>();
  db.returns
    .filter((r) => r.orderId === orderId && r.status !== 'rejected' && !(r as { deliveryId?: number | null }).deliveryId)
    .flatMap((r) => r.items ?? [])
    .forEach((i) => returned.set(i.variantId, (returned.get(i.variantId) ?? 0) + i.qty));
  return db.orderLines
    .filter((l) => l.orderId === orderId)
    .map((l) => {
      const label = variantLabel(l.variantId);
      return { variantId: l.variantId, sku: label.sku, productName: label.productName, packLabel: label.packLabel, unitPrice: l.unitPrice, maxQty: l.qtyKhay - (returned.get(l.variantId) ?? 0) };
    })
    .filter((l) => l.maxQty > 0);
}

/** SL còn nhận về từ một đợt giao thất bại. */
function returnableLinesForDelivery(deliveryId: number) {
  const delivery = db.deliveries.find((d) => d.id === deliveryId);
  if (!delivery || delivery.state !== 'failed') fail(409, 'Chỉ nhận hàng về từ đợt giao thất bại');
  const returned = new Map<number, number>();
  db.returns
    .filter((r) => (r as { deliveryId?: number | null }).deliveryId === deliveryId && r.status !== 'rejected')
    .flatMap((r) => r.items ?? [])
    .forEach((i) => returned.set(i.variantId, (returned.get(i.variantId) ?? 0) + i.qty));

  const byVariant = new Map<number, { maxQty: number; unitPrice: number }>();
  for (const item of delivery.items ?? []) {
    const line = db.orderLines.find((l) => l.id === item.orderLineId);
    if (!line) continue;
    const remain = item.qtyKhay - (returned.get(line.variantId) ?? 0);
    if (remain <= 0) continue;
    const cur = byVariant.get(line.variantId);
    if (cur) cur.maxQty += remain;
    else byVariant.set(line.variantId, { maxQty: remain, unitPrice: line.unitPrice });
  }
  return [...byVariant.entries()].map(([variantId, v]) => {
    const label = variantLabel(variantId);
    return { variantId, sku: label.sku, productName: label.productName, packLabel: label.packLabel, unitPrice: v.unitPrice, maxQty: v.maxQty };
  });
}

mock('GET', '/returns/returnable-orders', ({ query }) => {
  const deliveryId = query.deliveryId != null && query.deliveryId !== '' ? Number(query.deliveryId) : null;
  if (deliveryId != null) {
    const delivery = db.deliveries.find((d) => d.id === deliveryId);
    if (!delivery) fail(404, 'Không tìm thấy đợt giao');
    const order = db.orders.find((o) => o.id === delivery.orderId);
    if (!order) return [];
    const lines = returnableLinesForDelivery(deliveryId);
    if (!lines.length) return [];
    return [{ id: order.id, code: order.code, customerName: customerName(order.customerId) ?? 'Khách lẻ', lines }];
  }
  const q = String(query.q ?? '').trim().toLowerCase();
  return db.orders
    .filter((o) => ['delivered', 'completed', 'partial'].includes(o.state))
    .sort((a, b) => b.placedAt.localeCompare(a.placedAt))
    .map((o) => ({ id: o.id, code: o.code, customerName: customerName(o.customerId) ?? 'Khách lẻ', lines: returnableLines(o.id) }))
    .filter((o) => o.lines.length > 0)
    .filter((o) => !q || matches(q, o.code, o.customerName))
    .slice(0, 100);
});

mock('GET', '/returns', ({ query }) => {
  const rows = db.returns.map(toItem).filter((r) => matches(query.q, r.code, r.orderCode, r.customerName)).filter((r) => !query.status || r.status === query.status).sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return paginate(rows, query);
});

mock('POST', '/returns', ({ body, userId }) => {
  const order = db.orders.find((o) => o.id === body.orderId);
  if (!order) fail(400, 'Vui lòng chọn đơn hàng');
  const deliveryId = body.deliveryId != null ? Number(body.deliveryId) : null;
  const lines = deliveryId != null ? returnableLinesForDelivery(deliveryId) : returnableLines(order.id);
  if (deliveryId != null) {
    const delivery = db.deliveries.find((d) => d.id === deliveryId);
    if (!delivery) fail(400, 'Không tìm thấy đợt giao');
    if (delivery.orderId !== order.id) fail(400, 'Đợt giao không thuộc đơn đã chọn');
  } else if (!['delivered', 'completed', 'partial'].includes(order.state)) {
    fail(409, 'Chỉ trả hàng khi đơn đã giao / giao một phần / hoàn tất');
  }
  const items = body.items.filter((i: { qty: number }) => i.qty > 0).map((i: { variantId: number; qty: number }) => {
    const line = lines.find((l) => l.variantId === i.variantId);
    if (!line || i.qty > line.maxQty) fail(409, 'Số lượng trả vượt quá số đã mua');
    return { variantId: i.variantId, lotId: null, qty: i.qty, amount: i.qty * line.unitPrice };
  });
  if (!items.length) fail(400, 'Phiếu trả phải có ít nhất một khay');
  const row = {
    id: nextId(db.returns),
    code: nextCode('TH', db.returns),
    orderId: order.id,
    deliveryId,
    customerId: order.customerId,
    reason: body.reason,
    amount: items.reduce((s: number, i: { amount: number }) => s + i.amount, 0),
    items,
    status: 'pending' as const,
    createdBy: userId,
    createdAt: new Date().toISOString(),
  };
  db.returns.push(row);
  addOrderEvent(
    order.id,
    'return',
    deliveryId == null ? `Tạo phiếu trả ${row.code}` : `Tạo phiếu nhận hàng về ${row.code} (đợt giao #${deliveryId})`,
    userId,
  );
  return toItem(row);
});

mock('GET', '/returns/:id/inspect-defaults', ({ params }) => {
  const row = db.returns.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy phiếu trả');
  const lines = (row.items ?? []).map((item) => {
    const out = [...db.ledger]
      .reverse()
      .find((e) => e.variantId === item.variantId && e.refType === 'sales_order_line' && (e.entryType === 'out' || e.entryType === 'hold' || e.entryType === 'release') && e.locationId != null);
    const stock = db.stock.filter((s) => s.variantId === item.variantId).sort((a, b) => b.lotId - a.lotId)[0];
    const locationId = out?.locationId ?? stock?.locationId ?? null;
    const loc = locationId == null ? null : db.locations.find((l) => l.id === locationId);
    const label = loc ? [locationCode(loc.id), loc.zone].filter(Boolean).join(' · ') : null;
    return {
      variantId: item.variantId,
      lotId: item.lotId ?? out?.lotId ?? stock?.lotId ?? null,
      shippedLocationId: locationId,
      shippedLocationLabel: label,
      quarantineLocationId: null,
      quarantineLocationLabel: null,
    };
  });
  return {
    quarantineLocationId: null,
    quarantineLabel: null,
    lines,
  };
});

const ALLOWED: Record<string, string[]> = { pending: ['received', 'rejected'], received: ['refunded'] };

function applyStockIn(variantId: number, locationId: number | null, qty: number, refId: number, note: string, userId: number | null) {
  let stock = locationId != null
    ? db.stock.filter((s) => s.variantId === variantId && s.locationId === locationId).sort((a, b) => b.lotId - a.lotId)[0]
    : undefined;
  if (!stock) stock = db.stock.filter((s) => s.variantId === variantId).sort((a, b) => b.lotId - a.lotId)[0];
  if (!stock) return;
  stock.qtyAvailable += qty;
  appendLedger({
    variantId,
    lotId: stock.lotId,
    locationId: stock.locationId,
    toLocationId: null,
    entryType: 'return',
    qtyDelta: qty,
    balanceAfter: stock.qtyAvailable,
    refType: 'sales_return',
    refId,
    note,
    createdBy: userId,
  });
}

mock('PATCH', '/returns/:id/status', ({ params, body, userId }) => {
  const row = db.returns.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy phiếu trả');
  if (!ALLOWED[row.status]?.includes(body.status)) fail(409, 'Không thể chuyển sang trạng thái này');
  row.status = body.status;
  if (body.status === 'received') {
    const inspections = Array.isArray(body.inspections) ? body.inspections : null;
    if (inspections?.length) {
      const remaining = new Map<number, number>();
      row.items?.forEach((item) => remaining.set(item.variantId, (remaining.get(item.variantId) ?? 0) + item.qty));
      for (const line of inspections) {
        if (line.qty <= 0) continue;
        const left = remaining.get(line.variantId) ?? 0;
        if (line.qty > left) fail(409, 'Số lượng nhận vượt quá phiếu trả');
        remaining.set(line.variantId, left - line.qty);
        applyStockIn(
          line.variantId,
          line.locationId ?? null,
          line.qty,
          row.id,
          `Nhập lại hàng trả — ${row.code}`,
          userId,
        );
      }
      if ([...remaining.values()].some((q) => q > 0)) fail(400, 'Chưa nhận đủ số lượng trên phiếu trả');
    } else {
      row.items?.forEach((item) => {
        applyStockIn(item.variantId, null, item.qty, row.id, `Nhập lại hàng trả — ${row.code}`, userId);
      });
    }
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
  if (row.orderId) addOrderEvent(row.orderId, 'return', `Phiếu trả ${row.code}: ${body.status === 'received' ? 'đã nhận hàng' : body.status === 'refunded' ? 'đã hoàn tiền' : 'đã hủy'}`, userId);
  audit(userId, 'update', 'sales_return', row.id, { status: body.status });
});
