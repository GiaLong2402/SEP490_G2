import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { audit, createApproval, locationCode, userName, variantLabel } from '@/shared/mock/services';

const toItem = (d: (typeof db.damageReports)[number]) => ({
  ...d, ...variantLabel(d.variantId), lotCode: db.lots.find((l) => l.id === d.lotId)?.lotCode ?? null, locationCode: locationCode(d.locationId),
  reporterName: userName(d.reportedBy), reviewerName: userName(d.reviewedBy),
});

mock('GET', '/damage-reports', ({ query }) => {
  const rows = db.damageReports.map(toItem).filter((d) => matches(query.q, d.code, d.sku, d.productName, d.lotCode)).filter((d) => !query.status || d.status === query.status).sort((a, b) => b.reportedAt.localeCompare(a.reportedAt));
  return paginate(rows, query);
});

mock('POST', '/damage-reports', ({ body, userId }) => {
  const stock = db.stock.find((s) => s.lotId === body.lotId && s.locationId === body.locationId);
  if (!stock) fail(400, 'Lô không có hàng tại vị trí đã chọn');
  const qty = Number(body.qty);
  const onHand = stock.qtyAvailable + stock.qtyQuarantine + stock.qtyExpired;
  if (!Number.isInteger(qty) || qty <= 0) fail(400, 'Số lượng phải là số nguyên dương');
  if (qty > onHand) fail(409, `Vượt số lượng có thể báo hỏng (${onHand} khay)`);
  const row = { id: nextId(db.damageReports), code: nextCode('BH', db.damageReports), variantId: body.variantId, lotId: body.lotId, locationId: body.locationId, qty, reason: body.reason, status: 'pending' as const, reportedBy: userId, reviewedBy: null, reportedAt: new Date().toISOString(), reviewedAt: null, note: body.note };
  db.damageReports.push(row);
  createApproval('damage_report', 'damage_report', row.id, `${row.code}: ${qty} khay ${variantLabel(body.variantId).sku} — ${body.reason}`, userId);
  audit(userId, 'create', 'damage_report', row.id);
  return toItem(row);
});
