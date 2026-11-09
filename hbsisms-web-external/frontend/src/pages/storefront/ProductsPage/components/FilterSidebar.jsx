import { useEffect, useMemo, useState } from 'react'
import Button from '@/components/ui/Button'
import { ChevronDownIcon, RefreshIcon, SlidersIcon } from '@/components/icons/Icons'
import { PRICE_RANGES } from '@/constants/categories'
import {
  buildChildrenMap,
  catId,
  findCategory,
  getAncestorIds,
  getRootCategories,
} from '@/utils/categoryTree'
import './FilterSidebar.css'

/**
 * Bộ lọc danh mục dạng cây:
 * Tất cả → danh mục cha (chọn được) → bung con → chọn sâu hơn.
 */
export default function FilterSidebar({
  draft,
  categories = [],
  onChange,
  onApply,
  onClear,
  isOpen,
  onClose,
}) {
  const childrenByParent = useMemo(() => buildChildrenMap(categories), [categories])
  const roots = useMemo(() => getRootCategories(categories), [categories])

  const [expandedIds, setExpandedIds] = useState(() => new Set())

  // Khi chọn category (URL / draft), mở đường dẫn tổ tiên để thấy mục đang chọn
  useEffect(() => {
    const selected = catId(draft.categoryId)
    if (!selected || selected === 'all') return
    const ancestors = getAncestorIds(categories, selected)
    const self = findCategory(categories, selected)
    const next = new Set(ancestors)
    // Mở luôn chính nó nếu có con
    if (self && (childrenByParent.get(selected) || []).length > 0) {
      next.add(selected)
    }
    setExpandedIds((prev) => {
      const merged = new Set(prev)
      next.forEach((id) => merged.add(id))
      return merged
    })
  }, [draft.categoryId, categories, childrenByParent])

  function selectCategory(id) {
    onChange({ categoryId: id })
    const kids = childrenByParent.get(catId(id)) || []
    if (id !== 'all' && kids.length > 0) {
      setExpandedIds((prev) => new Set(prev).add(catId(id)))
    }
  }

  function toggleExpand(id, e) {
    e.preventDefault()
    e.stopPropagation()
    const key = catId(id)
    setExpandedIds((prev) => {
      const next = new Set(prev)
      if (next.has(key)) next.delete(key)
      else next.add(key)
      return next
    })
  }

  function renderNode(node, depth = 0) {
    const id = catId(node.id)
    const kids = childrenByParent.get(id) || []
    const hasKids = kids.length > 0
    const expanded = expandedIds.has(id)
    const selected = String(draft.categoryId) === id

    return (
      <div key={id} className="filter-sidebar__node">
        <div
          className={`filter-sidebar__option filter-sidebar__option--tree ${selected ? 'is-selected' : ''}`}
          style={{ paddingLeft: `${depth * 16}px` }}
        >
          <label className="filter-sidebar__option-main">
            <input
              type="radio"
              name="category"
              checked={selected}
              onChange={() => selectCategory(id)}
            />
            <span>{node.name}</span>
          </label>
          {hasKids && (
            <button
              type="button"
              className={`filter-sidebar__expand ${expanded ? 'is-open' : ''}`}
              aria-expanded={expanded}
              aria-label={expanded ? `Thu gọn ${node.name}` : `Xem mục con của ${node.name}`}
              onClick={(e) => toggleExpand(id, e)}
            >
              <ChevronDownIcon size={16} />
            </button>
          )}
        </div>
        {hasKids && expanded && (
          <div className="filter-sidebar__children">{kids.map((child) => renderNode(child, depth + 1))}</div>
        )}
      </div>
    )
  }

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

        <fieldset className="filter-sidebar__group">
          <legend>Danh mục sản phẩm</legend>

          <label
            className={`filter-sidebar__option filter-sidebar__option--tree ${
              draft.categoryId === 'all' ? 'is-selected' : ''
            }`}
          >
            <span className="filter-sidebar__option-main">
              <input
                type="radio"
                name="category"
                checked={draft.categoryId === 'all'}
                onChange={() => selectCategory('all')}
              />
              <span>Tất cả</span>
            </span>
          </label>

          {roots.map((root) => renderNode(root, 0))}
        </fieldset>

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
