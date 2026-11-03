import { mock } from '@/shared/api/mockServer';
import { creditCheck, priceLines } from '@/shared/mock/services';

mock('POST', '/pricing/resolve', ({ body }) => {
  const lines = priceLines(body.customerId, body.lines);
  const subtotal = lines.reduce((sum, l) => sum + l.lineTotal, 0);
  const total = Math.max(0, subtotal - Number(body.discountTotal ?? 0) + Number(body.shippingFee ?? 0));
  return { lines, subtotal, total, ...creditCheck(body.customerId, body.paymentMethod ?? '', total) };
});
