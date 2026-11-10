import { RecaptchaVerifier, signInWithPhoneNumber, type ConfirmationResult } from 'firebase/auth'
import { getFirebaseAuth, isFirebaseConfigured } from '@/lib/firebase'
import { toE164Vn } from '@/shared/utils/phone'
import { buildStubIdToken } from './stubIdToken'

const RECAPTCHA_HOST_ID = 'recaptcha-container'

let confirmationResult: ConfirmationResult | null = null
let recaptchaVerifier: RecaptchaVerifier | null = null

function resetRecaptchaDom() {
  const host = document.getElementById(RECAPTCHA_HOST_ID)
  if (host) host.innerHTML = ''
}

function createFreshRecaptchaWidget(): HTMLDivElement {
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

export async function sendPhoneOtp(phone: string): Promise<{ mode: 'stub' | 'firebase' }> {
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

export async function confirmPhoneOtp(code: string, phone: string): Promise<string> {
  if (!isFirebaseConfigured() || !confirmationResult) {
    return buildStubIdToken(phone)
  }
  const credential = await confirmationResult.confirm(code)
  const idToken = await credential.user.getIdToken()
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

export function mapFirebaseAuthError(err: unknown): string {
  const e = err as { code?: string; message?: string }
  const code = e?.code || ''
  const message = e?.message || ''
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
