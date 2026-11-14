import raw from './vn-admin.json'

const provinces = raw.provinces
const wards = raw.wards

export function listProvinces() {
  return provinces
}

export function listWardsByProvinceCode(provinceCode) {
  if (!provinceCode) return []
  return wards.filter((w) => w.provinceCode === provinceCode)
}

export function findProvinceByName(name) {
  if (!name?.trim()) return undefined
  const n = name.trim().toLowerCase()
  return (
    provinces.find((p) => p.name.toLowerCase() === n) ||
    provinces.find((p) => p.name.toLowerCase().includes(n) || n.includes(p.name.toLowerCase())) ||
    // "Hà Nội" ↔ "Thành phố Hà Nội"
    provinces.find((p) => {
      const short = p.name
        .toLowerCase()
        .replace(/^thành phố\s+/i, '')
        .replace(/^tỉnh\s+/i, '')
      return short === n || n.includes(short) || short.includes(n)
    })
  )
}

export function findWardByName(provinceCode, name) {
  if (!name?.trim()) return undefined
  const n = name.trim().toLowerCase()
  return listWardsByProvinceCode(provinceCode).find(
    (w) => w.name.toLowerCase() === n || w.name.toLowerCase().includes(n),
  )
}

export function provinceCodeOf(name) {
  return findProvinceByName(name)?.code ?? null
}
