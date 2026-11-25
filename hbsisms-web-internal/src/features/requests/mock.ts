import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { userName } from '@/shared/mock/services';

const toItem = (r: (typeof db.requests)[number]) => ({ ...r, handlerName: userName(r.handledBy) });

function mapOfferLines(inputLines: { variantId: number; unit?: string; qty: number }[]) {
  return inputLines.map((l, i) => {
    const v = db.variants.find((x) => x.id === l.variantId);
    const p = v ? db.products.find((x) => x.id === v.productId) : undefined;
    const unit = (l.unit === 'case' ? 'case' : 'pack') as 'pack' | 'case';
    const packPerCase = v?.packPerCase ?? 1;
    const qtyKhay = unit === 'case' ? l.qty * packPerCase : l.qty;
    return {
      id: i + 1,
      variantId: l.variantId,
      sku: v?.sku ?? String(l.variantId),
      productName: p?.name ?? null,
      packLabel: v?.packLabel ?? null,
      packPerCase,
      unit,
      qty: l.qty,
      qtyKhay,
      fulfillableQty: l.qty as number | null,
      shortageQty: 0 as number | null,
    };
  });
}

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
  const summary = String(body.summary ?? '').trim();
  if (!summary) fail(400, 'Vui lòng nhập nội dung yêu cầu');
  const row = {
    id: nextId(db.requests),
    code: nextCode('YC', db.requests),
    customerId: body.customerId,
    customerName: customer?.name ?? body.customerName ?? 'Khách vãng lai',
    contactPhone: body.contactPhone ?? null,
    contactAddress: body.contactAddress ?? null,
    channel: body.channel,
    summary,
    status: 'new' as const,
    receivedAt: new Date().toISOString(),
    handledBy: userId,
    note: null,
    neededDate: body.neededDate ?? null,
    feasible: null,
    fulfillableQty: null,
    shortageQty: null,
    expectedDate: null,
    rejectReason: null,
    lines: [] as ReturnType<typeof mapOfferLines>,
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
  if (body.feasible === false) {
    const reason = String(body.note ?? body.rejectReason ?? '').trim();
    if (!reason) fail(400, 'Vui lòng nhập lý do từ chối');
    row.status = 'rejected';
    row.rejectReason = reason;
    row.feasible = false;
    row.handledBy = row.handledBy ?? userId;
    return { request: toItem(row), order: null };
  }
  row.feasible = true;
  row.expectedDate = body.expectedDate ?? null;
  if (body.note) row.note = body.note;
  row.handledBy = row.handledBy ?? userId;
  row.fulfillableQty = body.fulfillableQty ?? null;
  row.shortageQty = body.shortageQty ?? null;
  if (Array.isArray(body.lines) && body.lines.length > 0) {
    const lines = mapOfferLines(body.lines);
    row.lines = lines;
    row.fulfillableQty = lines.reduce((s, l) => s + l.qtyKhay, 0);
    row.shortageQty = 0;
  }
  if (row.status === 'new') row.status = 'quoting';
  let order = null;
  if (body.customerAccepted) {
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
