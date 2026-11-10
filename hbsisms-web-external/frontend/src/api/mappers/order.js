import { orderUiStatus } from '@/constants/orderStatus'
import { formatAddress } from '@/utils/address'
import { checkDto } from './checkDto'

/** Field giao diện bắt buộc cần (API_CONTRACT.md § 2) */
const REQUIRED = ['id', 'code', 'state', 'paymentStatus', 'fulfillmentMethod', 'total', 'placedAt', 'canCancel', 'lines']
const REQUIRED_LINE = ['productName', 'qtyKhay', 'unitPrice', 'lineTotal']

const num = (v) => (v == null ? 0 : Number(v))

/**
 * OrderDetailResponse / OrderListItemResponse (backend) → đơn dùng trên giao diện.
 * Field nào backend chưa trả thì để mặc định, trang không bị vỡ.
 */
export function fromOrderDto(dto) {
  checkDto(dto, REQUIRED, 'Order (OrderDetailResponse)', '2. Đơn hàng')
  if (dto?.lines?.[0]) checkDto(dto.lines[0], REQUIRED_LINE, 'Order.lines[]', '2. Đơn hàng')
  const isDelivery = dto.fulfillmentMethod !== 'PICKUP'
  const delivery = dto.deliveries?.[0] || null
  const payment = [...(dto.payments || [])].reverse().find((p) => p.direction !== 'OUT') || null

  return {
    id: dto.id,
    code: dto.code,
    displayCode: `#${dto.code}`,
    placedAt: dto.placedAt || dto.createdAt || null,
    state: dto.state,
    paymentStatus: dto.paymentStatus,
    uiStatus: orderUiStatus(dto.state, dto.paymentStatus),
    isPaid: dto.paymentStatus === 'PAID',
    canCancel: Boolean(dto.canCancel),
    isDelivery,
    fulfillmentMethod: dto.fulfillmentMethod,
    subtotal: num(dto.subtotal),
    shippingFee: num(dto.shippingFee),
    discount: num(dto.discountTotal),
    total: num(dto.total),
    paidAmount: num(dto.paidAmount),
    holdExpiresAt: dto.holdExpiresAt || null,
    note: dto.note || '',
    receiver: {
      name: dto.address?.recipientName || dto.customer?.name || dto.customerName || '',
      phone: dto.address?.phone || dto.customer?.phone || '',
      address: isDelivery ? formatAddress(dto.address) : '',
    },
    schedule: delivery
      ? { date: delivery.scheduledDate || null, timeWindow: delivery.timeWindow || '' }
      : { date: null, timeWindow: '' },
    lines: (dto.lines || []).map((l) => ({
      id: l.id,
      variantId: l.variantId,
      name: l.productName || l.sku || 'Sản phẩm',
      packLabel: l.packLabel || '',
      qty: l.qtyKhay ?? l.qty ?? 0,
      unitPrice: num(l.unitPrice),
      lineTotal: num(l.lineTotal),
      imageUrl: l.imageUrl || '/images/demo-products/product-tom-su.png',
    })),
    events: [...(dto.events || [])]
      .sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
      .map((e) => ({ id: e.id, at: e.createdAt, title: e.description || e.eventType })),
    payment: payment
      ? {
          method: payment.method,
          status: payment.status,
          paidAt: payment.paidAt || null,
          checkoutUrl: payment.checkoutUrl || null,
          payosOrderCode: payment.payosOrderCode ?? null,
        }
      : null,
  }
}
