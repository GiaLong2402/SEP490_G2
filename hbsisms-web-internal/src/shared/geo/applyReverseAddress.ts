import type { GeoReverse } from '@/shared/api/geo';
import { findProvinceByName, findWardByName } from './vnAdmin';

/** Map Goong reverse → form fields, matched to admin dropdown names when possible. */
export function applyReverseAddress(hit: GeoReverse): {
  line1: string | null;
  ward: string | null;
  district: string | null;
  province: string | null;
  latitude: number;
  longitude: number;
  distanceKm: number;
} {
  const provinceHit = findProvinceByName(hit.province);
  const province = provinceHit?.name ?? hit.province ?? null;
  const provinceCode = provinceHit?.code ?? null;
  const wardHit = findWardByName(provinceCode, hit.ward);
  return {
    line1: hit.line1?.trim() || null,
    ward: wardHit?.name ?? hit.ward ?? null,
    district: null,
    province,
    latitude: Number(hit.latitude),
    longitude: Number(hit.longitude),
    distanceKm: Number(hit.distanceKm),
  };
}
