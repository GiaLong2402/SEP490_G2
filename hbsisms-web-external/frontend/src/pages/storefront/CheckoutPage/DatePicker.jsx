import { useEffect, useRef, useState } from 'react'
import { CalendarIcon, ChevronDownIcon, ChevronLeftIcon, ChevronRightIcon } from '@/components/icons/Icons'
import { formatFullDate, sameDay } from '@/utils/date'

const WEEK_HEAD = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN']
/**
 * Ô chọn ngày: bấm mở lịch tháng, chỉ cho chọn từ min (và tới max nếu có).
 */
export default function DatePicker({ value, onChange, min, max }) {
  const [open, setOpen] = useState(false)
  const [view, setView] = useState(() => new Date(value.getFullYear(), value.getMonth(), 1))
  const wrapRef = useRef(null)

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

  function toggle() {
    if (!open) setView(new Date(value.getFullYear(), value.getMonth(), 1))
    setOpen((v) => !v)
  }

  const year = view.getFullYear()
  const month = view.getMonth()
  const lead = (new Date(year, month, 1).getDay() + 6) % 7 // tuần bắt đầu từ Thứ Hai
  const daysInMonth = new Date(year, month + 1, 0).getDate()
  const cells = [
    ...Array.from({ length: lead }, () => null),
    ...Array.from({ length: daysInMonth }, (_, i) => new Date(year, month, i + 1)),
  ]

  const canPrev = new Date(year, month, 1) > new Date(min.getFullYear(), min.getMonth(), 1)
  const canNext = !max || new Date(year, month + 1, 1) <= max
  const isToday = sameDay(value, min)

  return (
    <div className="date-field" ref={wrapRef}>
      <button
        type="button"
        className={`date-field__trigger ${open ? 'is-open' : ''}`}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={toggle}
      >
        <CalendarIcon size={20} />
        <span className="date-field__value">
          <span className="date-field__date">{formatFullDate(value)}</span>
          {isToday && <small>Hôm nay</small>}
        </span>
        <ChevronDownIcon size={18} className="date-field__chevron" />
      </button>

      {open && (
        <div className="date-pop" role="dialog" aria-label="Chọn ngày nhận hàng">
          <div className="date-pop__head">
            <button
              type="button"
              aria-label="Tháng trước"
              disabled={!canPrev}
              onClick={() => setView(new Date(year, month - 1, 1))}
            >
              <ChevronLeftIcon size={18} />
            </button>
            <strong>
              Tháng {month + 1}, {year}
            </strong>
            <button
              type="button"
              aria-label="Tháng sau"
              disabled={!canNext}
              onClick={() => setView(new Date(year, month + 1, 1))}
            >
              <ChevronRightIcon size={18} />
            </button>
          </div>

          <div className="date-pop__grid">
            {WEEK_HEAD.map((w) => (
              <span key={w} className="date-pop__week">
                {w}
              </span>
            ))}
            {cells.map((d, i) =>
              d ? (
                <button
                  key={i}
                  type="button"
                  disabled={d < min || (max && d > max)}
                  aria-pressed={sameDay(d, value)}
                  className={[
                    'date-pop__day',
                    sameDay(d, value) ? 'is-selected' : '',
                    sameDay(d, min) ? 'is-today' : '',
                  ].join(' ')}
                  onClick={() => {
                    onChange(d)
                    setOpen(false)
                  }}
                >
                  {d.getDate()}
                </button>
              ) : (
                <span key={i} />
              ),
            )}
          </div>

        </div>
      )}
    </div>
  )
}
