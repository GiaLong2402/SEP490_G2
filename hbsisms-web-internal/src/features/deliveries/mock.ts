import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, audit, customerName, shipHeld, userName } from '@/shared/mock/services';

function toItem(d: (typeof db.deliveries)[number]) {
  const order = db.orders.find((o) => o.id === d.orderId);
  const address = db.addresses.find((a) => a.id === d.addressId);
  return {
    ...d, orderCode: order?.code ?? '—', customerName: customerName(order?.customerId) ?? 'Khách lẻ', addressText: address ? [address.line1, address.ward, address.province || address.district].filter(Boolean).join(', ') : null,
    assigneeName: userName(d.assignedTo), totalKhay: (d.items ?? []).reduce((sum, i) => sum + i.qtyKhay, 0),
  };
}

/** Cập nhật trạng thái đơn theo tổng số khay đã giao. */
function syncOrderState(orderId: number, userId: number | null) {
  const order = db.orders.find((o) => o.id === orderId);
  if (!order) return;
  const ordered = db.orderLines.filter((l) => l.orderId === orderId).reduce((sum, l) => sum + l.qtyKhay, 0);
  const delivered = db.deliveries.filter((d) => d.orderId === orderId && d.state === 'delivered').flatMap((d) => d.items ?? []).reduce((sum, i) => sum + i.qtyKhay, 0);
  const next = delivered >= ordered ? 'delivered' : delivered > 0 ? 'partial' : order.state;
  if (next !== order.state) {
    order.state = next;
    order.updatedAt = new Date().toISOString();
    if (next === 'delivered') addOrderEvent(orderId, 'delivered', 'Đã giao đủ hàng', userId);
  }
}

mock('GET', '/deliveries/couriers', () =>
  db.users.filter((u) => u.isActive && u.position === 'Giao hàng').map((u) => ({ id: u.id, fullName: u.fullName, position: u.position })),
);

mock('GET', '/deliveries', ({ query }) => {
  const rows = db.deliveries
    .map(toItem)
    .filter((d) => matches(query.q, d.code, d.orderCode, d.customerName))
    .filter((d) => !query.state || d.state === query.state)
    .filter((d) => !query.assignedTo || d.assignedTo === Number(query.assignedTo))
    .filter((d) => !query.date || d.scheduledDate === query.date)
    .sort((a, b) => (b.scheduledDate ?? '').localeCompare(a.scheduledDate ?? '') || b.id - a.id);
  return paginate(rows, query);
});

mock('POST', '/deliveries', ({ body, userId }) => {
  const order = db.orders.find((o) => o.id === body.orderId);
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  const items = body.items.filter((i: any) => i.qtyKhay > 0);
  if (!items.length) fail(400, 'Đợt giao phải có ít nhất một khay');
  // Không cho vượt số lượng còn lại của từng dòng.
  items.forEach((item: any) => {
    const line = db.orderLines.find((l) => l.id === item.orderLineId);
    if (!line) fail(400, 'Dòng hàng không thuộc đơn');
    const scheduled = db.deliveries.filter((d) => d.orderId === order.id && d.state !== 'failed').flatMap((d) => d.items ?? []).filter((i) => i.orderLineId === line.id).reduce((sum, i) => sum + i.qtyKhay, 0);
    if (scheduled + item.qtyKhay > line.qtyKhay) fail(409, 'Số khay vượt quá phần còn lại của đơn');
  });
  const row = { ...body, items, id: nextId(db.deliveries), code: nextCode('GH', db.deliveries), state: 'ready' as const, deliveredAt: null };
  db.deliveries.push(row);
  addOrderEvent(order.id, 'delivery', `Tạo đợt giao ${row.legLabel}`, userId);
  return toItem(row);
});

mock('PATCH', '/deliveries/:id', ({ params, body, userId }) => {
  const row = db.deliveries.find((d) => d.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy đợt giao');
  if (row.state === 'delivered') fail(409, 'Đợt giao đã hoàn tất');
  if ('assignedTo' in body) row.assignedTo = body.assignedTo;
  if (body.note) row.note = body.note;
  if (body.state) {
    const order = db.orders.find((o) => o.id === row.orderId);
    if (order && order.state !== 'to_ship' && order.state !== 'partial') {
      fail(409, 'Chỉ được bắt đầu/cập nhật giao khi đơn ở trạng thái Chờ giao hoặc Giao một phần');
    }
    row.state = body.state;
    if (body.state === 'delivered') {
      row.deliveredAt = new Date().toISOString();
      row.items?.forEach((item) => {
        const line = db.orderLines.find((l) => l.id === item.orderLineId);
        if (line) shipHeld(line.variantId, item.qtyKhay, row.id, userId);
      });
      addOrderEvent(row.orderId, 'delivery', `Đã giao ${row.legLabel}`, userId);
      syncOrderState(row.orderId, userId);
    } else if (body.state === 'failed') {
      addOrderEvent(row.orderId, 'delivery', `Giao thất bại ${row.legLabel}${body.note ? `: ${body.note}` : ''}`, userId);
    } else if (body.state === 'going') {
      addOrderEvent(row.orderId, 'delivery', `Bắt đầu giao ${row.legLabel}`, userId);
    }
    audit(userId, 'update', 'delivery', row.id, { state: body.state });
  }
});
