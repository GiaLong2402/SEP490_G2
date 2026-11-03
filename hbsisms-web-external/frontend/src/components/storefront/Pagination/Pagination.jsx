import { ChevronLeftIcon, ChevronRightIcon } from '@/components/icons/Icons'
import './Pagination.css'

export default function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) {
    return (
      <nav className="pagination" aria-label="Phân trang">
        <button type="button" className="pagination__arrow" disabled aria-label="Trang trước">
          <ChevronLeftIcon />
        </button>
        <button type="button" className="pagination__page is-active" aria-current="page">
          1
        </button>
        <button type="button" className="pagination__arrow" disabled aria-label="Trang sau">
          <ChevronRightIcon />
        </button>
      </nav>
    )
  }

  const pages = Array.from({ length: totalPages }, (_, i) => i + 1)

  return (
    <nav className="pagination" aria-label="Phân trang">
      <button
        type="button"
        className="pagination__arrow"
        disabled={page <= 1}
        aria-label="Trang trước"
        onClick={() => onChange(page - 1)}
      >
        <ChevronLeftIcon />
      </button>

      {pages.map((p) => (
        <button
          key={p}
          type="button"
          className={`pagination__page ${p === page ? 'is-active' : ''}`}
          aria-current={p === page ? 'page' : undefined}
          onClick={() => onChange(p)}
        >
          {p}
        </button>
      ))}

      <button
        type="button"
        className="pagination__arrow"
        disabled={page >= totalPages}
        aria-label="Trang sau"
        onClick={() => onChange(page + 1)}
      >
        <ChevronRightIcon />
      </button>
    </nav>
  )
}
