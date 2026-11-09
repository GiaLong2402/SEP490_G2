import { initializeApp, getApps } from 'firebase/app'
import { getAuth } from 'firebase/auth'

/** true khi đã cấu hình đủ VITE_FIREBASE_* — bật Phone Auth thật. */
export function isFirebaseConfigured() {
  return Boolean(import.meta.env.VITE_FIREBASE_API_KEY && import.meta.env.VITE_FIREBASE_PROJECT_ID)
}

function buildConfig() {
  return {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
  }
}

let authInstance = null

export function getFirebaseAuth() {
  if (!isFirebaseConfigured()) {
    throw new Error('Chưa cấu hình Firebase (VITE_FIREBASE_*)')
  }
  if (!authInstance) {
    const app = getApps().length ? getApps()[0] : initializeApp(buildConfig())
    authInstance = getAuth(app)
  }
  return authInstance
}
