/**
 * Ghép địa chỉ thành 1 dòng chữ (địa giới 2 cấp sau sáp nhập 2025).
 * Nhận chuỗi hoặc object {line1|street, ward, province}; `district` chỉ hiện nếu dữ liệu cũ còn.
 */
export function formatAddress(address) {
  if (!address) return ''
  if (typeof address === 'string') return address
  return [address.line1 || address.street, address.ward, address.province || address.district]
    .filter(Boolean)
    .join(', ')
}
