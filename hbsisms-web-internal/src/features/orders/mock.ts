import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import type { OrderState } from '@/shared/domain/enums';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, audit, confirmOrder, creditCheck, createApproval, postDebtEntry, priceLines, releaseOrder, userName, variantLabel } from '@/shared/mock/services';

const MANUAL_NEXT: Partial<Record<OrderState, OrderState[]>> = {
  pending: ['confirmed'],
  confirmed: ['preparing'],
  preparing: ['to_ship'],
  delivered: ['completed'],
};

const toItem = (order: (typeof db.orders)[number]) => {
  const customer = db.customers.find((c) => c.id === order.customerId);
  return { ...order, customerName: customer?.name ?? 'Khách lẻ', customerType: customer?.customerType ?? null };
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
    nextStates: blockedByLimit ? [] : (MANUAL_NEXT[order.state] ?? []),
    canCancel: !hasDelivered && !['delivered', 'completed', 'cancelled', 'partial'].includes(order.state),
  };
}

mock('GET', '/orders', ({ query }) => {
  const rows = db.orders
    .filter((o) => matches(query.q, o.code, toItem(o).customerName))
    .filter((o) => !query.state || o.state === query.state)
    .filter((o) => !query.orderType || o.orderType === query.orderType)
    .filter((o) => !query.paymentStatus || o.paymentStatus === query.paymentStatus)
    .sort((a, b) => b.placedAt.localeCompare(a.placedAt));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/orders/:id', ({ params }) => toDetail(Number(params.id)));

mock('POST', '/orders', ({ body, userId }) => {
  const customer = db.customers.find((c) => c.id === body.customerId);
  if (!customer) fail(400, 'Vui lòng chọn khách hàng');
  if (body.paymentMethod === 'credit_net' && customer.customerType !== 'b2b') fail(400, 'Chỉ khách B2B được mua công nợ');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');
  const priced = priceLines(customer.id, body.lines);
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  const id = nextId(db.orders);
  const order = {
    id, code: nextCode('SO-2026', db.orders), customerId: customer.id, orderType: body.orderType, channel: body.channel, sourceQuoteId: body.sourceQuoteId ?? null, paymentMethod: body.paymentMethod,
    paymentTermDays: body.paymentMethod === 'credit_net' ? customer.netDays : 0, paymentStatus: 'unpaid' as const, allocationStatus: 'none' as const, fulfillmentMethod: body.fulfillmentMethod,
    shipAddressId: body.shipAddressId, state: 'pending' as const, subtotal, discountTotal: Number(body.discountTotal ?? 0), shippingFee: Number(body.shippingFee ?? 0), total, voucherCode: body.voucherCode,
    ...creditCheck(customer.id, body.paymentMethod, total), approvedBy: null, placedBy: userId, placedAt: now, holdExpiresAt: null, note: body.note, createdAt: now, updatedAt: now,
  };
  db.orders.push(order);
  priced.forEach(({ available: _available, ...line }) => db.orderLines.push({ ...line, id: nextId(db.orderLines), orderId: id, outstandingQty: 0, note: null }));
  addOrderEvent(id, 'created', 'Tạo đơn', userId);
  audit(userId, 'create', 'sales_order', id, { total });
  if (order.isOverLimit) {
    createApproval('over_limit_order', 'sales_order', id, `Đơn ${order.code} của ${customer.name} vượt hạn mức ${order.overLimitAmount.toLocaleString('vi-VN')} ₫`, userId);
    addOrderEvent(id, 'approval', 'Đơn vượt hạn mức, chờ Chủ cửa hàng duyệt', userId);
  } else {
    confirmOrder(db.orders[db.orders.length - 1], userId);
  }
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
    order.state = body.state;
    addOrderEvent(order.id, body.state, stateLabels[body.state as OrderState] ?? body.state, userId);
  }
  order.updatedAt = new Date().toISOString();
});

mock('POST', '/orders/:id/cancel', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (['delivered', 'completed', 'cancelled', 'partial'].includes(order.state)) fail(409, 'Đơn đã giao hoặc đã hủy, không thể hủy');
  const wasCharged = order.paymentStatus === 'debt';
  releaseOrder(order, userId);
  if (wasCharged && order.customerId) postDebtEntry(order.customerId, { orderId: order.id, entryType: 'adjust', amount: -order.total, note: `Hủy đơn ${order.code}` });
  order.state = 'cancelled';
  order.paymentStatus = 'unpaid';
  order.updatedAt = new Date().toISOString();
  db.deliveries.filter((d) => d.orderId === order.id && d.state !== 'delivered').forEach((d) => (d.state = 'failed'));
  db.approvals.filter((a) => a.refType === 'sales_order' && a.refId === order.id && a.status === 'pending').forEach((a) => { a.status = 'rejected'; a.note = 'Đơn đã hủy'; a.decidedAt = new Date().toISOString(); });
  addOrderEvent(order.id, 'cancelled', `Hủy đơn: ${body.reason}`, userId);
  audit(userId, 'cancel', 'sales_order', order.id, { reason: body.reason });
});
