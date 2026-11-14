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
    latitude: dto.latitude == null ? null : Number(dto.latitude),
    longitude: dto.longitude == null ? null : Number(dto.longitude),
    isDefault: Boolean(dto.isDefault),
    inDeliveryRadius: dto.inDeliveryRadius !== false,
    distanceKm: dto.distanceKm == null ? null : Number(dto.distanceKm),
    fullText: formatAddress(dto),
  }
}

/** Form địa chỉ → AddressRequest (body POST/PUT). district bỏ sau sáp nhập 2025. */
export function toAddressRequest(form) {
  return {
    kind: 'SHIPPING',
    recipientName: form.recipientName.trim(),
    phone: form.phone.trim(),
    line1: form.line1.trim(),
    ward: (form.ward || '').trim(),
    district: '',
    province: form.province.trim(),
    latitude: form.latitude == null || form.latitude === '' ? null : Number(form.latitude),
    longitude: form.longitude == null || form.longitude === '' ? null : Number(form.longitude),
    distanceKm: form.distanceKm == null || form.distanceKm === '' ? null : Number(form.distanceKm),
    isDefault: Boolean(form.isDefault),
  }
}
