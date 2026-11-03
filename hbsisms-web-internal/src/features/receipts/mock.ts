import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { appendLedger, audit, userName, variantLabel } from '@/shared/mock/services';

const supplierName = (id: number) => db.suppliers.find((s) => s.id === id)?.name ?? '—';
const toItem = (r: (typeof db.receipts)[number]) => ({ ...r, receiverName: userName(r.receivedBy), supplierName: supplierName(r.supplierId) });

function checkRawMaterial(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code || !String(body.name ?? '').trim() || !String(body.unit ?? '').trim()) fail(400, 'Vui lòng nhập mã, tên và đơn vị');
  if (db.rawMaterials.some((m) => m.code === code && m.id !== id)) fail(409, `Mã ${code} đã tồn tại`);
  if (body.reorderLevel !== null && Number(body.reorderLevel) < 0) fail(400, 'Mức đặt lại không được âm');
  return code;
}

mock('GET', '/raw-materials', () => db.rawMaterials);

mock('POST', '/raw-materials', ({ body, userId }) => {
  const row = { ...body, code: checkRawMaterial(body, null), id: nextId(db.rawMaterials), qtyOnHand: 0 };
  db.rawMaterials.push(row);
  audit(userId, 'create', 'raw_material', row.id);
  return row;
});

// Không nhận qtyOnHand: tồn chỉ thay đổi qua phiếu nhập.
mock('PUT', '/raw-materials/:id', ({ params, body, userId }) => {
  const row = db.rawMaterials.find((m) => m.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy nguyên liệu');
  const { qtyOnHand: _ignored, ...rest } = body;
  Object.assign(row, rest, { code: checkRawMaterial(body, row.id) });
  audit(userId, 'update', 'raw_material', row.id);
  return row;
});

mock('GET', '/goods-receipts', ({ query }) => {
  const rows = db.receipts
    .filter((r) => matches(query.q, r.code, supplierName(r.supplierId)))
    .filter((r) => !query.receiptType || r.receiptType === query.receiptType)
    .filter((r) => !query.supplierId || r.supplierId === Number(query.supplierId))
    .sort((a, b) => b.receivedAt.localeCompare(a.receivedAt));
  return paginate(rows.map(toItem), query);
});

mock('POST', '/goods-receipts', ({ body, userId }) => {
  const supplier = db.suppliers.find((s) => s.id === body.supplierId);
  if (!supplier) fail(400, 'Vui lòng chọn nhà cung cấp');
  if (!supplier.isActive) fail(409, `${supplier.name} đã ngừng giao dịch`);
  const id = nextId(db.receipts);
  const now = new Date().toISOString();
  let items: (typeof db.receipts)[number]['items'] = [];
  if (body.receiptType === 'finished') {
    if (!body.finishedLines?.length) fail(400, 'Phiếu phải có ít nhất một dòng');
    items = body.finishedLines.map((line: any) => {
      const qty = Number(line.qty);
      if (!Number.isInteger(qty) || qty <= 0) fail(400, 'Số lượng phải là số nguyên dương');
      if (line.mfgDate && line.expDate < line.mfgDate) fail(400, 'HSD không được trước ngày sản xuất');
      const lotId = nextId(db.lots);
      const lotCode = line.lotCode?.trim() || `LOT-${now.slice(2, 10).replace(/-/g, '')}-${String(lotId).padStart(2, '0')}`;
      if (db.lots.some((l) => l.lotCode === lotCode)) fail(409, `Mã lô ${lotCode} đã tồn tại`);
      db.lots.push({ id: lotId, lotCode, variantId: line.variantId, mfgDate: line.mfgDate, expDate: line.expDate, receivedAt: now, note: null });
      db.stock.push({ id: nextId(db.stock), variantId: line.variantId, lotId, locationId: line.locationId, qtyAvailable: qty, qtyHeld: 0, qtyQuarantine: 0, qtyExpired: 0, updatedAt: now });
      appendLedger({ variantId: line.variantId, lotId, locationId: line.locationId, toLocationId: null, entryType: 'in', qtyDelta: qty, balanceAfter: qty, refType: 'goods_receipt', refId: id, note: 'Nhập thành phẩm', createdBy: userId });
      return { name: `${variantLabel(line.variantId).sku} · ${lotCode}`, qty, unit: 'khay' };
    });
  } else {
    if (!body.rawLines?.length) fail(400, 'Phiếu phải có ít nhất một dòng');
    // Schema chưa có nơi lưu chi tiết nhập nguyên liệu; tạm giữ ở `items` (spec mục 5 #4).
    items = body.rawLines.map((line: any) => {
      const material = db.rawMaterials.find((m) => m.id === line.rawMaterialId);
      if (!material) fail(400, 'Nguyên liệu không tồn tại');
      const qty = Number(line.qty);
      if (!(qty > 0)) fail(400, 'Số lượng phải lớn hơn 0');
      material.qtyOnHand += qty;
      return { name: material.name, qty, unit: material.unit };
    });
  }
  const row = { id, code: nextCode('PN', db.receipts), receiptType: body.receiptType, supplierId: supplier.id, source: supplier.name, receivedBy: userId, receivedAt: now, note: body.note, items };
  db.receipts.push(row);
  audit(userId, 'create', 'goods_receipt', id);
  return toItem(row);
});
