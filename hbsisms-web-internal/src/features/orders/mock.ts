import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import type { OrderState } from '@/shared/domain/enums';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, allocateOrder, audit, confirmOrder, postDebtEntry, priceLines, releaseOrder, userName, variantLabel } from '@/shared/mock/services';

const MANUAL_NEXT: Partial<Record<OrderState, OrderState[]>> = {
  pending: ['confirmed'],
  confirmed: ['preparing'],
  preparing: ['to_ship'],
  delivered: ['completed'],
};

const toItem = (order: (typeof db.orders)[number]) => {
  const customer = db.customers.find((c) => c.id === order.customerId);
  return {
    ...order,
    customerName: order.customerName ?? customer?.name ?? 'Khách vãng lai',
    customerType: customer?.customerType ?? null,
  };
};

function toDetail(id: number) {
  const order = db.orders.find((o) => o.id === id);
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  const deliveries = db.deliveries.filter((d) => d.orderId === id);
  const delivered = new Map<number, number>();
  deliveries.filter((d) => d.state === 'delivered').forEach((d) => d.items?.forEach((item) => delivered.set(item.orderLineId, (delivered.get(item.orderLineId) ?? 0) + item.qtyKhay)));
  const payments = db.payments.filter((p) => p.orderId === id && p.status === 'success');
  const hasDelivered = deliveries.some((d) => d.state === 'delivered');
  const blockedByLimit = order.isOverLimit && !order.approvedBy;
  const rawNext = blockedByLimit ? [] : (MANUAL_NEXT[order.state] ?? []);
  const nextStates = rawNext.filter((state) => {
    if (state !== 'preparing') return true;
    return order.paymentStatus === 'paid' && order.allocationStatus === 'full';
  });
  return {
    ...toItem(order),
    customer: db.customers.find((c) => c.id === order.customerId) ?? null,
    address: db.addresses.find((a) => a.id === order.shipAddressId) ?? null,
    lines: db.orderLines.filter((l) => l.orderId === id).map((l) => ({ ...l, ...variantLabel(l.variantId), deliveredKhay: delivered.get(l.id) ?? (deliveries.length === 0 && ['delivered', 'completed'].includes(order.state) ? l.qtyKhay : 0) })),
    deliveries: deliveries.map((d) => ({ ...d, assigneeName: userName(d.assignedTo) })),
    payments,
    events: db.orderEvents.filter((e) => e.orderId === id).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((e) => ({ ...e, actorName: userName(e.createdBy) })),
    paidAmount: payments.reduce((sum, p) => sum + (p.direction === 'in' ? p.amount : -p.amount), 0),
    sourceQuoteCode: db.quotes.find((q) => q.id === order.sourceQuoteId)?.code ?? null,
    placedByName: userName(order.placedBy),
    approvedByName: userName(order.approvedBy),
    nextStates,
    canCancel: !hasDelivered && !['delivered', 'completed', 'cancelled', 'partial'].includes(order.state),
  };
}

mock('GET', '/orders', ({ query }) => {
  const placedFrom = query.placedFrom ? String(query.placedFrom) : '';
  const placedTo = query.placedTo ? String(query.placedTo) : '';
  const customerId = query.customerId ? Number(query.customerId) : null;
  const rows = db.orders
    .filter((o) => matches(query.q, o.code, toItem(o).customerName))
    .filter((o) => !query.state || o.state === query.state)
    .filter((o) => !query.orderType || o.orderType === query.orderType)
    .filter((o) => !query.paymentStatus || o.paymentStatus === query.paymentStatus)
    .filter((o) => !query.channel || o.channel === query.channel)
    .filter((o) => !query.allocationStatus || o.allocationStatus === query.allocationStatus)
    .filter((o) => customerId == null || Number.isNaN(customerId) || o.customerId === customerId)
    .filter((o) => !placedFrom || o.placedAt.slice(0, 10) >= placedFrom)
    .filter((o) => !placedTo || o.placedAt.slice(0, 10) <= placedTo)
    .sort((a, b) => b.placedAt.localeCompare(a.placedAt));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/orders/:id', ({ params }) => toDetail(Number(params.id)));

mock('PUT', '/orders/:id', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (!['pending', 'confirmed'].includes(order.state)) fail(409, 'Chỉ sửa được đơn ở trạng thái Chờ xử lý hoặc Đã xác nhận');
  if (['paid', 'partial'].includes(order.paymentStatus)) fail(409, 'Đơn đã thu tiền, không thể sửa');
  if (body.paymentMethod === 'credit_net') fail(409, 'Không bán nợ — mọi đơn phải thanh toán đủ trước khi chuẩn bị');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');

  const customer = body.customerId == null ? null : db.customers.find((c) => c.id === body.customerId);
  if (body.customerId != null && !customer) fail(400, 'Vui lòng chọn khách hàng');
  if (customer && !customer.isActive) fail(409, 'Khách hàng đã bị khóa, không thể sửa đơn');
  if (body.shipAddressId != null && !customer) fail(400, 'Khách vãng lai không gắn địa chỉ hồ sơ — chọn giao nhận phù hợp');
  const customerName = customer?.name ?? (String(body.customerName ?? '').trim() || 'Khách vãng lai');

  const wasConfirmed = order.state === 'confirmed';
  const wasDebt = order.paymentStatus === 'debt';
  if (wasConfirmed) releaseOrder(order, userId);
  if (wasDebt && order.customerId) postDebtEntry(order.customerId, { orderId: order.id, entryType: 'adjust', amount: -order.total, note: `Sửa đơn ${order.code} — đảo nợ cũ` });

  const priced = priceLines(customer?.id ?? null, body.lines);
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  Object.assign(order, {
    customerId: customer?.id ?? null,
    customerName,
    orderType: body.orderType,
    channel: body.channel,
    paymentMethod: body.paymentMethod,
    paymentTermDays: 0,
    fulfillmentMethod: body.fulfillmentMethod,
    shipAddressId: body.shipAddressId,
    subtotal,
    discountTotal: Number(body.discountTotal ?? 0),
    shippingFee: Number(body.shippingFee ?? 0),
    total,
    voucherCode: body.voucherCode,
    note: body.note,
    holdExpiresAt: null,
    allocationStatus: 'none' as const,
    isOverLimit: false,
    overLimitAmount: 0,
    updatedAt: now,
  });
  if (wasDebt) order.paymentStatus = 'unpaid';

  db.orderLines = db.orderLines.filter((l) => l.orderId !== order.id);
  priced.forEach(({ available: _available, shortfall: _shortfall, ...line }) => db.orderLines.push({ ...line, id: nextId(db.orderLines), orderId: order.id, outstandingQty: 0, note: null }));

  if (wasConfirmed) {
    allocateOrder(order, userId);
    addOrderEvent(order.id, 'updated', `Sửa đơn — giữ lại hàng (${order.allocationStatus})`, userId);
  } else {
    const pending = db.approvals.find((a) => a.refType === 'sales_order' && a.refId === order.id && a.status === 'pending');
    if (pending) { pending.status = 'approved'; pending.note = 'Đơn sau khi sửa — không còn đường bán nợ'; pending.decidedAt = now; }
    addOrderEvent(order.id, 'updated', 'Sửa đơn', userId);
    confirmOrder(order, userId);
  }
  audit(userId, 'update', 'sales_order', order.id, { total });
  return toDetail(order.id);
});

mock('GET', '/orders/:id/receipt', ({ params, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (order.paymentStatus !== 'paid') fail(409, 'Chỉ in biên lai khi đã thanh toán đủ');
  const customer = db.customers.find((c) => c.id === order.customerId);
  const paymentMethod = order.paymentMethod === 'cash' || order.paymentMethod === 'credit_net' ? order.paymentMethod : 'bank_transfer';
  audit(userId, 'print_receipt', 'sales_order', order.id, { code: order.code });
  return {
    orderId: order.id,
    code: order.code,
    placedAt: order.placedAt,
    customerName: order.customerName ?? customer?.name ?? 'Khách vãng lai',
    cashierName: userName(order.placedBy) ?? '—',
    paymentMethod,
    lines: db.orderLines.filter((l) => l.orderId === order.id).map((l) => {
      const label = variantLabel(l.variantId);
      return { variantId: l.variantId, productName: label.productName, packLabel: label.packLabel, qtyLabel: `${l.qty} ${l.unit === 'case' ? 'thùng' : 'khay'}`, unitPrice: l.unitPrice, lineTotal: l.lineTotal };
    }),
    subtotal: order.subtotal,
    discountTotal: order.discountTotal,
    total: order.total,
    tendered: null,
    change: null,
    debtAfter: null,
  };
});

mock('POST', '/orders', ({ body, userId }) => {
  if (body.paymentMethod === 'credit_net') fail(409, 'Không bán nợ — mọi đơn phải thanh toán đủ trước khi chuẩn bị');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');
  const customer = body.customerId == null ? null : db.customers.find((c) => c.id === body.customerId);
  if (body.customerId != null && !customer) fail(400, 'Vui lòng chọn khách hàng');
  if (customer && !customer.isActive) fail(409, 'Khách hàng đã bị khóa, không thể tạo đơn');
  if (body.shipAddressId != null && !customer) fail(400, 'Khách vãng lai không gắn địa chỉ hồ sơ — chọn giao nhận phù hợp');
  const customerName = customer?.name ?? (String(body.customerName ?? '').trim() || 'Khách vãng lai');
  const priced = priceLines(customer?.id ?? null, body.lines);
  const short = priced.some((l) => l.shortfall > 0);
  if (short) fail(409, 'Không đủ hàng khả dụng để giữ đủ số lượng — không thể xác nhận đơn');
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  const id = nextId(db.orders);
  const order = {
    id, code: nextCode('SO-2026', db.orders), customerId: customer?.id ?? null, customerName, orderType: body.orderType, channel: body.channel, sourceQuoteId: body.sourceQuoteId ?? null, paymentMethod: body.paymentMethod,
    paymentTermDays: 0, paymentStatus: 'unpaid' as const, allocationStatus: 'none' as const, fulfillmentMethod: body.fulfillmentMethod,
    shipAddressId: body.shipAddressId, state: 'pending' as const, subtotal, discountTotal: Number(body.discountTotal ?? 0), shippingFee: Number(body.shippingFee ?? 0), total, voucherCode: body.voucherCode,
    isOverLimit: false, overLimitAmount: 0, approvedBy: null, placedBy: userId, placedAt: now, holdExpiresAt: null, note: body.note, createdAt: now, updatedAt: now,
  };
  db.orders.push(order);
  priced.forEach(({ available: _available, shortfall: _shortfall, ...line }) => db.orderLines.push({ ...line, id: nextId(db.orderLines), orderId: id, outstandingQty: 0, note: null }));
  addOrderEvent(id, 'created', 'Tạo đơn', userId);
  audit(userId, 'create', 'sales_order', id, { total });
  confirmOrder(db.orders[db.orders.length - 1], userId);
  return toDetail(id);
});

const stateLabels: Partial<Record<OrderState, string>> = { confirmed: 'Xác nhận đơn', preparing: 'Bắt đầu chuẩn bị hàng', to_ship: 'Hàng sẵn sàng, chờ giao', completed: 'Hoàn tất đơn' };

mock('POST', '/orders/:id/transition', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (order.isOverLimit && !order.approvedBy) fail(409, 'Đơn vượt hạn mức, cần Chủ cửa hàng duyệt trước');
  if (!MANUAL_NEXT[order.state]?.includes(body.state)) fail(409, 'Không thể chuyển sang trạng thái này');
  if (body.state === 'confirmed') {
    confirmOrder(order, userId);
  } else {
    if (body.state === 'preparing') {
      if (order.paymentStatus !== 'paid') fail(409, 'Chỉ chuẩn bị khi đã thanh toán đủ');
      if (order.allocationStatus !== 'full') fail(409, 'Chưa phân bổ đủ hàng — không thể chuẩn bị');
    }
    order.state = body.state;
    addOrderEvent(order.id, body.state, stateLabels[body.state as OrderState] ?? body.state, userId);
  }
  order.updatedAt = new Date().toISOString();
});

mock('POST', '/orders/:id/complete-at-counter', ({ params, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (order.state === 'cancelled') fail(409, 'Đơn đã hủy');
  if (order.state === 'completed') return toDetail(order.id);
  if (order.paymentStatus !== 'paid') fail(409, 'Đơn chưa thu đủ — không thể hoàn tất tại quầy');
  if (order.state === 'pending') fail(409, 'Đơn chưa xác nhận');
  order.state = 'completed';
  order.allocationStatus = 'full';
  order.holdExpiresAt = null;
  order.updatedAt = new Date().toISOString();
  addOrderEvent(order.id, 'completed_counter', 'Hoàn tất quầy — xuất kho theo hold', userId);
  audit(userId, 'complete_counter', 'sales_order', order.id, {});
  return toDetail(order.id);
});

mock('POST', '/orders/:id/cancel', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (['delivered', 'completed', 'cancelled', 'partial'].includes(order.state)) fail(409, 'Đơn đã giao hoặc đã hủy, không thể hủy');
  if (!String(body.reason ?? '').trim()) fail(400, 'Vui lòng nhập lý do hủy');
  const wasCharged = order.paymentStatus === 'debt';
  const hadPaid = order.paymentStatus === 'paid' || order.paymentStatus === 'partial';
  releaseOrder(order, userId);
  if (wasCharged && order.customerId) postDebtEntry(order.customerId, { orderId: order.id, entryType: 'adjust', amount: -order.total, note: `Hủy đơn ${order.code}` });
  order.state = 'cancelled';
  order.paymentStatus = hadPaid ? 'refund_pending' : 'unpaid';
  order.updatedAt = new Date().toISOString();
  db.deliveries.filter((d) => d.orderId === order.id && d.state !== 'delivered').forEach((d) => (d.state = 'failed'));
  db.approvals.filter((a) => a.refType === 'sales_order' && a.refId === order.id && a.status === 'pending').forEach((a) => { a.status = 'rejected'; a.note = 'Đơn đã hủy'; a.decidedAt = new Date().toISOString(); });
  addOrderEvent(
    order.id,
    'cancelled',
    hadPaid ? `Hủy đơn (đã thu tiền — chờ hoàn tiền): ${body.reason}` : `Hủy đơn: ${body.reason}`,
    userId,
  );
  audit(userId, 'cancel', 'sales_order', order.id, { reason: body.reason, paymentStatus: order.paymentStatus });
});
