import { normalizeVnPhone } from '@/features/customers/customerValidation';
import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import type { OrderState } from '@/shared/domain/enums';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, allocateOrder, audit, configInt, confirmOrder, postDebtEntry, priceLines, releaseOrder, userName, variantLabel } from '@/shared/mock/services';
import { isCollectOnDelivery, normalizePaymentForFulfillment } from './cod';

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
  const allPayments = db.payments.filter((p) => p.orderId === id);
  const payments = allPayments.filter((p) => p.status === 'success' || p.status === 'pending');
  const paidAmount = allPayments
    .filter((p) => p.status === 'success')
    .reduce((sum, p) => sum + (p.direction === 'in' ? p.amount : -p.amount), 0);
  const hasDelivered = deliveries.some((d) => d.state === 'delivered');
  const blockedByLimit = order.isOverLimit && !order.approvedBy;
  const rawNext = blockedByLimit ? [] : (MANUAL_NEXT[order.state] ?? []);
  const nextStates = rawNext.filter((state) => {
    if (state !== 'preparing') return true;
    if (order.allocationStatus !== 'full') return false;
    return isCollectOnDelivery(order.paymentMethod, order.fulfillmentMethod) || order.paymentStatus === 'paid';
  });
  return {
    ...toItem(order),
    customer: db.customers.find((c) => c.id === order.customerId) ?? null,
    address: db.addresses.find((a) => a.id === order.shipAddressId) ?? null,
    lines: db.orderLines.filter((l) => l.orderId === id).map((l) => ({ ...l, ...variantLabel(l.variantId), deliveredKhay: delivered.get(l.id) ?? (deliveries.length === 0 && ['delivered', 'completed'].includes(order.state) ? l.qtyKhay : 0) })),
    deliveries: deliveries.map((d) => ({ ...d, assigneeName: userName(d.assignedTo) })),
    payments,
    events: db.orderEvents.filter((e) => e.orderId === id).sort((a, b) => b.createdAt.localeCompare(a.createdAt)).map((e) => ({ ...e, actorName: userName(e.createdBy) })),
    returns: db.returns.filter((r) => r.orderId === id),
    auditTrail: db.auditLog
      .filter((a) => a.entityType === 'sales_order' && a.entityId === id)
      .sort((a, b) => b.createdAt.localeCompare(a.createdAt))
      .map((a) => ({
        id: a.id,
        action: a.action,
        userId: a.userId,
        actorName: userName(a.userId),
        createdAt: a.createdAt,
        detail: a.detail,
      })),
    paidAmount,
    sourceQuoteCode: db.quotes.find((q) => q.id === order.sourceQuoteId)?.code ?? null,
    placedByName: userName(order.placedBy),
    approvedByName: userName(order.approvedBy),
    nextStates,
    canCancel: !hasDelivered && !['delivered', 'completed', 'cancelled', 'partial'].includes(order.state),
  };
}

function orderMatchesSharedFilters(o: (typeof db.orders)[number], query: Record<string, unknown>) {
  const placedFrom = query.placedFrom ? String(query.placedFrom) : '';
  const placedTo = query.placedTo ? String(query.placedTo) : '';
  const deliveryFrom = query.deliveryFrom ? String(query.deliveryFrom) : '';
  const deliveryTo = query.deliveryTo ? String(query.deliveryTo) : '';
  const timeWindow = query.timeWindow ? String(query.timeWindow) : '';
  const customerId = query.customerId != null ? Number(query.customerId) : null;
  const placedBy = query.placedBy != null ? Number(query.placedBy) : null;
  const customer = db.customers.find((c) => c.id === o.customerId);
  if (!matches(query.q, o.code, toItem(o).customerName, customer?.name, customer?.code, customer?.phone, customer?.taxCode)) {
    return false;
  }
  if (query.orderType && o.orderType !== query.orderType) return false;
  if (query.paymentStatus && o.paymentStatus !== query.paymentStatus) return false;
  if (query.channel && o.channel !== query.channel) return false;
  if (query.allocationStatus && o.allocationStatus !== query.allocationStatus) return false;
  if (customerId != null && !Number.isNaN(customerId) && o.customerId !== customerId) return false;
  if (placedBy != null && !Number.isNaN(placedBy) && o.placedBy !== placedBy) return false;
  if (query.fulfillmentMethod && o.fulfillmentMethod !== query.fulfillmentMethod) return false;
  if (placedFrom && o.placedAt.slice(0, 10) < placedFrom) return false;
  if (placedTo && o.placedAt.slice(0, 10) > placedTo) return false;
  if (deliveryFrom || deliveryTo || timeWindow) {
    const deliveries = db.deliveries.filter((d) => d.orderId === o.id);
    const hit = deliveries.some((d) => {
      const day = d.scheduledDate ?? '';
      if (deliveryFrom && (!day || day < deliveryFrom)) return false;
      if (deliveryTo && (!day || day > deliveryTo)) return false;
      if (timeWindow && d.timeWindow !== timeWindow) return false;
      return true;
    });
    if (!hit) return false;
  }
  return true;
}

function orderInGroup(o: (typeof db.orders)[number], group: string) {
  const deliveries = db.deliveries.filter((d) => d.orderId === o.id);
  switch (group) {
    case 'awaiting_payment':
      return (o.paymentStatus === 'unpaid' || o.paymentStatus === 'waiting') && o.state !== 'cancelled';
    case 'awaiting_prepare':
      return o.state === 'confirmed' && (o.paymentStatus === 'paid' || o.paymentStatus === 'debt') && o.allocationStatus === 'full';
    case 'preparing':
      return o.state === 'preparing';
    case 'ready':
      return o.state === 'to_ship';
    case 'delivering':
      return deliveries.some((d) => d.state === 'going');
    case 'delivery_failed':
      return o.state !== 'completed' && o.state !== 'cancelled'
        && deliveries.some((d) => d.state === 'failed')
        && !deliveries.some((d) => d.state === 'ready' || d.state === 'going');
    case 'needs_refund':
      return o.paymentStatus === 'refund_pending';
    case 'completed':
      return o.state === 'completed';
    case 'cancelled':
      return o.state === 'cancelled';
    default:
      return true;
  }
}

mock('GET', '/orders', ({ query }) => {
  const rows = db.orders
    .filter((o) => orderMatchesSharedFilters(o, query))
    .filter((o) => !query.state || o.state === query.state)
    .filter((o) => !query.group || orderInGroup(o, String(query.group)))
    .sort((a, b) => b.placedAt.localeCompare(a.placedAt));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/orders/counts', ({ query }) => {
  const base = db.orders.filter((o) => orderMatchesSharedFilters(o, query));
  const count = (group: string) => base.filter((o) => orderInGroup(o, group)).length;
  return {
    awaitingPayment: count('awaiting_payment'),
    awaitingPrepare: count('awaiting_prepare'),
    preparing: count('preparing'),
    ready: count('ready'),
    delivering: count('delivering'),
    deliveryFailed: count('delivery_failed'),
    needsRefund: count('needs_refund'),
    completed: count('completed'),
    cancelled: count('cancelled'),
  };
});

/** Inter 3 dòng 11 — việc cần Chủ (mock). */
mock('GET', '/orders/owner-inbox', () => {
  const items: {
    type: string;
    title: string;
    orderId: number | null;
    orderCode: string | null;
    refId: number | null;
    createdAt: string;
    link: string;
  }[] = [];
  const lateOrderIds = new Set<number>();

  for (const task of db.approvals.filter((a) => a.status === 'pending' && a.refType === 'sales_order')) {
    const order = db.orders.find((o) => o.id === task.refId);
    items.push({
      type: 'discount_or_limit_approval',
      title: task.title,
      orderId: task.refId,
      orderCode: order?.code ?? null,
      refId: task.id,
      createdAt: task.createdAt,
      link: `/orders/${task.refId}`,
    });
  }

  for (const p of db.payments.filter((p) => p.needsRefund)) {
    if (p.orderId == null) continue;
    lateOrderIds.add(p.orderId);
    const order = db.orders.find((o) => o.id === p.orderId);
    items.push({
      type: 'late_payos_refund',
      title: `Tiền về sau khi đơn hết hạn — cần hoàn · ${order?.code ?? p.orderId}`,
      orderId: p.orderId,
      orderCode: order?.code ?? null,
      refId: p.id,
      createdAt: p.paidAt ?? order?.updatedAt ?? order?.placedAt ?? taskNow(),
      link: `/orders/${p.orderId}`,
    });
  }

  for (const o of db.orders.filter((o) => o.paymentStatus === 'refund_pending')) {
    if (lateOrderIds.has(o.id)) continue;
    items.push({
      type: 'needs_refund',
      title: `Cần hoàn tiền · ${o.code}`,
      orderId: o.id,
      orderCode: o.code,
      refId: o.id,
      createdAt: o.updatedAt,
      link: `/orders/${o.id}`,
    });
  }

  const mismatchOrders = new Set<number>();
  for (const o of db.orders.filter((o) => o.paymentStatus === 'diff')) {
    mismatchOrders.add(o.id);
    items.push({
      type: 'payment_mismatch',
      title: `Thanh toán lệch tiền · ${o.code}`,
      orderId: o.id,
      orderCode: o.code,
      refId: o.id,
      createdAt: o.updatedAt,
      link: `/orders/${o.id}`,
    });
  }
  for (const p of db.payments.filter((p) => p.reconcileFlag && p.reconcileFlag !== 'none')) {
    if (p.orderId == null || mismatchOrders.has(p.orderId)) continue;
    mismatchOrders.add(p.orderId);
    const order = db.orders.find((o) => o.id === p.orderId);
    items.push({
      type: 'payment_mismatch',
      title: `Thanh toán lệch tiền · ${order?.code ?? p.orderId}`,
      orderId: p.orderId,
      orderCode: order?.code ?? null,
      refId: p.id,
      createdAt: p.paidAt ?? order?.updatedAt ?? taskNow(),
      link: `/orders/${p.orderId}`,
    });
  }

  for (const o of db.orders) {
    if (!orderInGroup(o, 'delivery_failed')) continue;
    const failed = db.deliveries.find((d) => d.orderId === o.id && d.state === 'failed');
    items.push({
      type: 'delivery_failed_open',
      title: `Giao thất bại chưa xử lý · ${o.code}`,
      orderId: o.id,
      orderCode: o.code,
      refId: failed?.id ?? o.id,
      createdAt: failed?.deliveredAt ?? o.updatedAt,
      link: `/orders/${o.id}`,
    });
  }

  for (const r of db.returns.filter((r) => r.status === 'pending')) {
    const order = r.orderId == null ? undefined : db.orders.find((o) => o.id === r.orderId);
    items.push({
      type: 'return_pending_inspect',
      title: `Phiếu trả chờ kiểm · ${r.code}`,
      orderId: r.orderId,
      orderCode: order?.code ?? null,
      refId: r.id,
      createdAt: r.createdAt,
      link: r.orderId == null ? '/returns' : `/orders/${r.orderId}`,
    });
  }

  items.sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return { items };
});

function taskNow() {
  return new Date().toISOString();
}

mock('GET', '/orders/:id', ({ params }) => toDetail(Number(params.id)));

function phoneKey(raw: string | null | undefined) {
  return normalizeVnPhone(raw);
}

function resolveCustomerForOrder(body: Record<string, unknown>) {
  if (body.customerId != null) {
    const customer = db.customers.find((c) => c.id === Number(body.customerId));
    if (!customer) fail(400, 'Vui lòng chọn khách hàng');
    if (!customer.isActive) fail(409, 'Khách hàng đã bị khóa, không thể tạo đơn');
    return customer;
  }

  const phone = phoneKey(body.phone == null ? null : String(body.phone));
  if (!phone || !/^0\d{9}$/.test(phone)) fail(400, 'Số điện thoại khách là bắt buộc');
  const name = String(body.customerName ?? '').trim() || 'Khách vãng lai';
  const now = new Date().toISOString();

  let customer = db.customers.find((c) => phoneKey(c.phone) === phone && c.accountUserId == null)
    ?? db.customers.find((c) => phoneKey(c.phone) === phone);
  if (customer) {
    if (!customer.isActive) fail(409, 'Khách hàng đã bị khóa, không thể tạo đơn');
    if (customer.accountUserId == null && customer.name !== name) {
      customer.name = name;
      customer.updatedAt = now;
    }
    return customer;
  }

  customer = {
    id: nextId(db.customers),
    code: nextCode('KH', db.customers.filter((c) => c.customerType === 'b2c')),
    customerType: 'b2c',
    name,
    email: null,
    phone,
    taxCode: null,
    contactName: null,
    contactPhone: null,
    accountUserId: null,
    creditLimit: 0,
    netDays: 0,
    currentDebt: 0,
    isActive: true,
    note: null,
    createdAt: now,
    updatedAt: now,
  };
  db.customers.push(customer);
  return customer;
}

function resolveShipAddressId(customer: (typeof db.customers)[number], body: Record<string, unknown>) {
  const needsAddress = body.fulfillmentMethod !== 'pickup';
  if (body.shipAddressId != null) {
    const address = db.addresses.find((a) => a.id === Number(body.shipAddressId) && a.customerId === customer.id);
    if (!address) fail(400, 'Địa chỉ giao hàng không hợp lệ');
    return address.id;
  }

  const ship = body.shipAddress as Record<string, unknown> | null | undefined;
  if (ship) {
    const line1 = String(ship.line1 ?? '').trim();
    if (!line1) fail(400, 'Địa chỉ là bắt buộc');
    const distanceKm = ship.distanceKm === null || ship.distanceKm === undefined || ship.distanceKm === ''
      ? null
      : Number(ship.distanceKm);
    const latitude = ship.latitude == null || ship.latitude === '' ? null : Number(ship.latitude);
    const longitude = ship.longitude == null || ship.longitude === '' ? null : Number(ship.longitude);
    const radius = configInt('delivery_radius_km', 8);
    const isFirst = !db.addresses.some((a) => a.customerId === customer.id);
    if (isFirst) {
      db.addresses.filter((a) => a.customerId === customer.id).forEach((a) => { a.isDefault = false; });
    }
    const id = nextId(db.addresses);
    db.addresses.push({
      id,
      customerId: customer.id,
      kind: 'shipping',
      recipientName: String(ship.recipientName ?? '').trim() || customer.name,
      phone: phoneKey(ship.phone == null ? null : String(ship.phone)) || customer.phone,
      line1,
      ward: String(ship.ward ?? '').trim() || null,
      district: null,
      province: String(ship.province ?? '').trim() || 'Hải Phòng',
      latitude: Number.isFinite(latitude as number) ? (latitude as number) : null,
      longitude: Number.isFinite(longitude as number) ? (longitude as number) : null,
      distanceKm: Number.isFinite(distanceKm as number) ? (distanceKm as number) : null,
      inDeliveryRadius: distanceKm != null && Number.isFinite(distanceKm) && distanceKm <= radius,
      isDefault: isFirst,
      createdAt: new Date().toISOString(),
    });
    return id;
  }

  if (needsAddress) fail(400, 'Vui lòng nhập địa chỉ giao hàng');
  return null;
}

mock('PUT', '/orders/:id', ({ params, body, userId }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (!['pending', 'confirmed'].includes(order.state)) fail(409, 'Chỉ sửa được đơn ở trạng thái Chờ xử lý hoặc Đã xác nhận');
  if (['paid', 'partial'].includes(order.paymentStatus)) fail(409, 'Đơn đã thu tiền, không thể sửa');
  if (body.paymentMethod === 'credit_net') fail(409, 'Không bán nợ — mọi đơn phải thanh toán đủ trước khi chuẩn bị');
  if (!body.lines?.length) fail(400, 'Đơn phải có ít nhất một dòng hàng');

  const paymentMethod = normalizePaymentForFulfillment(body.paymentMethod, body.fulfillmentMethod);
  const customer = resolveCustomerForOrder(body);
  const customerName = customer.name;
  const shipAddressId = resolveShipAddressId(customer, body);

  const wasConfirmed = order.state === 'confirmed';
  const wasDebt = order.paymentStatus === 'debt';
  if (wasConfirmed) releaseOrder(order, userId);
  if (wasDebt && order.customerId) postDebtEntry(order.customerId, { orderId: order.id, entryType: 'adjust', amount: -order.total, note: `Sửa đơn ${order.code} — đảo nợ cũ` });

  const priced = priceLines(customer.id, body.lines);
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  Object.assign(order, {
    customerId: customer.id,
    customerName,
    orderType: body.orderType,
    channel: body.channel,
    paymentMethod,
    paymentTermDays: 0,
    fulfillmentMethod: body.fulfillmentMethod,
    shipAddressId,
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
  const paymentMethod = normalizePaymentForFulfillment(body.paymentMethod, body.fulfillmentMethod);
  const customer = resolveCustomerForOrder(body);
  const customerName = customer.name;
  const shipAddressId = resolveShipAddressId(customer, body);
  const priced = priceLines(customer.id, body.lines);
  const short = priced.some((l) => l.shortfall > 0);
  if (short) fail(409, 'Không đủ hàng khả dụng để giữ đủ số lượng — không thể xác nhận đơn');
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  const now = new Date().toISOString();
  const id = nextId(db.orders);
  const order = {
    id, code: nextCode('SO-2026', db.orders), customerId: customer.id, customerName, orderType: body.orderType, channel: body.channel, sourceQuoteId: body.sourceQuoteId ?? null, paymentMethod,
    paymentTermDays: 0, paymentStatus: 'unpaid' as const, allocationStatus: 'none' as const, fulfillmentMethod: body.fulfillmentMethod,
    shipAddressId, state: 'pending' as const, subtotal, discountTotal: Number(body.discountTotal ?? 0), shippingFee: Number(body.shippingFee ?? 0), total, voucherCode: body.voucherCode,
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
      const cod = isCollectOnDelivery(order.paymentMethod, order.fulfillmentMethod);
      if (!cod && order.paymentStatus !== 'paid') fail(409, 'Chỉ chuẩn bị khi đã thanh toán đủ');
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
