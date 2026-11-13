import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { userName } from '@/shared/mock/services';

const toItem = (r: (typeof db.requests)[number]) => ({ ...r, handlerName: userName(r.handledBy) });

mock('GET', '/sales-requests', ({ query }) => {
  const rows = db.requests
    .filter((r) => matches(query.q, r.code, r.customerName, r.summary))
    .filter((r) => !query.status || r.status === query.status)
    .filter((r) => !query.channel || r.channel === query.channel)
    .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  return paginate(rows.map(toItem), query);
});

mock('POST', '/sales-requests', ({ body, userId }) => {
  const customer = db.customers.find((c) => c.id === body.customerId);
  const row = {
    id: nextId(db.requests),
    code: nextCode('YC', db.requests),
    customerId: body.customerId,
    customerName: customer?.name ?? body.customerName ?? 'Khách vãng lai',
    contactPhone: body.customerId ? null : (body.contactPhone ?? null),
    contactAddress: body.customerId ? null : (body.contactAddress ?? null),
    channel: body.channel,
    summary: body.summary,
    status: 'new' as const,
    receivedAt: new Date().toISOString(),
    handledBy: userId,
    note: null,
    feasible: null,
    fulfillableQty: null,
    shortageQty: null,
    expectedDate: null,
    rejectReason: null,
  };
  db.requests.push(row);
  return toItem(row);
});

mock('GET', '/sales-requests/:id', ({ params }) => {
  const row = db.requests.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy yêu cầu');
  return toItem(row);
});

mock('PATCH', '/sales-requests/:id', ({ params, body, userId }) => {
  const row = db.requests.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy yêu cầu');
  Object.assign(row, body, { handledBy: row.handledBy ?? userId });
  return toItem(row);
});

mock('POST', '/sales-requests/:id/feasibility', ({ params, body, userId }) => {
  const row = db.requests.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy yêu cầu');
  if (['rejected', 'closed', 'ordered'].includes(row.status)) fail(409, 'Yêu cầu đã kết thúc, không thể cập nhật');
  row.feasible = body.feasible;
  row.fulfillableQty = body.fulfillableQty ?? null;
  row.shortageQty = body.shortageQty ?? null;
  row.expectedDate = body.expectedDate ?? null;
  if (body.note) row.note = body.note;
  row.handledBy = row.handledBy ?? userId;
  if (row.status === 'new') row.status = 'quoting';
  let order = null;
  if (body.customerAccepted && body.feasible) {
    if (!body.order) fail(400, 'Khách đồng ý — vui lòng gửi thông tin đơn hàng');
    row.status = 'ordered';
  }
  return { request: toItem(row), order };
});

mock('POST', '/sales-requests/:id/reject', ({ params, body, userId }) => {
  const row = db.requests.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy yêu cầu');
  if (['rejected', 'closed', 'ordered'].includes(row.status)) fail(409, 'Yêu cầu đã kết thúc, không thể cập nhật');
  const reason = String(body.rejectReason ?? '').trim();
  if (!reason) fail(400, 'Vui lòng nhập lý do từ chối');
  row.status = 'rejected';
  row.rejectReason = reason;
  row.feasible = false;
  row.handledBy = row.handledBy ?? userId;
  return toItem(row);
});
