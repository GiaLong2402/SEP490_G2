import { RecaptchaVerifier, signInWithPhoneNumber } from 'firebase/auth'
import { getFirebaseAuth, isFirebaseConfigured } from '@/lib/firebase'
import { buildStubIdToken } from '@/auth/stubIdToken'
import { normalizeVietnamPhone } from '@/utils/phone'

const RECAPTCHA_HOST_ID = 'recaptcha-container'

/** E.164 cho Firebase Phone Auth: +84xxxxxxxxx */
export function toE164Vn(raw) {
  const local = normalizeVietnamPhone(raw)
  return local ? `+84${local}` : ''
}

let confirmationResult = null
let recaptchaVerifier = null

function resetRecaptchaDom() {
  const host = document.getElementById(RECAPTCHA_HOST_ID)
  if (host) host.innerHTML = ''
}

/** Mỗi lần gửi OTP: widget DOM mới — tránh "already been rendered". */
function createFreshRecaptchaWidget() {
  const host = document.getElementById(RECAPTCHA_HOST_ID)
  if (!host) {
    throw new Error('Thiếu #recaptcha-container trên trang đăng nhập')
  }
  host.innerHTML = ''
  const widget = document.createElement('div')
  host.appendChild(widget)
  return widget
}

export function clearPhoneAuthSession() {
  confirmationResult = null
  if (recaptchaVerifier) {
    try {
      recaptchaVerifier.clear()
    } catch {
      // ignore
    }
    recaptchaVerifier = null
  }
  resetRecaptchaDom()
}

/**
 * Gửi OTP.
 * @returns {{ mode: 'stub' | 'firebase' }}
 */
export async function sendPhoneOtp(phone) {
  if (!isFirebaseConfigured()) {
    clearPhoneAuthSession()
    return { mode: 'stub' }
  }

  const auth = getFirebaseAuth()
  clearPhoneAuthSession()

  try {
    const widget = createFreshRecaptchaWidget()
    recaptchaVerifier = new RecaptchaVerifier(auth, widget, { size: 'invisible' })
    confirmationResult = await signInWithPhoneNumber(auth, toE164Vn(phone), recaptchaVerifier)
    return { mode: 'firebase' }
  } catch (err) {
    clearPhoneAuthSession()
    throw err
  }
}

/**
 * Xác nhận mã → idToken (Firebase JWT hoặc stub Phase 1).
 */
export async function confirmPhoneOtp(code, phone) {
  if (!isFirebaseConfigured() || !confirmationResult) {
    return buildStubIdToken(phone)
  }
  const credential = await confirmationResult.confirm(code)
  const idToken = await credential.user.getIdToken()
  // OTP xong — free reCAPTCHA để lần gửi sau không kẹt
  confirmationResult = null
  if (recaptchaVerifier) {
    try {
      recaptchaVerifier.clear()
    } catch {
      // ignore
    }
    recaptchaVerifier = null
  }
  resetRecaptchaDom()
  return idToken
}

export function mapFirebaseAuthError(err) {
  const code = err?.code || ''
  const message = err?.message || ''
  if (code === 'auth/invalid-phone-number') return 'Số điện thoại không hợp lệ.'
  if (code === 'auth/too-many-requests') return 'Gửi OTP quá nhiều lần. Thử lại sau.'
  if (code === 'auth/code-expired') return 'Mã OTP đã hết hạn. Nhận lại mã mới.'
  if (code === 'auth/invalid-verification-code') return 'Mã OTP không đúng.'
  if (code === 'auth/captcha-check-failed') return 'Xác minh reCAPTCHA thất bại. Tải lại trang.'
  if (message.includes('already been rendered')) {
    return 'reCAPTCHA bị kẹt. Tải lại trang rồi thử lại.'
  }
  return message || 'Không gửi/xác thực được OTP.'
}
