import type { FulfillmentMethod, PaymentMethod } from '@/shared/domain/enums';

/** Tiền mặt + shop tự giao → COD (thu khi giao). */
export function normalizePaymentForFulfillment(
  method: PaymentMethod,
  fulfillment: FulfillmentMethod,
): PaymentMethod {
  if (method === 'cash' && fulfillment === 'shop_delivery') return 'cod';
  return method;
}

/** Đơn thu khi giao (COD hoặc cash+shop_delivery chưa normalize). */
export function isCollectOnDelivery(
  method: PaymentMethod,
  fulfillment: FulfillmentMethod,
): boolean {
  if (method === 'cod') return true;
  return method === 'cash' && fulfillment === 'shop_delivery';
}
