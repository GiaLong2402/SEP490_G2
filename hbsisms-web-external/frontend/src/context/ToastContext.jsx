import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { CheckIcon } from '@/components/icons/Icons'
import '@/components/ui/Toast.css'

const ToastContext = createContext(null)

/** Thời gian thông báo tự ẩn (ms) */
const DURATION = 5000

/**
 * Thông báo nhỏ góc màn hình, tự ẩn sau 5 giây.
 *
 *   const { showToast } = useToast()
 *   showToast('Sản phẩm đã được thêm vào giỏ hàng')
 */
export function ToastProvider({ children }) {
  const [toast, setToast] = useState(null) // { id, message }
  const timer = useRef(null)

  const showToast = useCallback((message) => {
    window.clearTimeout(timer.current)
    setToast({ id: Date.now(), message })
    timer.current = window.setTimeout(() => setToast(null), DURATION)
  }, [])

  useEffect(() => () => window.clearTimeout(timer.current), [])

  const value = useMemo(() => ({ showToast }), [showToast])

  return (
    <ToastContext.Provider value={value}>
      {children}
      <div className="toast-region" role="status" aria-live="polite">
        {toast && (
          <div key={toast.id} className="toast">
            <span className="toast__icon" aria-hidden="true">
              <CheckIcon size={18} />
            </span>
            <span>{toast.message}</span>
          </div>
        )}
      </div>
    </ToastContext.Provider>
  )
}

export function useToast() {
  const ctx = useContext(ToastContext)
  if (!ctx) throw new Error('useToast phải dùng bên trong ToastProvider')
  return ctx
}
