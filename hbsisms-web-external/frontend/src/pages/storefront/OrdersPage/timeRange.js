/** Khoảng thời gian lọc đơn / yêu cầu — days: số ngày lịch tính cả hôm nay (null = không giới hạn) */
export const TIME_OPTIONS = [
  { value: 'all', label: 'Mọi thời gian', days: null },
  { value: 'today', label: 'Hôm nay', days: 1 },
  { value: '7', label: '7 ngày qua', days: 7 },
  { value: '30', label: '30 ngày qua', days: 30 },
  { value: '90', label: '3 tháng qua', days: 90 },
]

/** Ngày iso có nằm trong khoảng đã chọn không (tính theo ngày lịch, từ 0h) */
export function withinRange(iso, value, now) {
  const days = TIME_OPTIONS.find((o) => o.value === value)?.days
  if (!days || !iso) return true
  const start = new Date(now)
  start.setHours(0, 0, 0, 0)
  start.setDate(start.getDate() - (days - 1))
  return new Date(iso) >= start
}
