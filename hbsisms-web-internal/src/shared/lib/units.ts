/** Đơn vị khối lượng / thể tích — cho phép thập phân khi nhập NVL. */
const CONTINUOUS_UNITS = new Set(['kg', 'g', 'gram', 'l', 'lit', 'ml']);

export function isContinuousUnit(unit: string | null | undefined): boolean {
  if (!unit) return false;
  return CONTINUOUS_UNITS.has(unit.trim().toLowerCase());
}

/** Túi, cái, cuộn, khay… — chỉ số nguyên. */
export function isPieceUnit(unit: string | null | undefined): boolean {
  return !!unit?.trim() && !isContinuousUnit(unit);
}
