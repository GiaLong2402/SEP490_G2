/**
 * CHỈ KHI DEV: báo trên console nếu dữ liệu backend thiếu field mà giao diện cần.
 * Giúp người nối API thấy ngay chỗ lệch với API_CONTRACT.md, không phải dò từng màn.
 *
 *   checkDto(dto, ['id', 'code', 'payment.checkoutUrl'], 'OrderDetailResponse', '2. Đơn hàng')
 *
 * Bản build thật: hàm rỗng (không kiểm tra, không log).
 */
const warned = new Set()

function read(obj, path) {
  return path.split('.').reduce((v, key) => (v == null ? undefined : v[key]), obj)
}

export function checkDto(dto, fields, dtoName, contractSection) {
  if (!import.meta.env.DEV) return
  if (dto == null || typeof dto !== 'object') {
    warnOnce(`${dtoName}: backend trả về rỗng / không phải object`, contractSection)
    return
  }
  const missing = fields.filter((f) => read(dto, f) === undefined)
  if (missing.length) warnOnce(`${dtoName} thiếu field: ${missing.join(', ')}`, contractSection)
}

function warnOnce(message, contractSection) {
  if (warned.has(message)) return
  warned.add(message)
  console.warn(`[API] ${message} — xem API_CONTRACT.md § ${contractSection}`)
}
