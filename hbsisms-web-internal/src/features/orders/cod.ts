import type { FulfillmentMethod, PaymentMethod } from '@/shared/domain/enums';

/**
 * Khớp hình thức giao ↔ thanh toán:
 * - Tiền mặt + shop tự giao → COD (thu khi giao)
 * - COD + khách đến lấy → tiền mặt (không có chuyến giao)
 */
export function normalizePaymentForFulfillment(
  method: PaymentMethod,
  fulfillment: FulfillmentMethod,
): PaymentMethod {
  if (method === 'cash' && fulfillment === 'shop_delivery') return 'cod';
  if (method === 'cod' && fulfillment === 'pickup') return 'cash';
  return method;
}

/** Đơn thu khi giao (COD trên đơn giao, hoặc cash+shop_delivery chưa normalize). */
export function isCollectOnDelivery(
  method: PaymentMethod,
  fulfillment: FulfillmentMethod,
): boolean {
  if (method === 'cod') return fulfillment !== 'pickup';
  return method === 'cash' && fulfillment === 'shop_delivery';
}
