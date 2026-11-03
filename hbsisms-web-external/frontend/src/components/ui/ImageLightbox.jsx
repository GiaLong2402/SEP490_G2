import { useEffect, useState } from 'react'
import { createPortal } from 'react-dom'
import { ChevronLeftIcon, ChevronRightIcon, CloseIcon } from '@/components/icons/Icons'
import './ImageLightbox.css'

/**
 * Cửa sổ xem ảnh phóng to (lightbox) — dùng chung cho mọi trang.
 *
 * props:
 * - images:     mảng link ảnh
 * - startIndex: mở ở ảnh thứ mấy (bắt đầu từ 0)
 * - alt:        mô tả ảnh
 * - onClose:    hàm đóng cửa sổ
 *
 * Phím tắt: Esc = đóng, ← → = ảnh trước / sau
 *
 * Vì sao dùng createPortal?
 *   Nếu đặt lightbox bên trong trang, header (position: sticky) có thể đè lên nó.
 *   createPortal "đưa" lightbox ra thẳng thẻ <body>, nên nó luôn nằm trên cùng.
 */
export default function ImageLightbox({ images, startIndex = 0, alt = '', onClose }) {
  const [index, setIndex] = useState(startIndex)
  const total = images.length
  const hasMany = total > 1

  function showPrev() {
    setIndex((i) => (i - 1 + total) % total) // ảnh đầu bấm lùi => về ảnh cuối
  }

  function showNext() {
    setIndex((i) => (i + 1) % total) // ảnh cuối bấm tiếp => về ảnh đầu
  }

  useEffect(() => {
    // 1. Khóa cuộn trang phía sau khi đang xem ảnh
    const oldOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'

    // 2. Phím tắt
    function handleKeyDown(e) {
      if (e.key === 'Escape') onClose()
      if (e.key === 'ArrowLeft') setIndex((i) => (i - 1 + total) % total)
      if (e.key === 'ArrowRight') setIndex((i) => (i + 1) % total)
    }
    window.addEventListener('keydown', handleKeyDown)

    // 3. Khi đóng: trả lại như cũ
    return () => {
      document.body.style.overflow = oldOverflow
      window.removeEventListener('keydown', handleKeyDown)
    }
  }, [onClose, total])

  return createPortal(
    // Bấm vào nền tối => đóng
    <div className="lightbox" role="dialog" aria-modal="true" aria-label="Xem ảnh" onClick={onClose}>
      {/* stopPropagation: bấm vào bên trong khung ảnh thì KHÔNG đóng */}
      <div className="lightbox__frame" onClick={(e) => e.stopPropagation()}>
        <img className="lightbox__image" src={images[index]} alt={alt} />

        {/* Bộ đếm "1 / 3" */}
        <span className="lightbox__counter">
          {index + 1} / {total}
        </span>

        <button type="button" className="lightbox__close" aria-label="Đóng" onClick={onClose}>
          <CloseIcon size={22} />
        </button>

        {/* Mũi tên và dải ảnh nhỏ chỉ hiện khi có nhiều hơn 1 ảnh */}
        {hasMany && (
          <>
            <button type="button" className="lightbox__arrow is-prev" aria-label="Ảnh trước" onClick={showPrev}>
              <ChevronLeftIcon size={26} />
            </button>
            <button type="button" className="lightbox__arrow is-next" aria-label="Ảnh sau" onClick={showNext}>
              <ChevronRightIcon size={26} />
            </button>
          </>
        )}

        <div className="lightbox__thumbs">
          {images.map((src, i) => (
            <button
              key={`${src}-${i}`}
              type="button"
              className={i === index ? 'is-active' : ''}
              aria-label={`Xem ảnh ${i + 1}`}
              onClick={() => setIndex(i)}
            >
              <img src={src} alt="" />
            </button>
          ))}
        </div>
      </div>
    </div>,
    document.body,
  )
}
