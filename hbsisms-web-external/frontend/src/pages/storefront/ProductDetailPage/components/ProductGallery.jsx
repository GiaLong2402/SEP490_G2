import { useState } from 'react'
import ImageLightbox from '@/components/ui/ImageLightbox'
import { ZoomInIcon } from '@/components/icons/Icons'
import './ProductGallery.css'

/**
 * Khối ảnh sản phẩm: ảnh lớn + nút phóng to + dãy ảnh nhỏ (thumbnail).
 *
 * props:
 * - images: mảng link ảnh, ví dụ ['/images/a.png', '/images/b.png']
 * - name:   tên sản phẩm (dùng làm alt cho ảnh)
 */
export default function ProductGallery({ images, name }) {
  const [activeIndex, setActiveIndex] = useState(0) // ảnh đang hiện ở khung lớn
  const [isZoomOpen, setIsZoomOpen] = useState(false)

  return (
    <div className="product-gallery">
      {/* Ảnh lớn — bấm vào ảnh hoặc nút kính lúp để phóng to */}
      <div className="product-gallery__main">
        <img src={images[activeIndex]} alt={name} onClick={() => setIsZoomOpen(true)} />
        <button
          type="button"
          className="product-gallery__zoom"
          aria-label="Phóng to ảnh"
          onClick={() => setIsZoomOpen(true)}
        >
          <ZoomInIcon size={22} />
        </button>
      </div>

      {/* Ảnh nhỏ — bấm vào để đổi ảnh lớn */}
      <div className="product-gallery__thumbs">
        {images.map((src, index) => (
          <button
            key={`${src}-${index}`}
            type="button"
            className={index === activeIndex ? 'is-active' : ''}
            aria-label={`Xem ảnh ${index + 1}`}
            onClick={() => setActiveIndex(index)}
          >
            <img src={src} alt="" />
          </button>
        ))}
      </div>

      {/* Cửa sổ phóng to (component dùng chung trong components/ui) */}
      {isZoomOpen && (
        <ImageLightbox
          images={images}
          startIndex={activeIndex}
          alt={name}
          onClose={() => setIsZoomOpen(false)}
        />
      )}
    </div>
  )
}
