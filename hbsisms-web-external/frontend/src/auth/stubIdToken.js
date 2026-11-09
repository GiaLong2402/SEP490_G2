import { normalizeVietnamPhone } from '@/utils/phone'

const STUB_UID_PREFIX = 'hbsisms.external.stubUid.'

/** Chuẩn hóa về dạng lưu DB / stub token: 0xxxxxxxxx */
export function toVnMobile0(raw) {
  const local = normalizeVietnamPhone(raw)
  return local ? `0${local}` : ''
}

/**
 * Phase 1 — Firebase stub token: `stub.<uid>.<phone>`.
 * Giữ uid ổn định theo SĐT (localStorage) để tránh 409 khi login lại.
 */
export function buildStubIdToken(phoneRaw) {
  const phone = toVnMobile0(phoneRaw)
  if (!phone) {
    throw new Error('Số điện thoại không hợp lệ')
  }
  const key = `${STUB_UID_PREFIX}${phone}`
  let uid
  try {
    uid = localStorage.getItem(key)
  } catch {
    uid = null
  }
  if (!uid) {
    uid = `uid-ext-${phone}`
    try {
      localStorage.setItem(key, uid)
    } catch {
      // ignore
    }
  }
  return `stub.${uid}.${phone}`
}
