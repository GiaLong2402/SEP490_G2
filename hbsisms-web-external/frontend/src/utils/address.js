/**
 * Ghép địa chỉ thành 1 dòng chữ. Nhận:
 * - chuỗi
 * - AddressResponse backend {line1, ward, district, province}
 * - object cũ từ form hồ sơ {street, ward, district, province}
 */
export function formatAddress(address) {
  if (!address) return ''
  if (typeof address === 'string') return address
  return [address.line1 || address.street, address.ward, address.district, address.province]
    .filter(Boolean)
    .join(', ')
}
