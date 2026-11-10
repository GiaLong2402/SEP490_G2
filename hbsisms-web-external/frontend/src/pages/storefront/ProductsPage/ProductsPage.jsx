import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import PageBanner from '@/components/storefront/PageBanner/PageBanner'
import Pagination from '@/components/storefront/Pagination/Pagination'
import ProductCard from '@/components/storefront/ProductCard/ProductCard'
import Button from '@/components/ui/Button'
import { SlidersIcon } from '@/components/icons/Icons'
import { getListingItemsFromProducts } from '@/api/mappers/product'
import { PRICE_RANGES, SORT_OPTIONS } from '@/constants/categories'
import { ROUTES } from '@/constants/routes'
import { useStoreCatalog } from '@/hooks/useStoreCatalog'
import {
  catId,
  findCategory,
  getCategoryAndDescendantIds,
} from '@/utils/categoryTree'
import FilterSidebar from './components/FilterSidebar'
import './ProductsPage.css'

const PAGE_SIZE = 6

const DEFAULT_FILTERS = {
  categoryId: 'all',
  priceRangeId: 'all',
  inStockOnly: false,
  sortId: 'default',
}

function filterAndSortItems(items, filters, categories) {
  let result = [...items]

  if (filters.categoryId !== 'all') {
    const allowed = getCategoryAndDescendantIds(categories, filters.categoryId)
    if (allowed) {
      result = result.filter((item) => allowed.has(catId(item.categoryId)))
    }
  }

  if (filters.priceRangeId !== 'all') {
    const range = PRICE_RANGES.find((r) => r.id === filters.priceRangeId)
    result = result.filter(
      (item) => item.variant.price >= range.min && item.variant.price <= range.max,
    )
  }

  if (filters.inStockOnly) {
    result = result.filter((item) => item.inStock)
  }

  if (filters.sortId === 'price-asc') {
    result.sort((a, b) => a.variant.price - b.variant.price)
  } else if (filters.sortId === 'price-desc') {
    result.sort((a, b) => b.variant.price - a.variant.price)
  } else if (filters.sortId === 'newest') {
    result.sort((a, b) => String(b.productId).localeCompare(String(a.productId)))
  }

  return result
}

export default function ProductsPage() {
  const [searchParams] = useSearchParams()
  const categoryFromUrl = searchParams.get('category')

  const { products, categories, loading, error } = useStoreCatalog({ pageSize: 100 })

  const startCategory =
    categoryFromUrl && findCategory(categories, categoryFromUrl)
      ? catId(categoryFromUrl)
      : 'all'

  const [draft, setDraft] = useState({ ...DEFAULT_FILTERS, categoryId: startCategory })
  const [applied, setApplied] = useState({ ...DEFAULT_FILTERS, categoryId: startCategory })
  const [page, setPage] = useState(1)
  const [filterOpen, setFilterOpen] = useState(false)

  useEffect(() => {
    if (!categoryFromUrl) return
    if (!findCategory(categories, categoryFromUrl)) return
    const id = catId(categoryFromUrl)
    setDraft((prev) => ({ ...prev, categoryId: id }))
    setApplied((prev) => ({ ...prev, categoryId: id }))
    setPage(1)
  }, [categoryFromUrl, categories])

  const allItems = useMemo(() => getListingItemsFromProducts(products), [products])
  const filtered = useMemo(
    () => filterAndSortItems(allItems, applied, categories),
    [allItems, applied, categories],
  )

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const currentPage = Math.min(page, totalPages)
  const start = (currentPage - 1) * PAGE_SIZE
  const pageItems = filtered.slice(start, start + PAGE_SIZE)
  const showingFrom = filtered.length === 0 ? 0 : start + 1
  const showingTo = Math.min(start + PAGE_SIZE, filtered.length)

  const title =
    applied.categoryId === 'all'
      ? 'Tất cả sản phẩm'
      : findCategory(categories, applied.categoryId)?.name || 'Sản phẩm'

  function updateDraft(change) {
    setDraft((prev) => ({ ...prev, ...change }))
  }

  function applyFilters() {
    setApplied(draft)
    setPage(1)
    setFilterOpen(false)
  }

  function clearFilters() {
    setDraft(DEFAULT_FILTERS)
    setApplied(DEFAULT_FILTERS)
    setPage(1)
  }

  function changeSort(sortId) {
    setDraft((prev) => ({ ...prev, sortId }))
    setApplied((prev) => ({ ...prev, sortId }))
    setPage(1)
  }

  return (
    <div className="products-page">
      <PageBanner
        title="Sản phẩm"
        breadcrumbs={[{ label: 'Trang chủ', to: ROUTES.HOME }, { label: 'Sản phẩm' }]}
      />

      <div className="container products-layout">
        <button
          type="button"
          className="products-filter-toggle"
          onClick={() => setFilterOpen(true)}
        >
          <SlidersIcon size={20} /> Bộ lọc
        </button>

        <FilterSidebar
          draft={draft}
          categories={categories}
          onChange={updateDraft}
          onApply={applyFilters}
          onClear={clearFilters}
          isOpen={filterOpen}
          onClose={() => setFilterOpen(false)}
        />

        <section className="products-main">
          <div className="products-main__head">
            <div>
              <h2>{title}</h2>
              <p>
                {loading
                  ? 'Đang tải sản phẩm từ hệ thống…'
                  : error
                    ? `Không tải được dữ liệu: ${error.message}`
                    : `Hiển thị ${showingFrom}–${showingTo} trong ${filtered.length} sản phẩm`}
              </p>
            </div>

            <label className="products-sort">
              <span className="visually-hidden">Sắp xếp</span>
              <select value={applied.sortId} onChange={(e) => changeSort(e.target.value)}>
                {SORT_OPTIONS.map((opt) => (
                  <option key={opt.id} value={opt.id}>
                    {opt.label}
                  </option>
                ))}
              </select>
            </label>
          </div>

          {loading ? (
            <div className="products-empty">
              <p>Đang tải sản phẩm…</p>
            </div>
          ) : !error && pageItems.length === 0 ? (
            <div className="products-empty">
              <p>Không có sản phẩm phù hợp.</p>
              <Button variant="link" onClick={clearFilters}>
                Xóa bộ lọc
              </Button>
            </div>
          ) : (
            <div className="products-grid">
              {pageItems.map((item) => (
                <ProductCard
                  key={item.listingId}
                  product={item}
                  variant={item.variant}
                  cartVariant="icon"
                />
              ))}
            </div>
          )}

          <Pagination page={currentPage} totalPages={totalPages} onChange={setPage} />
        </section>
      </div>
    </div>
  )
}
