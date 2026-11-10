import { formatAddress } from '@/utils/address'
import { checkDto } from './checkDto'

/** Field giao diện bắt buộc cần (API_CONTRACT.md § 1) */
const REQUIRED = ['id', 'recipientName', 'phone', 'line1', 'province', 'isDefault']

/** AddressResponse (backend) → địa chỉ dùng trên giao diện */
export function fromAddressDto(dto) {
  checkDto(dto, REQUIRED, 'AddressResponse', '1. Địa chỉ')
  return {
    id: dto.id,
    recipientName: dto.recipientName || '',
    phone: dto.phone || '',
    line1: dto.line1 || '',
    ward: dto.ward || '',
    district: dto.district || '',
    province: dto.province || '',
    isDefault: Boolean(dto.isDefault),
    inDeliveryRadius: dto.inDeliveryRadius !== false,
    distanceKm: dto.distanceKm == null ? null : Number(dto.distanceKm),
    fullText: formatAddress(dto),
  }
}

/** Form địa chỉ → AddressRequest (body POST/PUT) */
export function toAddressRequest(form) {
  return {
    kind: 'SHIPPING',
    recipientName: form.recipientName.trim(),
    phone: form.phone.trim(),
    line1: form.line1.trim(),
    ward: form.ward.trim(),
    district: form.district.trim(),
    province: form.province.trim(),
    isDefault: Boolean(form.isDefault),
  }
}
