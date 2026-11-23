import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { appendLedger, audit, userName, variantLabel } from '@/shared/mock/services';

type ReceiptRow = (typeof db.receipts)[number];
type ReceiptStatus = 'pending' | 'completed' | 'cancelled';
interface MockLine {
  id: number; rawMaterialId: number; qtyExpected: number; qtyReceived: number | null; lotCode: string | null;
  mfgDate: string | null; expDate: string | null; unitPrice: number | null;
}
interface MockRawLot {
  id: number; rawMaterialId: number; lotCode: string; mfgDate: string | null; expDate: string | null; unitPrice: number | null;
  qtyReceived: number; qtyRemaining: number; receiptId: number | null; receiptCode: string | null; receivedAt: string; note: string | null;
}
/** Trạng thái + dòng của phiếu NVL (Inter 1 dòng 46–47). Phiếu seed cũ không có → coi là đã hoàn thành. */
const extras = new Map<number, { status: ReceiptStatus; expectedDate: string | null; completedAt: string | null; completedBy: number | null; lines: MockLine[] }>();
const rawLots: MockRawLot[] = [];
let lineSeq = 1;

/** Tồn NVL có sẵn trong dữ liệu mẫu → 1 lô "tồn đầu kỳ" (giống BE lúc khởi động). */
function ensureOpeningLots() {
  db.rawMaterials.forEach((m) => {
    const inLots = rawLots.filter((l) => l.rawMaterialId === m.id).reduce((sum, l) => sum + l.qtyRemaining, 0);
    const gap = m.qtyOnHand - inLots;
    if (gap > 0) {
      rawLots.push({ id: rawLots.length + 1, rawMaterialId: m.id, lotCode: 'TON-DAU', mfgDate: null, expDate: null, unitPrice: null, qtyReceived: gap, qtyRemaining: gap, receiptId: null, receiptCode: null, receivedAt: new Date().toISOString(), note: 'Tồn đầu kỳ (có trước khi quản lý theo lô)' });
    }
  });
}

const lineView = (line: MockLine) => {
  const material = db.rawMaterials.find((m) => m.id === line.rawMaterialId);
  return {
    ...line,
    rawMaterialCode: material?.code ?? '-',
    rawMaterialName: material?.name ?? '-',
    unit: material?.unit ?? '',
    amount: line.qtyReceived !== null && line.unitPrice !== null ? line.qtyReceived * line.unitPrice : null,
  };
};

const toItem = (r: ReceiptRow) => {
  const extra = extras.get(r.id);
  const lines = (extra?.lines ?? []).map(lineView);
  const amounts = lines.map((l) => l.amount).filter((a): a is number => a !== null);
  return {
    ...r,
    receiverName: userName(r.receivedBy),
    supplierName: r.source?.trim() || '-',
    status: extra?.status ?? ('completed' as ReceiptStatus),
    expectedDate: extra?.expectedDate ?? null,
    completedAt: extra?.completedAt ?? null,
    completerName: extra?.completedBy ? userName(extra.completedBy) : null,
    totalAmount: amounts.length ? amounts.reduce((a, b) => a + b, 0) : null,
    lines,
  };
};

/** Ưu tiên unitId; chuỗi unit cũ chỉ dùng khi không có unitId. Trả về code + đơn vị đã chuẩn hóa. */
function checkRawMaterial(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code || !String(body.name ?? '').trim()) fail(400, 'Vui lòng nhập mã, tên và đơn vị');
  if (db.rawMaterials.some((m) => m.code === code && m.id !== id)) fail(409, `Mã ${code} đã tồn tại`);
  if (body.reorderLevel !== null && body.reorderLevel !== undefined && Number(body.reorderLevel) < 0) fail(400, 'Mức đặt lại không được âm');
  let unitId: number | null = null;
  let unit: string;
  if (body.unitId !== null && body.unitId !== undefined) {
    const ref = db.units.find((u) => u.id === Number(body.unitId));
    if (!ref) fail(404, 'Không tìm thấy đơn vị tính');
    const current = id === null ? undefined : db.rawMaterials.find((m) => m.id === id);
    if (!ref.isActive && ref.id !== current?.unitId) fail(400, 'Đơn vị tính đã ngừng sử dụng');
    unitId = ref.id;
    unit = ref.code;
  } else {
    const legacy = String(body.unit ?? '').trim();
    if (!legacy) fail(400, 'Vui lòng nhập mã, tên và đơn vị');
    const matched = db.units.find((u) => u.code.toLowerCase() === legacy.toLowerCase());
    unitId = matched?.id ?? null;
    unit = matched?.code ?? legacy;
  }
  return { code, unit, unitId };
}

mock('GET', '/raw-materials', ({ query }) => {
  const q = String(query.q ?? '').trim().toLowerCase();
  return db.rawMaterials.filter((m) => {
    if (q && !m.code.toLowerCase().includes(q) && !m.name.toLowerCase().includes(q)) return false;
    if (query.activeOnly === 'true' && !m.isActive) return false;
    if (query.activeOnly === 'false' && m.isActive) return false;
    if (query.lowStock === 'true' && !(m.reorderLevel !== null && m.qtyOnHand < m.reorderLevel)) return false;
    return true;
  });
});

mock('GET', '/raw-materials/:id', ({ params }) => {
  const row = db.rawMaterials.find((m) => m.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy nguyên liệu');
  return row;
});

mock('POST', '/raw-materials', ({ body, userId }) => {
  const row = { ...body, ...checkRawMaterial(body, null), id: nextId(db.rawMaterials), qtyOnHand: 0 };
  db.rawMaterials.push(row);
  audit(userId, 'create', 'raw_material', row.id);
  return row;
});

// Không nhận qtyOnHand: tồn chỉ thay đổi qua phiếu nhập.
mock('PUT', '/raw-materials/:id', ({ params, body, userId }) => {
  const row = db.rawMaterials.find((m) => m.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy nguyên liệu');
  const { qtyOnHand: _ignored, ...rest } = body;
  Object.assign(row, rest, checkRawMaterial(body, row.id));
  audit(userId, 'update', 'raw_material', row.id);
  return row;
});

mock('GET', '/raw-materials/:id/lots', ({ params, query }) => {
  const material = db.rawMaterials.find((m) => m.id === Number(params.id));
  if (!material) fail(404, 'Không tìm thấy nguyên liệu');
  ensureOpeningLots();
  return rawLots
    .filter((l) => l.rawMaterialId === material.id && (query.availableOnly !== 'true' || l.qtyRemaining > 0))
    .sort((a, b) => (a.expDate === null ? 1 : 0) - (b.expDate === null ? 1 : 0) || (a.expDate ?? '').localeCompare(b.expDate ?? '') || a.receivedAt.localeCompare(b.receivedAt));
});

mock('GET', '/goods-receipts', ({ query }) => {
  const rows = db.receipts
    .filter((r) => matches(query.q, r.code, r.source))
    .filter((r) => !query.receiptType || r.receiptType === query.receiptType)
    .map(toItem)
    .filter((r) => !query.status || r.status === query.status)
    .sort((a, b) => (query.sort === 'asc' ? a.id - b.id : b.id - a.id));
  return paginate(rows, query);
});

mock('GET', '/goods-receipts/:id', ({ params }) => {
  const row = db.receipts.find((r) => r.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy phiếu nhập');
  return toItem(row);
});

mock('POST', '/goods-receipts', ({ body, userId }) => {
  const source = String(body.source ?? '').trim();
  if (!source) fail(400, 'Vui lòng nhập nguồn hàng');
  const id = nextId(db.receipts);
  const now = new Date().toISOString();
  let items: ReceiptRow['items'] = [];
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
      const existing = db.stock.find((s) => s.variantId === line.variantId && s.lotId === lotId && s.locationId === line.locationId);
      if (existing) {
        existing.qtyAvailable += qty;
        existing.updatedAt = now;
      } else {
        db.stock.push({ id: nextId(db.stock), variantId: line.variantId, lotId, locationId: line.locationId, qtyAvailable: qty, qtyHeld: 0, qtyQuarantine: 0, qtyExpired: 0, updatedAt: now });
      }
      appendLedger({ variantId: line.variantId, lotId, locationId: line.locationId, toLocationId: null, entryType: 'in', qtyDelta: qty, balanceAfter: qty, refType: 'goods_receipt', refId: id, note: 'Nhập thành phẩm', createdBy: userId });
      return { name: `${variantLabel(line.variantId).sku} · ${lotCode}`, qty, unit: 'khay' };
    });
    const row = { id, code: nextCode('PN', db.receipts), receiptType: body.receiptType, source, receivedBy: userId, receivedAt: now, note: body.note, items };
    db.receipts.push(row);
    extras.set(id, { status: 'completed', expectedDate: null, completedAt: now, completedBy: userId, lines: [] });
    audit(userId, 'create', 'goods_receipt', id);
    return toItem(row);
  }
  // Phiếu NVL: Chờ nhập — chưa cộng tồn.
  if (!body.rawLines?.length) fail(400, 'Phiếu phải có ít nhất một dòng');
  const seen = new Set<number>();
  const lines: MockLine[] = body.rawLines.map((line: any) => {
    const material = db.rawMaterials.find((m) => m.id === line.rawMaterialId);
    if (!material) fail(404, 'Không tìm thấy nguyên liệu');
    if (!material.isActive) fail(409, `${material.name} đã ngừng dùng`);
    if (seen.has(material.id)) fail(400, 'Mỗi nguyên liệu chỉ nhập một dòng trên phiếu');
    seen.add(material.id);
    const qty = Number(line.qty);
    if (!(qty > 0)) fail(400, 'Số lượng dự kiến phải lớn hơn 0');
    return { id: lineSeq++, rawMaterialId: material.id, qtyExpected: qty, qtyReceived: null, lotCode: null, mfgDate: null, expDate: null, unitPrice: null };
  });
  items = lines.map((l) => ({ name: lineView(l).rawMaterialName, qty: l.qtyExpected, unit: lineView(l).unit }));
  const row = { id, code: nextCode('PN', db.receipts), receiptType: body.receiptType, source, receivedBy: userId, receivedAt: now, note: body.note ?? null, items };
  db.receipts.push(row);
  extras.set(id, { status: 'pending', expectedDate: body.expectedDate ?? null, completedAt: null, completedBy: null, lines });
  audit(userId, 'create', 'goods_receipt', id);
  return toItem(row);
});

function pendingRow(id: number) {
  const row = db.receipts.find((r) => r.id === id);
  if (!row) fail(404, 'Không tìm thấy phiếu nhập');
  const extra = extras.get(id);
  if (!extra || extra.status === 'completed') fail(409, `Phiếu ${row.code} đã nhập kho`);
  if (extra.status === 'cancelled') fail(409, `Phiếu ${row.code} đã hủy`);
  return { row, extra };
}

mock('POST', '/goods-receipts/:id/complete', ({ params, body, userId }) => {
  const { row, extra } = pendingRow(Number(params.id));
  if (row.receiptType !== 'raw') fail(409, 'Chỉ phiếu nhập nguyên liệu mới xác nhận nhập kho theo bước này');
  const inputs = new Map<number, any>((body.lines ?? []).map((l: any) => [l.lineId, l]));
  const today = new Date().toISOString().slice(0, 10);
  let total = 0;
  extra.lines.forEach((line) => {
    const input = inputs.get(line.id);
    const name = lineView(line).rawMaterialName;
    if (!input) fail(400, `Thiếu số lượng thực nhận cho ${name}`);
    const qty = Number(input.qtyReceived);
    if (!(qty >= 0)) fail(400, 'Số lượng thực nhận không được âm');
    if (qty > 0) {
      if (!(Number(input.unitPrice) > 0)) fail(400, `Vui lòng nhập đơn giá nhập lớn hơn 0 cho ${name}`);
      if (input.mfgDate && input.expDate && input.expDate < input.mfgDate) fail(400, `HSD của ${name} không được trước ngày sản xuất`);
      if (input.expDate && input.expDate < today) fail(400, `HSD của ${name} đã qua - không nhập kho lô hết hạn`);
    }
    total += qty;
  });
  if (!(total > 0)) fail(400, 'Phải có ít nhất một dòng thực nhận lớn hơn 0 - nếu hàng không về, hãy hủy phiếu');
  ensureOpeningLots();
  const now = new Date().toISOString();
  extra.lines.forEach((line) => {
    const input = inputs.get(line.id);
    const qty = Number(input.qtyReceived);
    line.qtyReceived = qty;
    line.mfgDate = input.mfgDate ?? null;
    line.expDate = input.expDate ?? null;
    if (qty <= 0) return;
    let lotCode = String(input.lotCode ?? '').trim();
    if (!lotCode) {
      const day = now.slice(2, 10).replace(/-/g, '');
      for (let i = 1; ; i++) {
        lotCode = `NVL-${day}-${String(i).padStart(2, '0')}`;
        if (!rawLots.some((l) => l.rawMaterialId === line.rawMaterialId && l.lotCode === lotCode)) break;
      }
    } else if (rawLots.some((l) => l.rawMaterialId === line.rawMaterialId && l.lotCode.toLowerCase() === lotCode.toLowerCase())) {
      fail(409, `Mã lô ${lotCode} đã có cho nguyên liệu ${lineView(line).rawMaterialName}`);
    }
    line.lotCode = lotCode;
    line.unitPrice = Number(input.unitPrice);
    rawLots.push({ id: rawLots.length + 1, rawMaterialId: line.rawMaterialId, lotCode, mfgDate: line.mfgDate, expDate: line.expDate, unitPrice: line.unitPrice, qtyReceived: qty, qtyRemaining: qty, receiptId: row.id, receiptCode: row.code, receivedAt: now, note: null });
    const material = db.rawMaterials.find((m) => m.id === line.rawMaterialId);
    if (material) material.qtyOnHand += qty;
  });
  extra.status = 'completed';
  extra.completedAt = now;
  extra.completedBy = userId;
  row.receivedAt = now;
  row.items = extra.lines.map((l) => ({ name: lineView(l).rawMaterialName, qty: l.qtyReceived ?? 0, unit: lineView(l).unit }));
  if (body.note) row.note = row.note ? `${row.note} - ${body.note}` : body.note;
  audit(userId, 'complete', 'goods_receipt', row.id);
  return toItem(row);
});

mock('POST', '/goods-receipts/:id/cancel', ({ params, body, userId }) => {
  const { row, extra } = pendingRow(Number(params.id));
  extra.status = 'cancelled';
  extra.completedAt = new Date().toISOString();
  extra.completedBy = userId;
  const reason = String(body?.reason ?? '').trim();
  if (reason) row.note = row.note ? `${row.note} - Hủy: ${reason}` : `Hủy: ${reason}`;
  audit(userId, 'cancel', 'goods_receipt', row.id);
  return toItem(row);
});
