import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';
import { appendLedger, audit, configInt, locationCode, userName, variantLabel } from '@/shared/mock/services';

const dayMs = 86_400_000;
const daysLeft = (exp: string | null) => (exp ? Math.round((new Date(`${exp}T00:00:00`).getTime() - new Date(new Date().toDateString()).getTime()) / dayMs) : null);

function lotRows() {
  return db.stock.map((s) => {
    const lot = db.lots.find((l) => l.id === s.lotId);
    const label = variantLabel(s.variantId);
    return {
      stockId: s.id, lotId: s.lotId, lotCode: lot?.lotCode ?? '—', variantId: s.variantId, sku: label.sku, productName: label.productName, locationId: s.locationId,
      locationCode: locationCode(s.locationId) ?? '—', mfgDate: lot?.mfgDate ?? null, expDate: lot?.expDate ?? null, daysLeft: daysLeft(lot?.expDate ?? null),
      available: s.qtyAvailable, held: s.qtyHeld, quarantine: s.qtyQuarantine, expired: s.qtyExpired,
    };
  });
}
const byFefo = <T extends { expDate: string | null }>(a: T, b: T) => (a.expDate ?? '9999').localeCompare(b.expDate ?? '9999');

function variantRows() {
  const threshold = configInt('low_stock_threshold', 15);
  const lots = lotRows();
  return db.variants.map((v) => {
    const own = lots.filter((l) => l.variantId === v.id).sort(byFefo);
    const available = own.reduce((sum, l) => sum + l.available, 0);
    return {
      variantId: v.id, ...variantLabel(v.id), available, held: own.reduce((s, l) => s + l.held, 0), quarantine: own.reduce((s, l) => s + l.quarantine, 0), expired: own.reduce((s, l) => s + l.expired, 0),
      isLow: available < threshold, lots: own.filter((l) => l.available + l.held + l.quarantine + l.expired > 0),
    };
  });
}

// Khác view v_near_expiry: bỏ lô đã hết hàng để không cảnh báo sai (spec mục 5 #3).
function nearExpiryRows() {
  const days = configInt('expiry_warning_days', 5);
  return lotRows().filter((l) => l.daysLeft !== null && l.daysLeft <= days && l.available + l.held + l.quarantine + l.expired > 0).sort(byFefo);
}

mock('GET', '/inventory/summary', () => {
  const rows = variantRows();
  return {
    available: rows.reduce((s, r) => s + r.available, 0), held: rows.reduce((s, r) => s + r.held, 0), quarantine: rows.reduce((s, r) => s + r.quarantine, 0),
    nearExpiryLots: nearExpiryRows().length, lowStockVariants: rows.filter((r) => r.isLow).length, lowStockThreshold: configInt('low_stock_threshold', 15), expiryWarningDays: configInt('expiry_warning_days', 5),
  };
});

mock('GET', '/inventory/variants', ({ query }) => {
  const rows = variantRows()
    .filter((r) => matches(query.q, r.sku, r.productName))
    .filter((r) => !query.lowOnly || r.isLow)
    .filter((r) => !query.locationId || r.lots.some((l) => l.locationId === Number(query.locationId)))
    .sort((a, b) => a.available - b.available);
  return paginate(rows, query);
});

mock('GET', '/inventory/lots', ({ query }) => {
  const rows = lotRows()
    .filter((r) => matches(query.q, r.lotCode, r.sku, r.productName))
    .filter((r) => !query.locationId || r.locationId === Number(query.locationId))
    .filter((r) => r.available + r.held + r.quarantine + r.expired > 0)
    .sort(byFefo);
  return paginate(rows, query);
});

mock('GET', '/inventory/near-expiry', ({ query }) => paginate(nearExpiryRows().filter((r) => matches(query.q, r.lotCode, r.sku, r.productName)), query));

mock('GET', '/inventory/ledger', ({ query }) => {
  const rows = db.ledger
    .map((e) => ({
      ...e, ...variantLabel(e.variantId), lotCode: db.lots.find((l) => l.id === e.lotId)?.lotCode ?? null, locationCode: locationCode(e.locationId),
      toLocationCode: locationCode(e.toLocationId), creatorName: userName(e.createdBy),
    }))
    .filter((e) => matches(query.q, e.sku, e.productName, e.lotCode, e.note))
    .filter((e) => !query.entryType || e.entryType === query.entryType)
    .filter((e) => {
      if (!query.from && !query.to) return true;
      const day = e.createdAt.slice(0, 10);
      if (query.from && day < String(query.from)) return false;
      if (query.to && day > String(query.to)) return false;
      return true;
    })
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
  return paginate(rows, query);
});

// Ô chọn vị trí chỉ lấy kho đang dùng; kho ngừng dùng luôn đã hết hàng.
mock('GET', '/locations', () =>
  db.locations
    .filter((l) => db.warehouses.find((w) => w.id === l.warehouseId)?.isActive)
    .map((l) => ({ ...l, label: [locationCode(l.id), l.zone].filter(Boolean).join(' · ') })),
);

mock('POST', '/inventory/moves', ({ body, userId }) => {
  const source = db.stock.find((s) => s.id === body.stockId);
  if (!source) fail(404, 'Không tìm thấy dòng tồn');
  const qty = Number(body.qty);
  if (!Number.isInteger(qty) || qty <= 0) fail(400, 'Số lượng phải là số nguyên dương');
  if (qty > source.qtyAvailable) fail(409, 'Vượt số lượng khả dụng ở vị trí hiện tại');
  if (source.locationId === body.toLocationId) fail(400, 'Vị trí đích trùng vị trí hiện tại');
  source.qtyAvailable -= qty;
  let target = db.stock.find((s) => s.lotId === source.lotId && s.locationId === body.toLocationId);
  if (!target) {
    target = { ...source, id: db.stock.reduce((m, s) => Math.max(m, s.id), 0) + 1, locationId: body.toLocationId, qtyAvailable: 0, qtyHeld: 0, qtyQuarantine: 0, qtyExpired: 0 };
    db.stock.push(target);
  }
  target.qtyAvailable += qty;
  appendLedger({ variantId: source.variantId, lotId: source.lotId, locationId: source.locationId, toLocationId: body.toLocationId, entryType: 'move', qtyDelta: qty, balanceAfter: target.qtyAvailable, refType: null, refId: null, note: 'Chuyển vị trí', createdBy: userId });
  audit(userId, 'move', 'stock', source.id, { qty, to: body.toLocationId });
});

mock('POST', '/inventory/adjusts', ({ body, userId }) => {
  const source = db.stock.find((s) => s.id === body.stockId);
  if (!source) fail(404, 'Không tìm thấy dòng tồn');
  const qtyDelta = Number(body.qtyDelta);
  const note = String(body.note ?? '').trim();
  if (!Number.isInteger(qtyDelta) || qtyDelta === 0) fail(400, 'Số lượng điều chỉnh phải khác 0');
  if (!note) fail(400, 'Vui lòng nhập lý do điều chỉnh');
  if (qtyDelta < 0 && Math.abs(qtyDelta) > source.qtyAvailable) fail(409, 'Không đủ tồn kho');
  source.qtyAvailable += qtyDelta;
  appendLedger({
    variantId: source.variantId,
    lotId: source.lotId,
    locationId: source.locationId,
    toLocationId: null,
    entryType: 'adjust',
    qtyDelta,
    balanceAfter: source.qtyAvailable,
    refType: null,
    refId: null,
    note,
    createdBy: userId,
  });
  audit(userId, 'adjust', 'stock', source.id, { qtyDelta, note });
});
