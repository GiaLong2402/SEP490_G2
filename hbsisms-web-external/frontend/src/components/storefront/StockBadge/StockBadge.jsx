import Badge from '@/components/ui/Badge'
import './StockBadge.css'

/**
 * Nhãn trạng thái kho: "Còn hàng" / "Hết hàng".
 *
 * props:
 * - inStock:   true = còn hàng, false = hết hàng
 * - solid:     true = nền đặc, chữ trắng (kiểu trên card sản phẩm & trang chi tiết)
 *              false = nền nhạt có chấm tròn
 * - className: thêm class nếu cần chỉnh vị trí
 */
export default function StockBadge({ inStock, solid = false, className = '' }) {
  const classes = `stock-badge ${solid ? 'stock-badge--solid' : ''} ${className}`.trim()

  if (inStock) {
    return (
      <Badge tone="success" className={classes}>
        {!solid && <span className="stock-badge__dot" aria-hidden="true" />}
        Còn hàng
      </Badge>
    )
  }

  return (
    <Badge tone="danger" className={classes}>
      Hết hàng
    </Badge>
  )
}
