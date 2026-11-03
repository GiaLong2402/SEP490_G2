import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { audit, createApproval, locationCode, userName, variantLabel } from '@/shared/mock/services';

function toItem(s: (typeof db.stocktakes)[number]) {
  const lines = db.stocktakeLines.filter((l) => l.stocktakeId === s.id);
  return {
    ...s, creatorName: userName(s.createdBy), counterName: userName(s.countedBy), lineCount: lines.length, countedCount: lines.filter((l) => l.countedQty !== null).length,
    diffTotal: lines.filter((l) => l.countedQty !== null).reduce((sum, l) => sum + l.diff, 0), pendingApproval: db.approvals.some((a) => a.refType === 'stocktake' && a.refId === s.id && a.status === 'pending'),
  };
}

function toDetail(id: number) {
  const row = db.stocktakes.find((s) => s.id === id);
  if (!row) fail(404, 'Không tìm thấy phiếu kiểm kê');
  return {
    ...toItem(row),
    lines: db.stocktakeLines.filter((l) => l.stocktakeId === id).map((l) => ({ ...l, ...variantLabel(l.variantId), lotCode: db.lots.find((x) => x.id === l.lotId)?.lotCode ?? null, locationCode: locationCode(l.locationId) })),
  };
}

mock('GET', '/stocktakes', ({ query }) => {
  const rows = db.stocktakes.filter((s) => matches(query.q, s.code, s.scope)).filter((s) => !query.status || s.status === query.status).sort((a, b) => b.id - a.id);
  return paginate(rows.map(toItem), query);
});

mock('GET', '/stocktakes/:id', ({ params }) => toDetail(Number(params.id)));

mock('POST', '/stocktakes', ({ body, userId }) => {
  const id = nextId(db.stocktakes);
  const zoneLocations = db.locations.filter((l) => !body.zone || l.zone === body.zone).map((l) => l.id);
  const stocks = db.stock.filter((s) => zoneLocations.includes(s.locationId) && s.qtyAvailable + s.qtyHeld > 0);
  if (!stocks.length) fail(409, 'Khu vực này không có hàng để kiểm kê');
  db.stocktakes.push({ id, code: nextCode('KK', db.stocktakes), scope: body.zone ?? 'Toàn kho', status: 'counting', createdBy: userId, countedBy: userId, startedAt: new Date().toISOString(), closedAt: null, note: body.note });
  stocks.forEach((s) => db.stocktakeLines.push({ id: nextId(db.stocktakeLines), stocktakeId: id, variantId: s.variantId, lotId: s.lotId, locationId: s.locationId, systemQty: s.qtyAvailable + s.qtyHeld, countedQty: null, diff: 0 }));
  return toDetail(id);
});

mock('PUT', '/stocktakes/:id/lines', ({ params, body, userId }) => {
  const row = db.stocktakes.find((s) => s.id === Number(params.id));
  if (!row || row.status !== 'counting') fail(409, 'Phiếu không còn ở trạng thái đang đếm');
  if (toItem(row).pendingApproval) fail(409, 'Phiếu đang chờ duyệt, không thể sửa số đếm');
  body.counts.forEach(({ lineId, countedQty }: { lineId: number; countedQty: number | null }) => {
    const line = db.stocktakeLines.find((l) => l.id === lineId && l.stocktakeId === row.id);
    if (!line) return;
    line.countedQty = countedQty;
    line.diff = (countedQty ?? 0) - line.systemQty;
  });
  row.countedBy = userId;
});

mock('POST', '/stocktakes/:id/complete', ({ params, userId }) => {
  const row = db.stocktakes.find((s) => s.id === Number(params.id));
  if (!row || row.status !== 'counting') fail(409, 'Phiếu không còn ở trạng thái đang đếm');
  if (toItem(row).pendingApproval) fail(409, 'Phiếu đã gửi duyệt');
  const lines = db.stocktakeLines.filter((l) => l.stocktakeId === row.id);
  if (lines.some((l) => l.countedQty === null)) fail(409, 'Còn dòng chưa đếm');
  const diffs = lines.filter((l) => l.diff !== 0);
  if (diffs.length === 0) {
    row.status = 'done';
    row.closedAt = new Date().toISOString();
    audit(userId, 'complete', 'stocktake', row.id);
    return { needsApproval: false };
  }
  createApproval('stocktake', 'stocktake', row.id, `${row.code}: chênh lệch ${diffs.length} dòng, tổng ${diffs.reduce((s, l) => s + l.diff, 0)} khay`, userId);
  return { needsApproval: true };
});

mock('POST', '/stocktakes/:id/cancel', ({ params, userId }) => {
  const row = db.stocktakes.find((s) => s.id === Number(params.id));
  if (!row || row.status !== 'counting') fail(409, 'Chỉ hủy được phiếu đang đếm');
  if (toItem(row).pendingApproval) fail(409, 'Phiếu đang chờ duyệt, không thể hủy');
  row.status = 'cancelled';
  row.closedAt = new Date().toISOString();
  audit(userId, 'cancel', 'stocktake', row.id);
});
