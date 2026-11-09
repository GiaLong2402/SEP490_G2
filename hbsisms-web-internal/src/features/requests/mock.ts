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
  const row = { id: nextId(db.requests), code: nextCode('YC', db.requests), customerId: body.customerId, customerName: customer?.name ?? body.customerName ?? 'Khách vãng lai', channel: body.channel, summary: body.summary, status: 'new' as const, receivedAt: new Date().toISOString(), handledBy: userId, note: null, feasible: null, fulfillableQty: null, shortageQty: null, expectedDate: null, rejectReason: null };
  db.requests.push(row);
  return toItem(row);
});

mock('PATCH', '/sales-requests/:id', ({ params, body, userId }) => {
  const row = db.requests.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy yêu cầu');
  Object.assign(row, body, { handledBy: row.handledBy ?? userId });
});
