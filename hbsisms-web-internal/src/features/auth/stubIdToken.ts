import { toVnMobile0 } from '@/shared/utils/phone'

const STUB_UID_PREFIX = 'hbsisms.internal.stubUid.'

/** Stub token khi chưa cấu hình Firebase: `stub.<uid>.0xxxxxxxxx` */
export function buildStubIdToken(phoneRaw: string): string {
  const phone = toVnMobile0(phoneRaw)
  if (!phone) throw new Error('Số điện thoại không hợp lệ')
  const key = `${STUB_UID_PREFIX}${phone}`
  let uid: string | null
  try {
    uid = localStorage.getItem(key)
  } catch {
    uid = null
  }
  if (!uid) {
    uid = `uid-int-${phone}`
    try {
      localStorage.setItem(key, uid)
    } catch {
      // ignore
    }
  }
  return `stub.${uid}.${phone}`
}
