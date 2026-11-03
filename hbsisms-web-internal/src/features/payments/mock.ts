import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, audit, customerName, postDebtEntry, userName } from '@/shared/mock/services';

const toItem = (p: (typeof db.payments)[number]) => ({ ...p, orderCode: db.orders.find((o) => o.id === p.orderId)?.code ?? null, customerName: customerName(p.customerId) ?? (p.orderId ? 'Khách lẻ' : null), receiverName: userName(p.receivedBy) });

mock('GET', '/payments', ({ query }) => {
  const rows = db.payments
    .map(toItem)
    .filter((p) => matches(query.q, p.code, p.orderCode, p.customerName, p.reference))
    .filter((p) => !query.method || p.method === query.method)
    .filter((p) => !query.direction || p.direction === query.direction)
    .sort((a, b) => b.paidAt.localeCompare(a.paidAt));
  return paginate(rows, query);
});

mock('POST', '/payments', ({ body, userId }) => {
  const amount = Number(body.amount);
  if (!(amount > 0)) fail(400, 'Số tiền phải lớn hơn 0');
  const order = db.orders.find((o) => o.id === body.orderId);
  const customer = db.customers.find((c) => c.id === (body.customerId ?? order?.customerId));
  if (order) {
    if (order.state === 'cancelled') fail(409, 'Đơn đã hủy');
    if (order.paymentMethod === 'credit_net' && order.state === 'pending') fail(409, 'Đơn công nợ chưa được xác nhận, chưa phát sinh nợ');
    const paid = db.payments.filter((p) => p.orderId === order.id && p.status === 'success').reduce((s, p) => s + (p.direction === 'in' ? p.amount : -p.amount), 0);
    if (amount > order.total - paid) fail(409, 'Số tiền vượt quá phần còn lại của đơn');
  } else {
    if (!customer) fail(400, 'Vui lòng chọn khách hàng');
    if (amount > customer.currentDebt) fail(409, 'Số tiền vượt quá công nợ hiện tại');
  }
  const id = nextId(db.payments);
  const row = {
    id, code: nextCode('TT', db.payments), orderId: order?.id ?? null, customerId: customer?.id ?? null, method: body.method, direction: 'in' as const, amount, status: 'success' as const, reference: body.reference,
    gateway: body.method === 'payos' ? 'payos' : body.method === 'cash' ? 'cash' : 'bank', payosOrderCode: null, checkoutUrl: null, paidAt: new Date().toISOString(), receivedBy: userId, note: body.note,
  };
  db.payments.push(row);
  const isDebtPayment = !order || order.paymentMethod === 'credit_net';
  if (isDebtPayment && customer) postDebtEntry(customer.id, { orderId: order?.id, paymentId: id, entryType: 'payment', amount: -amount, note: `Thanh toán ${row.code}` });
  if (order) {
    const paid = db.payments.filter((p) => p.orderId === order.id && p.status === 'success').reduce((s, p) => s + (p.direction === 'in' ? p.amount : -p.amount), 0);
    order.paymentStatus = paid >= order.total ? 'paid' : 'partial';
    order.updatedAt = row.paidAt;
    addOrderEvent(order.id, 'payment', `Thu ${amount.toLocaleString('vi-VN')} ₫ (${row.code})`, userId);
  }
  audit(userId, 'create', 'payment', id, { amount });
  return toItem(row);
});
