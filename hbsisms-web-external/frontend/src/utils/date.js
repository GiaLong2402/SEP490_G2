const WEEKDAYS_FULL = ['Chủ nhật', 'Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy']

/** Cùng ngày (bỏ qua giờ) */
export const sameDay = (a, b) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate()

/** "Thứ Tư, 11/11/2026" */
export function formatFullDate(d) {
  const dd = String(d.getDate()).padStart(2, '0')
  const mm = String(d.getMonth() + 1).padStart(2, '0')
  return `${WEEKDAYS_FULL[d.getDay()]}, ${dd}/${mm}/${d.getFullYear()}`
}

const pad = (n) => String(n).padStart(2, '0')

/** Date → "YYYY-MM-DD" theo giờ máy (gửi lên API, tránh lệch múi giờ của toISOString) */
export function toIsoDate(d) {
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`
}

/** "2026-11-13" hoặc ISO datetime → "13/11/2026" */
export function formatDate(value) {
  if (!value) return ''
  if (/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    const [y, m, d] = value.split('-')
    return `${d}/${m}/${y}`
  }
  const d = new Date(value)
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)}/${d.getFullYear()}`
}

/** ISO datetime → "09:20 · 22/10/2026" */
export function formatDateTime(value) {
  if (!value) return ''
  const d = new Date(value)
  return `${pad(d.getHours())}:${pad(d.getMinutes())} · ${formatDate(value)}`
}

/** "10:00-12:00" → "10:00 – 12:00" */
export function formatTimeWindow(win) {
  return win ? win.replace(/\s*-\s*/, ' – ') : ''
}

/** { date: "2026-11-13", timeWindow: "10:00-12:00" } → "13/11/2026 · 10:00 – 12:00" */
export function formatSchedule(schedule) {
  if (!schedule?.date) return ''
  return [formatDate(schedule.date), formatTimeWindow(schedule.timeWindow)].filter(Boolean).join(' · ')
}
