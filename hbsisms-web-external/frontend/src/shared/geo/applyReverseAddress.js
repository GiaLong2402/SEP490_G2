import { findProvinceByName, findWardByName, provinceCodeOf } from './vnAdmin'

/** Map Goong reverse → form fields, matched to admin dropdown names when possible. */
export function applyReverseAddress(hit) {
  const provinceHit = findProvinceByName(hit.province)
  const province = provinceHit?.name ?? hit.province ?? ''
  const provinceCode = provinceHit?.code ?? provinceCodeOf(province)
  const wardHit = findWardByName(provinceCode, hit.ward)
  return {
    line1: hit.line1?.trim() || '',
    ward: wardHit?.name ?? hit.ward ?? '',
    district: '',
    province,
    latitude: Number(hit.latitude),
    longitude: Number(hit.longitude),
    distanceKm: Number(hit.distanceKm),
  }
}
