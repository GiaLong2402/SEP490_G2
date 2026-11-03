import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';
import { addOrderEvent, appendLedger, audit, confirmOrder, deductStock, releaseOrder, userName } from '@/shared/mock/services';

const refLink = (a: (typeof db.approvals)[number]) => {
  if (a.refType === 'sales_order') return `/orders/${a.refId}`;
  if (a.refType === 'damage_report') return '/damage';
  if (a.refType === 'stocktake') return `/stocktakes/${a.refId}`;
  if (a.refType === 'customer_price') {
    const price = db.customerPrices.find((p) => p.id === a.refId);
    return price ? `/customers/${price.customerId}` : null;
  }
  return null;
};

const toItem = (a: (typeof db.approvals)[number]) => ({ ...a, requesterName: userName(a.requestedBy), deciderName: userName(a.decidedBy), refLink: refLink(a) });

function applyOrder(refId: number, approved: boolean, userId: number | null, note: string) {
  const order = db.orders.find((o) => o.id === refId);
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  if (approved) {
    order.approvedBy = userId;
    addOrderEvent(order.id, 'approved', `Chủ cửa hàng duyệt vượt hạn mức${note ? `: ${note}` : ''}`, userId);
    confirmOrder(order, userId);
  } else {
    releaseOrder(order, userId);
    order.state = 'cancelled';
    order.updatedAt = new Date().toISOString();
    addOrderEvent(order.id, 'cancelled', `Từ chối duyệt vượt hạn mức${note ? `: ${note}` : ''}`, userId);
  }
}

function applyDamage(refId: number, approved: boolean, userId: number | null, note: string) {
  const report = db.damageReports.find((d) => d.id === refId);
  if (!report) fail(404, 'Không tìm thấy báo hỏng');
  report.status = approved ? 'approved' : 'rejected';
  report.reviewedBy = userId;
  report.reviewedAt = new Date().toISOString();
  report.note = note || report.note;
  if (approved) {
    const stock = deductStock(report.variantId, report.lotId, report.locationId, report.qty);
    appendLedger({ variantId: report.variantId, lotId: report.lotId, locationId: report.locationId, toLocationId: null, entryType: 'damage', qtyDelta: -report.qty, balanceAfter: stock?.qtyAvailable ?? null, refType: 'damage_report', refId: report.id, note: report.reason, createdBy: userId });
  }
}

function applyPrice(refId: number, approved: boolean, userId: number | null) {
  const price = db.customerPrices.find((p) => p.id === refId);
  if (!price) fail(404, 'Không tìm thấy đề xuất giá');
  const now = new Date().toISOString();
  if (approved) {
    price.unitPrice = price.pendingPrice ?? price.unitPrice;
    price.approvedBy = userId;
    price.approvedAt = now;
    price.status = 'approved';
  } else {
    // Đề xuất cho giá đã có thì giữ nguyên giá đang áp dụng.
    price.status = price.approvedAt ? 'approved' : 'rejected';
  }
  price.pendingPrice = null;
  price.updatedAt = now;
}

function applyStocktake(refId: number, approved: boolean, userId: number | null) {
  const row = db.stocktakes.find((s) => s.id === refId);
  if (!row) fail(404, 'Không tìm thấy phiếu kiểm kê');
  if (!approved) return; // Từ chối: phiếu quay lại trạng thái đang đếm để đếm lại.
  db.stocktakeLines.filter((l) => l.stocktakeId === row.id && l.diff !== 0).forEach((line) => {
    const stock = db.stock.find((s) => s.lotId === line.lotId && s.locationId === line.locationId);
    if (!stock) return;
    stock.qtyAvailable = Math.max(0, stock.qtyAvailable + line.diff);
    appendLedger({ variantId: line.variantId, lotId: line.lotId, locationId: line.locationId, toLocationId: null, entryType: 'adjust', qtyDelta: line.diff, balanceAfter: stock.qtyAvailable, refType: 'stocktake', refId: row.id, note: `Điều chỉnh theo kiểm kê ${row.code}`, createdBy: userId });
  });
  row.status = 'done';
  row.closedAt = new Date().toISOString();
}

mock('GET', '/approvals', ({ query }) => {
  const rows = db.approvals
    .filter((a) => matches(query.q, a.title))
    .filter((a) => !query.status || a.status === query.status)
    .filter((a) => !query.approvalType || a.approvalType === query.approvalType)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt));
  return paginate(rows.map(toItem), query);
});

mock('POST', '/approvals/:id/decision', ({ params, body, userId }) => {
  const task = db.approvals.find((a) => a.id === Number(params.id));
  if (!task) fail(404, 'Không tìm thấy việc cần duyệt');
  if (task.status !== 'pending') fail(409, 'Việc này đã được xử lý');
  const approved = body.decision === 'approved';
  if (task.refType === 'sales_order') applyOrder(task.refId, approved, userId, body.note);
  if (task.refType === 'damage_report') applyDamage(task.refId, approved, userId, body.note);
  if (task.refType === 'customer_price') applyPrice(task.refId, approved, userId);
  if (task.refType === 'stocktake') applyStocktake(task.refId, approved, userId);
  task.status = body.decision;
  task.decidedBy = userId;
  task.decidedAt = new Date().toISOString();
  task.note = body.note || null;
  audit(userId, approved ? 'approve' : 'reject', task.refType, task.refId, { note: body.note });
});
