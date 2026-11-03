import Button from '@/components/ui/Button'
import { RefreshIcon, SlidersIcon } from '@/components/icons/Icons'
import { CATEGORIES, PRICE_RANGES } from '@/constants/categories'
import './FilterSidebar.css'

/**
 * Thanh bộ lọc bên trái trang Sản phẩm.
 *
 * props:
 * - draft:     bộ lọc người dùng ĐANG chọn (chưa áp dụng)
 * - onChange:  gọi khi đổi 1 lựa chọn, ví dụ onChange({ categoryId: 'tom' })
 * - onApply:   bấm "Áp dụng bộ lọc"
 * - onClear:   bấm "Xóa bộ lọc"
 * - isOpen:    (mobile) bộ lọc đang mở hay không
 * - onClose:   (mobile) đóng bộ lọc
 */
export default function FilterSidebar({ draft, onChange, onApply, onClear, isOpen, onClose }) {
  return (
    <>
      <aside className={`filter-sidebar ${isOpen ? 'is-open' : ''}`}>
        <div className="filter-sidebar__head">
          <h2>
            <SlidersIcon size={26} /> Bộ lọc
          </h2>
          <button type="button" className="filter-sidebar__close" onClick={onClose}>
            Đóng
          </button>
        </div>

        {/* Nhóm 1: danh mục */}
        <fieldset className="filter-sidebar__group">
          <legend>Danh mục sản phẩm</legend>
          {CATEGORIES.map((cat) => (
            <label key={cat.id} className="filter-sidebar__option">
              <input
                type="radio"
                name="category"
                checked={draft.categoryId === cat.id}
                onChange={() => onChange({ categoryId: cat.id })}
              />
              <span>{cat.name}</span>
            </label>
          ))}
        </fieldset>

        {/* Nhóm 2: khoảng giá */}
        <fieldset className="filter-sidebar__group">
          <legend>Khoảng giá / khay</legend>
          {PRICE_RANGES.map((range) => (
            <label key={range.id} className="filter-sidebar__option">
              <input
                type="radio"
                name="price"
                checked={draft.priceRangeId === range.id}
                onChange={() => onChange({ priceRangeId: range.id })}
              />
              <span>{range.label}</span>
            </label>
          ))}
        </fieldset>

        {/* Nhóm 3: còn hàng */}
        <div className="filter-sidebar__group">
          <label className="filter-sidebar__option">
            <input
              type="checkbox"
              checked={draft.inStockOnly}
              onChange={(e) => onChange({ inStockOnly: e.target.checked })}
            />
            <span>Chỉ hiện sản phẩm còn hàng</span>
          </label>
        </div>

        <Button variant="navy" fullWidth onClick={onApply}>
          Áp dụng bộ lọc
        </Button>
        <button type="button" className="filter-sidebar__clear" onClick={onClear}>
          <RefreshIcon /> Xóa bộ lọc
        </button>
      </aside>

      {/* Lớp nền tối phía sau bộ lọc (chỉ hiện trên mobile khi mở) */}
      {isOpen && (
        <button
          type="button"
          className="filter-sidebar__backdrop"
          aria-label="Đóng bộ lọc"
          onClick={onClose}
        />
      )}
    </>
  )
}
