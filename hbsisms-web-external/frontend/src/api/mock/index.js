/**
 * MOCK API phía khách — chỉ nạp khi VITE_USE_MOCK=true (xem api/client.js).
 *
 * Đổi kịch bản bằng tham số ?mock= trên URL trang đang mở:
 *   ?mock=shortage       → POST /checkout trả 409 thiếu hàng
 *   ?mock=pay-pending    → trang kết quả: PayOS chưa xác nhận (luôn PENDING)
 *   ?mock=pay-cancelled  → khách hủy trên PayOS
 *   ?mock=pay-failed     → thanh toán lỗi
 *   ?mock=pay-expired    → hết hạn giữ hàng
 *   ?mock=empty          → danh sách rỗng
 * Mặc định: 2 lần hỏi đầu PENDING, từ lần 3 PAID (giả lập webhook PayOS đến chậm).
 */
import { ApiError } from '../client'
import { addresses, orders, salesRequests } from './data'

export const NOT_MOCKED = Symbol('NOT_MOCKED')

/* Giữ dữ liệu giả qua lần tải lại trang (vd. quay về từ "PayOS") — như backend lưu DB */
const STORE_KEY = 'hbsisms.mock.v1'
function restore() {
  try {
    const saved = JSON.parse(sessionStorage.getItem(STORE_KEY) || 'null')
    if (!saved) return
    addresses.splice(0, addresses.length, ...saved.addresses)
    orders.splice(0, orders.length, ...saved.orders)
    salesRequests.splice(0, salesRequests.length, ...saved.salesRequests)
  } catch {
    // dữ liệu hỏng → dùng mặc định
  }
}
function persist() {
  try {
    sessionStorage.setItem(STORE_KEY, JSON.stringify({ addresses, orders, salesRequests }))
  } catch {
    // bỏ qua (chế độ ẩn danh / hết dung lượng)
  }
}
restore()

const scenario = () => new URLSearchParams(window.location.search).get('mock') || ''
const delay = (ms = 350) => new Promise((r) => window.setTimeout(r, ms))
const clone = (v) => JSON.parse(JSON.stringify(v))
// Link "PayOS" giả: quay về trang kết quả như returnUrl thật của PayOS
const mockCheckoutUrl = (orderCode) => `/thanh-toan/ket-qua?code=00&status=PAID&cancel=false&orderCode=${orderCode}`
const page = (items) => ({ items, total: items.length, page: 1, pageSize: items.length || 20 })

let nextId = 1000
const idempotency = new Map()
const returnPolls = new Map()

function notFound(what) {
  throw new ApiError(404, `Không tìm thấy ${what}`)
}

function findOrder(code) {
  return orders.find((o) => o.code === code) || notFound('đơn hàng')
}

function setDefault(id) {
  addresses.forEach((a) => {
    a.isDefault = a.id === id
  })
}

/* ---------- /me/addresses ---------- */
function addressRoutes(method, parts, body) {
  if (parts.length === 2 && method === 'GET') return clone(addresses)
  if (parts.length === 2 && method === 'POST') {
    const created = { ...body, id: nextId++, customerId: 1, kind: 'SHIPPING', inDeliveryRadius: true, createdAt: new Date().toISOString() }
    addresses.push(created)
    if (body.isDefault || addresses.length === 1) setDefault(created.id)
    return clone(created)
  }
  const id = Number(parts[2])
  const idx = addresses.findIndex((a) => a.id === id)
  if (idx < 0) notFound('địa chỉ')
  if (method === 'PUT') {
    addresses[idx] = { ...addresses[idx], ...body }
    if (body.isDefault) setDefault(id)
    return clone(addresses[idx])
  }
  if (method === 'DELETE') {
    if (addresses[idx].isDefault && addresses.length > 1) {
      throw new ApiError(409, 'Không xóa được địa chỉ mặc định. Hãy chọn địa chỉ mặc định khác trước.')
    }
    addresses.splice(idx, 1)
    return undefined
  }
  return NOT_MOCKED
}

/* ---------- /me/orders ---------- */
function orderRoutes(method, parts) {
  if (parts.length === 2 && method === 'GET') return page(scenario() === 'empty' ? [] : clone(orders))
  const o = findOrder(decodeURIComponent(parts[2]))
  if (parts.length === 3 && method === 'GET') {
    // ?mock=overpaid: đơn đã trả nhưng khách chuyển thừa (paidAmount > total)
    if (scenario() === 'overpaid' && o.paymentStatus === 'PAID') return { ...clone(o), paidAmount: o.total + 8000 }
    return clone(o)
  }
  if (parts[3] === 'cancel' && method === 'POST') {
    if (!o.canCancel) throw new ApiError(409, 'Đơn này không thể hủy nữa.')
    o.state = 'CANCELLED'
    o.paymentStatus = 'UNPAID'
    o.canCancel = false
    o.holdExpiresAt = null
    o.events.unshift({ id: nextId++, eventType: 'CANCELLED', description: 'Khách hủy đơn', createdAt: new Date().toISOString() })
    return clone(o)
  }
  return NOT_MOCKED
}

/* ---------- /me/sales-requests ---------- */
function requestRoutes(method, parts, body) {
  if (parts.length === 2 && method === 'GET') return page(scenario() === 'empty' ? [] : clone(salesRequests))
  if (parts.length === 2 && method === 'POST') {
    const n = salesRequests.length + 8
    const created = {
      id: nextId++,
      code: `YC-${String(n).padStart(4, '0')}`,
      channel: 'WEB',
      status: 'NEW',
      receivedAt: new Date().toISOString(),
      feasible: null,
      fulfillableQty: null,
      shortageQty: null,
      expectedDate: null,
      rejectReason: null,
      orderCode: null,
      ...body,
    }
    salesRequests.unshift(created)
    return clone(created)
  }
  const r = salesRequests.find((x) => x.code === decodeURIComponent(parts[2])) || notFound('yêu cầu')
  if (parts.length === 3 && method === 'GET') return clone(r)
  return NOT_MOCKED
}

/* ---------- POST /checkout ---------- */
function checkout(body, headers) {
  const key = headers?.['Idempotency-Key']
  if (key && idempotency.has(key)) return clone(idempotency.get(key))

  if (scenario() === 'shortage') {
    throw new ApiError(409, 'Một số sản phẩm không đủ hàng', {
      message: 'Một số sản phẩm không đủ hàng',
      shortages: body.lines.map((l) => ({
        variantId: l.variantId,
        requestedQty: l.qtyKhay,
        availableQty: Math.max(0, Math.floor(l.qtyKhay / 2)),
      })),
    })
  }

  const n = 132 + orders.length
  const code = `HB${String(n).padStart(5, '0')}`
  const payosOrderCode = n * 1000
  // ?mock=hold-short | hold-short-paid: giữ hàng 20 giây để thử QR đóng / đang kiểm tra / hết hạn
  const holdMs = scenario().startsWith('hold-short') ? 20_000 : 15 * 60_000
  const expiresAt = new Date(Date.now() + holdMs).toISOString()
  const subtotal = body.lines.reduce((s, l) => s + (l.unitPrice || 0) * l.qtyKhay, 0)
  const shippingFee = body.fulfillmentMethod === 'PICKUP' ? 0 : 20000
  const created = {
    id: n,
    code,
    state: 'PENDING',
    paymentStatus: 'WAITING',
    paymentMethod: 'PAYOS',
    fulfillmentMethod: body.fulfillmentMethod,
    subtotal,
    shippingFee,
    discountTotal: 0,
    total: subtotal + shippingFee,
    placedAt: new Date().toISOString(),
    holdExpiresAt: expiresAt,
    customerName: 'Nguyễn Văn An',
    customer: { name: 'Nguyễn Văn An', phone: '0912345678' },
    address: addresses.find((a) => a.id === body.shipAddressId) || null,
    lines: body.lines.map((l, i) => ({
      id: i + 1,
      variantId: l.variantId,
      qtyKhay: l.qtyKhay,
      unitPrice: l.unitPrice || 0,
      lineTotal: (l.unitPrice || 0) * l.qtyKhay,
      productName: l.productName,
      packLabel: l.packLabel,
      imageUrl: l.imageUrl,
    })),
    deliveries: [{ id: 1, scheduledDate: body.requestedDate, timeWindow: body.timeWindow, fulfillmentMethod: body.fulfillmentMethod, state: 'READY' }],
    payments: [{ id: 1, method: 'PAYOS', direction: 'IN', status: 'PENDING', payosOrderCode, checkoutUrl: mockCheckoutUrl(payosOrderCode) }],
    events: [{ id: 1, eventType: 'PLACED', description: 'Đặt hàng thành công, chờ thanh toán', createdAt: new Date().toISOString() }],
    paidAmount: 0,
    canCancel: true,
    note: body.note || null,
  }
  orders.unshift(created)
  const response = {
    order: created,
    payment: {
      payosOrderCode,
      amount: created.total,
      checkoutUrl: created.payments[0].checkoutUrl,
      qrCode: `00020101021238570010A000000727MOCK${payosOrderCode}`,
      expiresAt,
    },
  }
  if (key) idempotency.set(key, response)
  return clone(response)
}

/* ---------- /payments/payos ---------- */
function findByPayos(orderCode) {
  return orders.find((o) => o.payments?.some((p) => String(p.payosOrderCode) === String(orderCode)))
}

function paymentReturn(query) {
  const o = findByPayos(query.orderCode)
  if (!o) notFound('giao dịch')
  const mode = scenario()
  const polls = (returnPolls.get(o.code) || 0) + 1
  returnPolls.set(o.code, polls)

  let status = polls > 2 ? 'PAID' : 'PENDING'
  if (mode === 'pay-pending') status = 'PENDING'
  if (mode === 'pay-cancelled') status = 'CANCELLED'
  if (mode === 'pay-failed') status = 'FAILED'
  if (mode === 'pay-expired') status = 'EXPIRED'
  if (mode === 'pay-refund') status = 'REFUND'
  if (mode === 'pay-order-cancelled') {
    // Cửa hàng đã hủy đơn trong lúc khách đang ở màn QR
    return { payosOrderCode: Number(query.orderCode), status: 'CANCELLED', amount: o.total, paidAt: null, order: { ...clone(o), state: 'CANCELLED', canCancel: false } }
  }
  if (mode.startsWith('hold-short')) {
    // Giống backend: chưa có tiền thì PENDING tới hết 2 phút ân hạn rồi EXPIRED.
    // hold-short-paid: tiền về trong lúc đang kiểm tra (khách quét QR sát giờ)
    const pastHold = Date.now() >= new Date(o.holdExpiresAt).getTime()
    const pastGrace = Date.now() >= new Date(o.holdExpiresAt).getTime() + 120_000
    status = mode === 'hold-short-paid' && pastHold ? 'PAID' : pastGrace ? 'EXPIRED' : 'PENDING'
  }
  if (o.paymentStatus === 'PAID') status = 'PAID'

  if (status === 'PAID' && o.paymentStatus !== 'PAID') {
    o.paymentStatus = 'PAID'
    o.state = 'CONFIRMED'
    o.canCancel = false
    o.paidAmount = o.total
    o.payments[0] = { ...o.payments[0], status: 'SUCCESS', paidAt: new Date().toISOString() }
    o.events.unshift({ id: nextId++, eventType: 'PAID', description: 'Thanh toán thành công qua PayOS', createdAt: new Date().toISOString() })
  }

  return {
    payosOrderCode: Number(query.orderCode),
    status,
    amount: o.total,
    paidAt: o.payments[0]?.paidAt || null,
    order: clone(o),
  }
}

function recreateLink(orderCode) {
  const o = findByPayos(orderCode)
  if (!o) notFound('giao dịch')
  const holdMs = scenario().startsWith('hold-short') ? 20_000 : 15 * 60_000
  const expiresAt = new Date(Date.now() + holdMs).toISOString()
  o.holdExpiresAt = expiresAt
  returnPolls.delete(o.code)
  return {
    payosOrderCode: Number(orderCode),
    amount: o.total,
    checkoutUrl: mockCheckoutUrl(orderCode),
    qrCode: `00020101021238570010A000000727MOCK${orderCode}`,
    expiresAt,
    order: clone(o),
  }
}

/**
 * Router giả: trả dữ liệu hoặc NOT_MOCKED (để client gọi API thật, vd. /products, /auth).
 */
export async function mockRequest(method, path, { body, query, headers } = {}) {
  const parts = path.split('?')[0].split('/').filter(Boolean)
  const [root, second] = parts
  const handled =
    (root === 'me' && ['addresses', 'orders', 'sales-requests'].includes(second)) ||
    root === 'checkout' ||
    (root === 'payments' && second === 'payos')
  if (!handled) return NOT_MOCKED

  await delay()
  let result = NOT_MOCKED
  if (root === 'me' && second === 'addresses') result = addressRoutes(method, parts, body)
  else if (root === 'me' && second === 'orders') result = orderRoutes(method, parts)
  else if (root === 'me' && second === 'sales-requests') result = requestRoutes(method, parts, body)
  else if (root === 'checkout' && method === 'POST') result = checkout(body, headers)
  else if (root === 'payments' && parts[2] === 'return') result = paymentReturn(query || {})
  else if (root === 'payments' && parts[3] === 'link' && method === 'POST') result = recreateLink(parts[2])
  if (method !== 'GET' || root === 'payments') persist()
  return result
}
