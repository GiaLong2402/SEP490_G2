import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextId } from '@/shared/mock/db';
import { audit, customerName } from '@/shared/mock/services';

function validate(body: any, id: number | null) {
  const code = String(body.code ?? '').trim().toUpperCase();
  if (!code) fail(400, 'Vui lòng nhập mã voucher');
  if (db.vouchers.some((v) => v.code === code && v.id !== id)) fail(409, `Mã ${code} đã tồn tại`);
  if (body.voucherType === 'percent' && (body.value <= 0 || body.value > 100)) fail(400, 'Phần trăm giảm phải từ 1 đến 100');
  if (body.startsAt && body.endsAt && body.endsAt < body.startsAt) fail(400, 'Ngày kết thúc phải sau ngày bắt đầu');
  return code;
}

mock('GET', '/vouchers', ({ query }) => paginate(db.vouchers.filter((v) => matches(query.q, v.code, v.name)).sort((a, b) => b.id - a.id), query));

mock('POST', '/vouchers', ({ body, userId }) => {
  const row = { ...body, code: validate(body, null), id: nextId(db.vouchers), usedCount: 0 };
  db.vouchers.push(row);
  audit(userId, 'create', 'voucher', row.id);
  return row;
});

mock('PUT', '/vouchers/:id', ({ params, body, userId }) => {
  const row = db.vouchers.find((v) => v.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy voucher');
  Object.assign(row, body, { code: validate(body, row.id) });
  audit(userId, 'update', 'voucher', row.id);
  return row;
});

mock('GET', '/reviews', ({ query }) => {
  const rows = db.reviews
    .map((r) => ({ ...r, productName: db.products.find((p) => p.id === r.productId)?.name ?? '—', customerName: customerName(r.customerId) }))
    .filter((r) => matches(query.q, r.title, r.body, r.productName, r.customerName))
    .filter((r) => !query.status || r.status === query.status)
    .filter((r) => !query.rating || r.rating === Number(query.rating))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return paginate(rows, query);
});

mock('PATCH', '/reviews/:id/status', ({ params, body, userId }) => {
  const row = db.reviews.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy đánh giá');
  row.status = body.status;
  audit(userId, 'moderate', 'product_review', row.id, { status: body.status });
});
