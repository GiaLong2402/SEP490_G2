/**
 * DỮ LIỆU GIẢ (chỉ khi VITE_USE_MOCK=true) — cấu trúc giống DTO backend
 * (AddressResponse, OrderDetailResponse, SalesRequestResponse) để mapper chạy như thật.
 * Ngày giờ tính theo thời điểm mở trang.
 */
const IMG = {
  cha: '/images/demo-products/product-cha-muc.png',
  muc: '/images/demo-products/product-muc-ong.png',
  tom: '/images/demo-products/product-tom-su.png',
  ca: '/images/demo-products/product-ca-thu.png',
}

const now = Date.now()
const ago = (hours) => new Date(now - hours * 3600_000).toISOString()
const dayIso = (offsetDays) => new Date(now + offsetDays * 86400_000).toISOString().slice(0, 10)

export const addresses = [
  {
    id: 1,
    customerId: 1,
    kind: 'SHIPPING',
    recipientName: 'Nguyễn Văn An',
    phone: '0912345678',
    line1: 'Số 15 Đường Hòa Bình',
    ward: 'Phường Hồng Bàng',
    district: '',
    province: 'Hải Phòng',
    latitude: null,
    longitude: null,
    inDeliveryRadius: true,
    distanceKm: 3.2,
    isDefault: true,
    createdAt: ago(24 * 30),
  },
]

function line(id, name, pack, qty, price, image) {
  return {
    id,
    variantId: id,
    unit: 'PACK',
    qty,
    qtyKhay: qty,
    unitPrice: price,
    lineTotal: qty * price,
    productName: name,
    packLabel: pack,
    imageUrl: image, // đề xuất bổ sung cho phía khách
  }
}

function order({ code, state, paymentStatus, fulfillment, hoursAgo, lines, scheduled, timeWindow, events, payment }) {
  const subtotal = lines.reduce((s, l) => s + l.lineTotal, 0)
  const shippingFee = fulfillment === 'PICKUP' ? 0 : 20000
  return {
    id: Number(code.replace(/\D/g, '')),
    code,
    customerId: 1,
    orderType: 'RETAIL',
    channel: 'WEB',
    paymentMethod: 'PAYOS',
    paymentStatus,
    fulfillmentMethod: fulfillment,
    shipAddressId: fulfillment === 'PICKUP' ? null : 1,
    state,
    subtotal,
    discountTotal: 0,
    shippingFee,
    total: subtotal + shippingFee,
    placedAt: ago(hoursAgo),
    holdExpiresAt: state === 'PENDING' ? new Date(now + 14 * 60_000).toISOString() : null,
    note: null,
    customerName: 'Nguyễn Văn An',
    address: fulfillment === 'PICKUP' ? null : addresses[0],
    customer: { name: 'Nguyễn Văn An', phone: '0912345678' },
    lines,
    deliveries: [
      {
        id: 1,
        scheduledDate: scheduled,
        timeWindow,
        fulfillmentMethod: fulfillment,
        state: state === 'TO_SHIP' ? 'GOING' : state === 'COMPLETED' ? 'DELIVERED' : 'READY',
      },
    ],
    payments: payment ? [payment] : [],
    events,
    paidAmount: paymentStatus === 'PAID' ? subtotal + shippingFee : 0,
    canCancel: state === 'PENDING' && paymentStatus !== 'PAID',
  }
}

const ev = (id, hoursAgo, description) => ({ id, eventType: 'NOTE', description, createdAt: ago(hoursAgo) })
const paid = (hoursAgo) => ({
  id: 1,
  method: 'PAYOS',
  direction: 'IN',
  status: 'SUCCESS',
  gateway: 'payos',
  paidAt: ago(hoursAgo),
  checkoutUrl: null,
})

export const orders = [
  order({
    code: 'HB00131',
    state: 'PENDING',
    paymentStatus: 'WAITING',
    fulfillment: 'SHOP_DELIVERY',
    hoursAgo: 0.1,
    scheduled: dayIso(1),
    timeWindow: '10:00-12:00',
    lines: [line(31, 'Mực nguyên con làm sạch', 'Khay 500g', 2, 120000, IMG.muc)],
    events: [ev(1, 0.1, 'Đặt hàng thành công, chờ thanh toán')],
    payment: {
      id: 2,
      method: 'PAYOS',
      direction: 'IN',
      status: 'PENDING',
      gateway: 'payos',
      payosOrderCode: 131000,
      // Mock: link "PayOS" quay thẳng về trang kết quả (giống returnUrl thật)
      checkoutUrl: '/thanh-toan/ket-qua?code=00&status=PAID&cancel=false&orderCode=131000',
      paidAt: null,
    },
  }),
  order({
    code: 'HB00128',
    state: 'TO_SHIP',
    paymentStatus: 'PAID',
    fulfillment: 'SHOP_DELIVERY',
    hoursAgo: 3,
    scheduled: dayIso(0),
    timeWindow: '10:00-12:00',
    lines: [
      line(28, 'Mực ống loại 1', 'Khay 500g', 2, 140000, IMG.muc),
      line(29, 'Cá thu cắt khúc', 'Khay 500g', 1, 150000, IMG.ca),
    ],
    events: [
      ev(4, 1, 'Đơn hàng đang được giao'),
      ev(3, 2, 'Cửa hàng đang chuẩn bị hàng'),
      ev(2, 2.8, 'Thanh toán thành công qua PayOS'),
      ev(1, 3, 'Đặt hàng thành công'),
    ],
    payment: paid(2.8),
  }),
  order({
    code: 'HB00125',
    state: 'PREPARING',
    paymentStatus: 'PAID',
    fulfillment: 'PICKUP',
    hoursAgo: 20,
    scheduled: dayIso(1),
    timeWindow: '14:00-16:00',
    lines: [line(25, 'Chả mực Hạ Long', 'Khay 500g', 3, 110000, IMG.cha)],
    events: [ev(2, 19, 'Thanh toán thành công qua PayOS'), ev(1, 20, 'Đặt hàng thành công')],
    payment: paid(19),
  }),
  order({
    code: 'HB00112',
    state: 'COMPLETED',
    paymentStatus: 'PAID',
    fulfillment: 'SHOP_DELIVERY',
    hoursAgo: 24 * 5,
    scheduled: dayIso(-5),
    timeWindow: '08:00-10:00',
    lines: [line(12, 'Chả mực Hạ Long', 'Khay 500g', 2, 110000, IMG.cha)],
    events: [ev(3, 24 * 5 - 3, 'Đã giao hàng'), ev(2, 24 * 5 - 0.5, 'Thanh toán thành công qua PayOS'), ev(1, 24 * 5, 'Đặt hàng thành công')],
    payment: paid(24 * 5 - 0.5),
  }),
  order({
    code: 'HB00097',
    state: 'CANCELLED',
    paymentStatus: 'UNPAID',
    fulfillment: 'SHOP_DELIVERY',
    hoursAgo: 24 * 60,
    scheduled: dayIso(-59),
    timeWindow: '16:00-18:00',
    lines: [line(97, 'Cá thu cắt khúc', 'Khay 500g', 2, 150000, IMG.ca)],
    events: [ev(2, 24 * 60 - 0.3, 'Khách hủy đơn'), ev(1, 24 * 60, 'Đặt hàng thành công')],
  }),
  // Job hết hạn của backend hủy đơn: có sự kiện eventType 'expired' → giao diện hiện "Hết hạn"
  order({
    code: 'HB00090',
    state: 'CANCELLED',
    paymentStatus: 'UNPAID',
    fulfillment: 'SHOP_DELIVERY',
    hoursAgo: 26,
    scheduled: dayIso(0),
    timeWindow: '10:00-12:00',
    lines: [line(90, 'Tôm sú', 'Khay 500g', 1, 180000, IMG.tom)],
    events: [
      { id: 2, eventType: 'expired', description: 'Hết hạn thanh toán (15 phút) — hủy đơn và giải phóng hold', createdAt: ago(25.7) },
      ev(1, 26, 'Đặt hàng thành công, chờ thanh toán'),
    ],
  }),
]

export const salesRequests = [
  {
    id: 7,
    code: 'YC-0007',
    channel: 'WEB',
    summary: 'Chả mực Hạ Long · Khay 500g × 30 khay',
    status: 'NEW',
    receivedAt: ago(5),
    neededDate: dayIso(3),
    requestedQty: 30,
    availableQty: 12,
    feasible: null,
    fulfillableQty: null,
    shortageQty: null,
    expectedDate: null,
    rejectReason: null,
    orderCode: null,
  },
  {
    id: 6,
    code: 'YC-0006',
    channel: 'WEB',
    summary: 'Tôm sú · Khay 1kg × 15 khay',
    status: 'QUOTING',
    receivedAt: ago(30),
    neededDate: dayIso(2),
    requestedQty: 15,
    availableQty: 6,
    feasible: true,
    fulfillableQty: 15,
    shortageQty: 0,
    expectedDate: dayIso(2),
    rejectReason: null,
    orderCode: null,
  },
  {
    id: 5,
    code: 'YC-0005',
    channel: 'WEB',
    summary: 'Chả tôm · Khay 1kg × 12 khay',
    status: 'ORDERED',
    receivedAt: ago(24 * 4),
    neededDate: dayIso(-1),
    requestedQty: 12,
    availableQty: 4,
    feasible: true,
    fulfillableQty: 12,
    shortageQty: 0,
    expectedDate: dayIso(-1),
    rejectReason: null,
    orderCode: 'HB00131',
  },
  {
    id: 3,
    code: 'YC-0003',
    channel: 'WEB',
    summary: 'Chả mực Hạ Long · Khay 1kg × 30 khay',
    status: 'REJECTED',
    receivedAt: ago(24 * 20),
    neededDate: dayIso(-18),
    requestedQty: 30,
    availableQty: 5,
    feasible: false,
    fulfillableQty: 10,
    shortageQty: 20,
    expectedDate: null,
    rejectReason:
      'Lượng yêu cầu vượt khả năng chế biến trong 2 ngày. Bạn có thể gửi lại với số lượng nhỏ hơn hoặc ngày nhận muộn hơn.',
    orderCode: null,
  },
]
