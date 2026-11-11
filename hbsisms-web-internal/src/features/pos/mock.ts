import { fail, mock } from '@/shared/api/mockServer';
import { sellUnitLabels } from '@/shared/domain/enums';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { addOrderEvent, audit, availableQty, creditCheck, postDebtEntry, priceLines, requireUser, sellFefo, variantLabel } from '@/shared/mock/services';

const processed = new Map<string, unknown>();

mock('GET', '/pos/catalog', () =>
  db.variants
    .filter((v) => v.isActive && db.products.find((p) => p.id === v.productId)?.isActive)
    .map((v) => {
      const product = db.products.find((p) => p.id === v.productId)!;
      return {
        variantId: v.id, sku: v.sku, barcode: v.barcode, productName: product.name, packLabel: v.packLabel, categoryId: product.categoryId,
        categoryName: db.categories.find((c) => c.id === product.categoryId)?.name ?? null, listPrice: v.listPrice, packPerCase: v.packPerCase, available: availableQty(v.id),
      };
    }),
);

mock('POST', '/pos/checkout', ({ body, userId }) => {
  const cashier = requireUser(userId);
  if (body.clientRef && processed.has(body.clientRef)) return processed.get(body.clientRef);

  const customer = body.customerId ? db.customers.find((c) => c.id === body.customerId) : null;
  if (body.customerId && !customer) fail(400, 'Khách hàng không tồn tại');
  if (!['cash', 'bank_transfer', 'credit_net'].includes(body.paymentMethod)) fail(400, 'Phương thức thanh toán không hợp lệ');
  if (body.paymentMethod === 'credit_net' && customer?.customerType !== 'b2b') fail(400, 'Chỉ khách B2B được mua công nợ');
  if (!body.lines?.length) fail(400, 'Đơn chưa có sản phẩm');

  const priced = priceLines(customer?.id ?? null, body.lines);
  priced.forEach((line) => {
    if (line.qtyKhay > line.available) fail(409, `Không đủ hàng: ${variantLabel(line.variantId).sku} chỉ còn ${line.available} khay`);
  });
  const subtotal = priced.reduce((sum, l) => sum + l.lineTotal, 0);
  const discountTotal = Number(body.discountTotal ?? 0);
  if (discountTotal < 0 || discountTotal > subtotal) fail(400, 'Giảm giá không hợp lệ');
  const total = subtotal - discountTotal;
  if (Number(body.expectedTotal) !== total) fail(409, 'Giá vừa thay đổi, vui lòng kiểm tra lại đơn');
  const credit = creditCheck(customer?.id ?? null, body.paymentMethod, total);
  if (credit.isOverLimit) fail(409, `Vượt hạn mức ${credit.overLimitAmount.toLocaleString('vi-VN')} ₫. Hãy thu tiền hoặc tạo đơn thường để xin duyệt`);
  const tendered = body.paymentMethod === 'cash' ? (body.tendered === null ? total : Number(body.tendered)) : null;
  if (tendered !== null && tendered < total) fail(400, 'Tiền khách đưa chưa đủ');

  const now = new Date().toISOString();
  const id = nextId(db.orders);
  const isCredit = body.paymentMethod === 'credit_net';
  const order: (typeof db.orders)[number] = {
    id, code: nextCode('SO-2026', db.orders), customerId: customer?.id ?? null, orderType: customer?.customerType === 'b2b' ? 'b2b' : 'retail', channel: 'counter', sourceQuoteId: null, paymentMethod: body.paymentMethod,
    paymentTermDays: isCredit ? customer!.netDays : 0, paymentStatus: isCredit ? 'debt' : 'paid', allocationStatus: 'full', fulfillmentMethod: 'pickup', shipAddressId: null, state: 'completed', subtotal, discountTotal, shippingFee: 0, total,
    voucherCode: null, isOverLimit: false, overLimitAmount: 0, approvedBy: null, placedBy: cashier.id, placedAt: now, holdExpiresAt: null, note: body.note, createdAt: now, updatedAt: now,
  };
  db.orders.push(order);
  priced.forEach(({ available: _available, shortfall: _shortfall, ...line }) => {
    const lineId = nextId(db.orderLines);
    db.orderLines.push({ ...line, id: lineId, orderId: id, outstandingQty: 0, note: null });
    sellFefo(line.variantId, line.qtyKhay, lineId, cashier.id);
  });
  addOrderEvent(id, 'completed', 'Bán tại quầy (POS), khách nhận hàng ngay', cashier.id);

  if (isCredit) {
    const due = new Date();
    due.setDate(due.getDate() + customer!.netDays);
    postDebtEntry(customer!.id, { orderId: id, entryType: 'charge', amount: total, dueDate: due.toISOString().slice(0, 10), note: `Đơn ${order.code}` });
  } else if (total > 0) {
    db.payments.push({
      id: nextId(db.payments), code: nextCode('TT', db.payments), orderId: id, customerId: customer?.id ?? null, method: body.paymentMethod, direction: 'in', amount: total, status: 'success', reference: body.reference,
      gateway: body.paymentMethod === 'cash' ? 'cash' : 'bank', payosOrderCode: null, checkoutUrl: null, paidAt: now, receivedBy: cashier.id, note: 'Bán tại quầy',
    });
  }
  audit(cashier.id, 'create', 'sales_order', id, { channel: 'pos', total });

  const receipt = {
    orderId: id, code: order.code, placedAt: now, customerName: customer?.name ?? 'Khách lẻ', cashierName: cashier.fullName, paymentMethod: body.paymentMethod,
    lines: priced.map((l) => {
      const label = variantLabel(l.variantId);
      return { variantId: l.variantId, productName: label.productName, packLabel: label.packLabel, qtyLabel: `${l.qty} ${sellUnitLabels[l.unit].toLowerCase()}`, unitPrice: l.unitPrice, lineTotal: l.lineTotal };
    }),
    subtotal, discountTotal, total, tendered, change: tendered === null ? null : tendered - total, debtAfter: isCredit ? customer!.currentDebt : null,
  };
  if (body.clientRef) processed.set(body.clientRef, receipt);
  return receipt;
});
