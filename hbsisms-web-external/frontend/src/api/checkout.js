import { api } from './client'
import { checkDto } from './mappers/checkDto'
import { fromOrderDto } from './mappers/order'

/**
 * Checkout — task "Checkout" (Duy). Hợp đồng: API_CONTRACT.md § Checkout.
 * - 201: { order: OrderDetailResponse, payment: { payosOrderCode, amount, checkoutUrl, qrCode, expiresAt } }
 * - 409: { message, shortages: [{ variantId, requestedQty, availableQty }] } → ApiError.payload
 */
export const checkoutApi = {
  /**
   * cartItems: dòng trong CartContext; options: { fulfillmentMethod, shipAddressId, requestedDate, timeWindow, note }
   * idempotencyKey: tạo 1 lần cho mỗi lần bấm "Đặt hàng" (bấm lại / mạng chập chờn → vẫn 1 đơn)
   */
  async create(cartItems, options, idempotencyKey) {
    const body = {
      ...options,
      paymentMethod: 'PAYOS',
      lines: cartItems.map((item) => ({
        variantId: Number(item.variant.id) || item.variant.id,
        qtyKhay: item.quantity,
        // Backend tự tính lại giá (PriceResolver); gửi kèm chỉ để mock hiển thị
        unitPrice: item.variant.price,
        productName: item.name,
        packLabel: item.variant.label,
        imageUrl: item.image,
      })),
    }
    const data = await api.post('/checkout', body, { 'Idempotency-Key': idempotencyKey })
    checkDto(data, ['order', 'payment.payosOrderCode', 'payment.amount', 'payment.checkoutUrl', 'payment.expiresAt'], 'POST /checkout', '4. Checkout')
    return { order: fromOrderDto(data.order), payment: data.payment }
  },
}

export function newIdempotencyKey() {
  return globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(16).slice(2)}`
}
