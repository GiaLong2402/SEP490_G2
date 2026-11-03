/**
 * Định dạng giá tiền Việt Nam: 165000 -> "165.000đ"
 */
export function formatPrice(amount) {
  if (amount == null || Number.isNaN(Number(amount))) return '—'
  return `${Number(amount).toLocaleString('vi-VN')}đ`
}

/**
 * Định dạng khối lượng: 500 -> "500 g", 1000 -> "1 kg"
 */
export function formatWeight(grams) {
  if (grams >= 1000) {
    const kg = grams / 1000
    return Number.isInteger(kg) ? `${kg} kg` : `${kg} kg`
  }
  return `${grams} g`
}

/**
 * Tên quy cách khay: 500 -> "Khay 500 g", 1000 -> "Khay 1 kg"
 */
export function formatTrayLabel(grams) {
  return `Khay ${formatWeight(grams)}`
}
