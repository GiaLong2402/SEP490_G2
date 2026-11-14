import { useEffect, useRef, useState } from 'react'
import { CalendarIcon, CheckIcon, ChevronDownIcon } from '@/components/icons/Icons'
import { TIME_OPTIONS } from './timeRange'

/** Nút chọn khoảng thời gian (thay select mặc định của trình duyệt) */
export default function TimeFilter({ value, onChange }) {
  const [open, setOpen] = useState(false)
  const wrapRef = useRef(null)
  const current = TIME_OPTIONS.find((o) => o.value === value) || TIME_OPTIONS[0]

  useEffect(() => {
    if (!open) return undefined
    function onDown(e) {
      if (wrapRef.current && !wrapRef.current.contains(e.target)) setOpen(false)
    }
    function onKey(e) {
      if (e.key === 'Escape') setOpen(false)
    }
    document.addEventListener('mousedown', onDown)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('mousedown', onDown)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div className="orders-time" ref={wrapRef}>
      <button
        type="button"
        className={`orders-time__trigger ${open ? 'is-open' : ''} ${value !== 'all' ? 'is-set' : ''}`}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-label={`Thời gian: ${current.label}`}
        onClick={() => setOpen((v) => !v)}
      >
        <CalendarIcon size={18} />
        <span>{current.label}</span>
        <ChevronDownIcon size={18} className="orders-time__chevron" />
      </button>

      {open && (
        <ul className="orders-time__menu" role="listbox" aria-label="Khoảng thời gian">
          {TIME_OPTIONS.map((o) => {
            const active = o.value === current.value
            return (
              <li key={o.value} role="option" aria-selected={active}>
                <button
                  type="button"
                  className={`orders-time__option ${active ? 'is-active' : ''}`}
                  onClick={() => {
                    onChange(o.value)
                    setOpen(false)
                  }}
                >
                  {o.label}
                  {active && <CheckIcon size={16} />}
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}
