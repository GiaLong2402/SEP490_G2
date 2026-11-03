/**
 * Chuẩn hóa số điện thoại Việt Nam về dạng 84xxxxxxxxx (không dấu +).
 * Chấp nhận: 09xxxxxxxx, 9xxxxxxxx, +849xxxxxxxx, 849xxxxxxxx
 */
export function normalizeVietnamPhone(raw) {
  if (!raw) return ''
  let digits = String(raw).replace(/\D/g, '')

  if (digits.startsWith('84')) {
    digits = digits.slice(2)
  }
  if (digits.startsWith('0')) {
    digits = digits.slice(1)
  }

  return digits
}

/** Kiểm tra số VN hợp lệ sau khi chuẩn hóa (9–10 số, thường đầu 3/5/7/8/9) */
export function isValidVietnamPhone(raw) {
  const local = normalizeVietnamPhone(raw)
  return /^[3-9]\d{8}$/.test(local)
}

/** Hiển thị dạng +84 912 345 678 */
export function formatPhoneDisplay(raw) {
  const local = normalizeVietnamPhone(raw)
  if (!local) return ''
  const groups = local.match(/.{1,3}/g) || [local]
  return `+84 ${groups.join(' ')}`
}
