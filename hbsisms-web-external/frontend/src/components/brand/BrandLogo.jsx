import './BrandLogo.css'

/**
 * Logo Hòa Bình — cá + sóng (SVG), không dùng icon tròn.
 * tone: 'dark' (header) | 'light' (footer)
 */
export default function BrandLogo({ tone = 'dark', className = '' }) {
  const isLight = tone === 'light'
  const navy = isLight ? '#FFFFFF' : '#073653'
  const cyan = '#12B8D0'
  const accent = isLight ? '#7DD3E8' : '#0A4A6B'

  return (
    <span className={`brand-logo ${isLight ? 'brand-logo--light' : ''} ${className}`.trim()}>
      <svg
        className="brand-logo__mark"
        viewBox="0 0 48 48"
        width="44"
        height="44"
        aria-hidden="true"
        focusable="false"
      >
        {/* Sóng dưới */}
        <path
          d="M4 34c3.5-3 7-3 10.5 0s7 3 10.5 0 7-3 10.5 0 7 3 10.5 0"
          fill="none"
          stroke={cyan}
          strokeWidth="2.2"
          strokeLinecap="round"
        />
        <path
          d="M6 40c3-2.4 6-2.4 9 0s6 2.4 9 0 6-2.4 9 0 6 2.4 9 0"
          fill="none"
          stroke={accent}
          strokeWidth="1.6"
          strokeLinecap="round"
          opacity="0.7"
        />
        {/* Cá */}
        <path
          d="M8 22c7.5-11 22-12.5 30-3.5-8.5 1.5-15 7-18.5 14.5C15 29 11.5 26.5 8 22z"
          fill={navy}
        />
        <path
          d="M34 20.5c2.2-.2 4.2.2 5.8 1.2-1.8 1.8-4.2 2.6-6.6 2.4.2-1.2.4-2.4.8-3.6z"
          fill={cyan}
        />
        <circle cx="30.5" cy="18.5" r="1.7" fill={isLight ? '#073653' : '#FFFFFF'} />
      </svg>
      <span className="brand-logo__text">
        <strong>HÒA BÌNH</strong>
        <small>HẢI SẢN HẢI PHÒNG</small>
      </span>
    </span>
  )
}
