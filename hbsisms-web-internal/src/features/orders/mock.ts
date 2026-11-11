import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import type { OrderState } from '@/shared/domain/enums';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, allocateOrder, audit, confirmOrder, creditCheck, createApproval, postDebtEntry, priceLines, releaseOrder, userName, variantLabel } from '@/shared/mock/services';

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

mock('PUT', '/orders/:id', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (!['pending', 'confirmed'].includes(order.state)) fail(409, 'Chỉ sửa được đơn ở trạng thái Chờ xử lý hoặc Đã xác nhận');
  if (['paid', 'partial'].includes(order.paymentStatus)) fail(409, 'Đơn đã thu tiền, không thể sửa');
  const customer = db.customers.find((c) => c.id === body.customerId);
  if (!customer) fail(400, 'Vui lòng chọn khách hàng');
  if (!customer.isActive) fail(409, 'Khách hàng đã bị khóa, không thể sửa đơn');
  if (body.paymentMethod === 'credit_net' && customer.customerType !== 'b2b') fail(400, 'Chỉ khách B2B được mua công nợ');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');

  const wasConfirmed = order.state === 'confirmed';
  const wasDebt = order.paymentStatus === 'debt';
  if (wasConfirmed) releaseOrder(order, userId);
  if (wasDebt && order.customerId) postDebtEntry(order.customerId, { orderId: order.id, entryType: 'adjust', amount: -order.total, note: `Sửa đơn ${order.code} — đảo nợ cũ` });

  const priced = priceLines(customer.id, body.lines);
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  Object.assign(order, {
    customerId: customer.id, orderType: body.orderType, channel: body.channel, paymentMethod: body.paymentMethod,
    paymentTermDays: body.paymentMethod === 'credit_net' ? customer.netDays : 0,
    fulfillmentMethod: body.fulfillmentMethod, shipAddressId: body.shipAddressId,
    subtotal, discountTotal: Number(body.discountTotal ?? 0), shippingFee: Number(body.shippingFee ?? 0), total,
    voucherCode: body.voucherCode, note: body.note, holdExpiresAt: null, allocationStatus: 'none' as const,
    ...creditCheck(customer.id, body.paymentMethod, total), updatedAt: now,
  });
  if (wasDebt) order.paymentStatus = 'unpaid';

  db.orderLines = db.orderLines.filter((l) => l.orderId !== order.id);
  priced.forEach(({ available: _available, shortfall: _shortfall, ...line }) => db.orderLines.push({ ...line, id: nextId(db.orderLines), orderId: order.id, outstandingQty: 0, note: null }));

  if (wasConfirmed) {
    if (order.paymentMethod === 'credit_net') {
      postDebtEntry(customer.id, { orderId: order.id, entryType: 'charge', amount: order.total, dueDate: new Date(Date.now() + order.paymentTermDays * 86400000).toISOString().slice(0, 10), note: `Đơn ${order.code}` });
      order.paymentStatus = 'debt';
    }
    allocateOrder(order, userId);
    addOrderEvent(order.id, 'updated', `Sửa đơn — giữ lại hàng (${order.allocationStatus})`, userId);
  } else if (order.isOverLimit && !order.approvedBy) {
    const pending = db.approvals.find((a) => a.refType === 'sales_order' && a.refId === order.id && a.status === 'pending');
    if (pending) pending.title = `Đơn ${order.code} của ${customer.name} vượt hạn mức ${order.overLimitAmount.toLocaleString('vi-VN')} ₫`;
    else createApproval('over_limit_order', 'sales_order', order.id, `Đơn ${order.code} của ${customer.name} vượt hạn mức ${order.overLimitAmount.toLocaleString('vi-VN')} ₫`, userId);
    addOrderEvent(order.id, 'updated', 'Sửa đơn (vẫn chờ duyệt vượt hạn mức)', userId);
  } else if (!order.isOverLimit) {
    const pending = db.approvals.find((a) => a.refType === 'sales_order' && a.refId === order.id && a.status === 'pending');
    if (pending) { pending.status = 'approved'; pending.note = 'Đơn sau khi sửa không còn vượt hạn mức'; pending.decidedAt = now; }
    addOrderEvent(order.id, 'updated', 'Sửa đơn', userId);
    confirmOrder(order, userId);
  } else {
    addOrderEvent(order.id, 'updated', 'Sửa đơn', userId);
  }
  audit(userId, 'update', 'sales_order', order.id, { total });
  return toDetail(order.id);
});

mock('GET', '/orders/:id/receipt', ({ params, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (!['paid', 'debt'].includes(order.paymentStatus)) fail(409, 'Chỉ in biên lai khi đã thanh toán đủ hoặc đã ghi công nợ');
  const customer = db.customers.find((c) => c.id === order.customerId);
  const paymentMethod = order.paymentMethod === 'cash' || order.paymentMethod === 'credit_net' ? order.paymentMethod : 'bank_transfer';
  audit(userId, 'print_receipt', 'sales_order', order.id, { code: order.code });
  return {
    orderId: order.id,
    code: order.code,
    placedAt: order.placedAt,
    customerName: customer?.name ?? 'Khách lẻ',
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
    debtAfter: order.paymentMethod === 'credit_net' ? (customer?.currentDebt ?? null) : null,
  };
});

mock('POST', '/orders', ({ body, userId }) => {
  const customer = db.customers.find((c) => c.id === body.customerId);
  if (!customer) fail(400, 'Vui lòng chọn khách hàng');
  if (body.paymentMethod === 'credit_net' && customer.customerType !== 'b2b') fail(400, 'Chỉ khách B2B được mua công nợ');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');
  const priced = priceLines(customer.id, body.lines);
  const needed = new Map<number, number>();
  const available = new Map<number, number>();
  for (const line of priced) {
    needed.set(line.variantId, (needed.get(line.variantId) ?? 0) + line.qtyKhay);
    if (!available.has(line.variantId)) available.set(line.variantId, line.available);
  }
  const shortages: string[] = [];
  for (const [variantId, qty] of needed) {
    const avail = available.get(variantId) ?? 0;
    if (qty > avail) {
      const label = variantLabel(variantId);
      shortages.push(`${label.productName} · ${label.packLabel} chỉ còn ${avail} khay (cần ${qty})`);
    }
  }
  if (shortages.length) fail(409, `Một số sản phẩm không đủ hàng: ${shortages.join('; ')}`);
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
  priced.forEach(({ available: _available, shortfall: _shortfall, ...line }) => db.orderLines.push({ ...line, id: nextId(db.orderLines), orderId: id, outstandingQty: 0, note: null }));
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

mock('POST', '/orders/:id/complete-at-counter', ({ params, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (order.state === 'cancelled') fail(409, 'Đơn đã hủy');
  if (order.state === 'completed') return toDetail(order.id);
  if (!['paid', 'debt'].includes(order.paymentStatus)) fail(409, 'Đơn chưa thu đủ — không thể hoàn tất tại quầy');
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
