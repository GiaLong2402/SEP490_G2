/**
 * Lấy chữ cái viết tắt từ họ tên.
 * "Nguyễn Văn An" → "VA" (2 từ cuối)
 */
export function getInitials(name) {
  if (!name) return '?'
  const parts = String(name).trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase()
  if (parts.length === 2) {
    return (parts[0][0] + parts[1][0]).toUpperCase()
  }
  const a = parts[parts.length - 2][0]
  const b = parts[parts.length - 1][0]
  return (a + b).toUpperCase()
}
