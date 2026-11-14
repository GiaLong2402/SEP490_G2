import raw from './vn-admin.json';

export type VnProvince = { code: string; name: string };
export type VnWard = { code: string; name: string; provinceCode: string };

const provinces = (raw as { provinces: VnProvince[] }).provinces;
const wards = (raw as { wards: VnWard[] }).wards;

export function listProvinces(): VnProvince[] {
  return provinces;
}

export function listWardsByProvinceCode(provinceCode: string | null | undefined): VnWard[] {
  if (!provinceCode) return [];
  return wards.filter((w) => w.provinceCode === provinceCode);
}

export function findProvinceByName(name: string | null | undefined): VnProvince | undefined {
  if (!name?.trim()) return undefined;
  const n = name.trim().toLowerCase();
  return (
    provinces.find((p) => p.name.toLowerCase() === n) ||
    provinces.find((p) => p.name.toLowerCase().includes(n) || n.includes(p.name.toLowerCase())) ||
    provinces.find((p) => {
      const short = p.name
        .toLowerCase()
        .replace(/^thành phố\s+/i, '')
        .replace(/^tỉnh\s+/i, '');
      return short === n || n.includes(short) || short.includes(n);
    })
  );
}

export function findWardByName(
  provinceCode: string | null | undefined,
  name: string | null | undefined,
): VnWard | undefined {
  if (!name?.trim()) return undefined;
  const n = name.trim().toLowerCase();
  return listWardsByProvinceCode(provinceCode).find(
    (w) => w.name.toLowerCase() === n || w.name.toLowerCase().includes(n),
  );
}

/** resolveCode from stored province name when editing. */
export function provinceCodeOf(name: string | null | undefined): string | null {
  return findProvinceByName(name)?.code ?? null;
}
