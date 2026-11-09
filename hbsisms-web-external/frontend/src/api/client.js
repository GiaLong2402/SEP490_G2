import { API_BASE_URL } from './config'

const TOKEN_KEY = 'hbsisms.external.token'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

export const tokenStore = {
  get() {
    try {
      return localStorage.getItem(TOKEN_KEY)
    } catch {
      return null
    }
  },
  set(token) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token)
      else localStorage.removeItem(TOKEN_KEY)
    } catch {
      // ignore storage errors
    }
  },
}

let onUnauthorized = () => {}

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

export async function apiRequest(method, path, { body, query, signal } = {}) {
  const url = new URL(
    `${API_BASE_URL.replace(/\/$/, '')}${path.startsWith('/') ? path : `/${path}`}`,
    window.location.origin,
  )
  Object.entries(query || {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      url.searchParams.set(key, String(value))
    }
  })

  const token = tokenStore.get()
  const res = await fetch(url, {
    method,
    signal,
    headers: {
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  if (!res.ok) {
    const payload = await res.json().catch(() => null)
    const error = new ApiError(res.status, payload?.message || `Lỗi máy chủ (${res.status})`)
    if (res.status === 401) onUnauthorized()
    throw error
  }

  if (res.status === 204) return undefined
  return res.json()
}

export const api = {
  get: (path, query, signal) => apiRequest('GET', path, { query, signal }),
  post: (path, body) => apiRequest('POST', path, { body }),
  put: (path, body) => apiRequest('PUT', path, { body }),
  patch: (path, body) => apiRequest('PATCH', path, { body }),
  delete: (path) => apiRequest('DELETE', path),
}
