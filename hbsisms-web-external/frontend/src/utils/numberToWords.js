const DIGITS = ['không', 'một', 'hai', 'ba', 'bốn', 'năm', 'sáu', 'bảy', 'tám', 'chín']
const UNITS = ['', 'nghìn', 'triệu', 'tỷ']

/** Đọc 1 nhóm 3 chữ số. full = true khi không phải nhóm đầu (đọc đủ "không trăm", "linh") */
function readTriple(n, full) {
  const h = Math.floor(n / 100)
  const t = Math.floor((n % 100) / 10)
  const u = n % 10
  const out = []
  if (full || h > 0) out.push(DIGITS[h], 'trăm')
  if (t === 0) {
    if (u > 0) {
      if (full || h > 0) out.push('linh')
      out.push(DIGITS[u])
    }
  } else {
    out.push(t === 1 ? 'mười' : `${DIGITS[t]} mươi`)
    if (u === 1) out.push(t === 1 ? 'một' : 'mốt')
    else if (u === 4) out.push(t === 1 ? 'bốn' : 'tư')
    else if (u === 5) out.push('lăm')
    else if (u > 0) out.push(DIGITS[u])
  }
  return out.join(' ')
}

/**
 * Đọc số tiền thành chữ tiếng Việt: 255000 -> "Hai trăm năm mươi lăm nghìn đồng"
 * (hỗ trợ tới hàng nghìn tỷ)
 */
export function moneyToWords(amount) {
  let n = Math.round(Number(amount) || 0)
  if (n === 0) return 'Không đồng'
  const groups = []
  while (n > 0) {
    groups.push(n % 1000)
    n = Math.floor(n / 1000)
  }
  const parts = []
  for (let i = groups.length - 1; i >= 0; i--) {
    if (groups[i] === 0) continue
    const isFirst = i === groups.length - 1
    parts.push(readTriple(groups[i], !isFirst), UNITS[i])
  }
  const text = parts.filter(Boolean).join(' ')
  return `${text.charAt(0).toUpperCase()}${text.slice(1)} đồng`
}
