import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { audit } from '@/shared/mock/services';

function toItem(s: (typeof db.suppliers)[number]) {
  // Receipts store free-text source (no supplier FK yet); match by name for mock stats.
  const receipts = db.receipts.filter((r) => (r.source ?? '').toLowerCase() === s.name.toLowerCase());
  return { ...s, receiptCount: receipts.length, lastReceiptAt: receipts.map((r) => r.receivedAt).sort().pop() ?? null };
}

function validate(body: any, id: number | null) {
  const name = String(body.name ?? '').trim();
  if (!name) fail(400, 'Vui lòng nhập tên nhà cung cấp');
  if (body.taxCode && db.suppliers.some((s) => s.taxCode === body.taxCode && s.id !== id)) fail(409, `Mã số thuế ${body.taxCode} đã có nhà cung cấp khác dùng`);
  return name;
}

mock('GET', '/suppliers', ({ query }) => {
  const rows = db.suppliers
    .filter((s) => matches(query.q, s.code, s.name, s.taxCode, s.phone, s.contactName))
    .filter((s) => !query.status || s.isActive === (query.status === 'active'))
    .sort((a, b) => a.name.localeCompare(b.name, 'vi'));
  return paginate(rows.map(toItem), query);
});

mock('POST', '/suppliers', ({ body, userId }) => {
  const now = new Date().toISOString();
  const row = { ...body, name: validate(body, null), id: nextId(db.suppliers), code: nextCode('NCC', db.suppliers), createdAt: now, updatedAt: now };
  db.suppliers.push(row);
  audit(userId, 'create', 'supplier', row.id);
  return toItem(row);
});

mock('PUT', '/suppliers/:id', ({ params, body, userId }) => {
  const row = db.suppliers.find((s) => s.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy nhà cung cấp');
  Object.assign(row, body, { name: validate(body, row.id), updatedAt: new Date().toISOString() });
  audit(userId, 'update', 'supplier', row.id);
  return toItem(row);
});
