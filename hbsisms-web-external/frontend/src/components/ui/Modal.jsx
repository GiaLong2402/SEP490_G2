import { useEffect, useRef } from 'react'
import { createPortal } from 'react-dom'
import { CloseIcon } from '@/components/icons/Icons'
import './Modal.css'

/**
 * Hộp thoại dùng chung.
 *
 * props:
 * - open, onClose
 * - title, description (dòng phụ dưới tiêu đề)
 * - icon: node icon trong ô tròn đầu hộp thoại; tone: accent | danger | warning
 * - footer: các nút ở chân hộp thoại
 * - size: sm | md (mặc định md)
 * - busy: true thì không đóng bằng Esc / nền mờ (đang gửi)
 */
export default function Modal({ open, onClose, title, description, icon, tone = 'accent', footer, size = 'md', busy = false, children }) {
  const boxRef = useRef(null)
  // Giữ bản mới nhất để effect chỉ chạy khi mở/đóng (tránh nhảy focus khi đang gõ)
  const latest = useRef({ onClose, busy })
  useEffect(() => {
    latest.current = { onClose, busy }
  })

  useEffect(() => {
    if (!open) return undefined
    const prevOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    // Focus phần tử nhập liệu / nút đầu tiên
    const first = boxRef.current?.querySelector('input, select, textarea, button:not(.modal__close)')
    first?.focus()

    function onKey(e) {
      if (e.key === 'Escape' && !latest.current.busy) latest.current.onClose?.()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.body.style.overflow = prevOverflow
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  if (!open) return null

  return createPortal(
    <div className="modal" onMouseDown={(e) => e.target === e.currentTarget && !busy && onClose?.()}>
      <div
        ref={boxRef}
        className={`modal__box modal__box--${size}`}
        role="dialog"
        aria-modal="true"
        aria-labelledby="modal-title"
      >
        <button type="button" className="modal__close" aria-label="Đóng" onClick={onClose} disabled={busy}>
          <CloseIcon size={20} />
        </button>

        <div className="modal__head">
          {icon && <span className={`modal__icon modal__icon--${tone}`}>{icon}</span>}
          <div>
            <h2 id="modal-title">{title}</h2>
            {description && <p>{description}</p>}
          </div>
        </div>

        {children && <div className="modal__body">{children}</div>}
        {footer && <div className="modal__foot">{footer}</div>}
      </div>
    </div>,
    document.body,
  )
}
