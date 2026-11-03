import { Link } from 'react-router-dom'
import './Breadcrumbs.css'

/**
 * items: [{ label, to? }] — phần tử cuối không có to (trang hiện tại)
 */
export default function Breadcrumbs({ items = [] }) {
  return (
    <nav className="breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {items.map((item, index) => {
          const isLast = index === items.length - 1
          return (
            <li key={`${item.label}-${index}`}>
              {!isLast && item.to ? (
                <Link to={item.to}>{item.label}</Link>
              ) : (
                <span aria-current={isLast ? 'page' : undefined}>{item.label}</span>
              )}
              {!isLast && <span className="breadcrumbs__sep" aria-hidden="true"> / </span>}
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
