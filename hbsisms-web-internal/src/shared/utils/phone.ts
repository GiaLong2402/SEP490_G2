/** Chuẩn hóa SĐT VN về 9 số local (không 0 đầu). */
export function normalizeVietnamPhone(raw: string): string {
  if (!raw) return ''
  let digits = String(raw).replace(/\D/g, '')
  if (digits.startsWith('84')) digits = digits.slice(2)
  if (digits.startsWith('0')) digits = digits.slice(1)
  return digits
}

export function isValidVietnamPhone(raw: string): boolean {
  const local = normalizeVietnamPhone(raw)
  return /^[3-9]\d{8}$/.test(local)
}

export function toVnMobile0(raw: string): string {
  const local = normalizeVietnamPhone(raw)
  return local ? `0${local}` : ''
}

export function toE164Vn(raw: string): string {
  const local = normalizeVietnamPhone(raw)
  return local ? `+84${local}` : ''
}

export function formatPhoneDisplay(raw: string): string {
  const local = normalizeVietnamPhone(raw)
  if (!local) return ''
  const groups = local.match(/.{1,3}/g) || [local]
  return `+84 ${groups.join(' ')}`
}
