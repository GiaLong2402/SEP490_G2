/** Rules mirrored from API `CustomerService` (VN phone + B2B tax). */

const VN_PHONE = /^0\d{9}$/;
const TAX_CODE = /^\d{10,13}$/;

/** Normalize optional phone like API: trim, +84/84 → 0…; blank → null. */
export function normalizeVnPhone(raw: string | null | undefined): string | null {
  const value = raw?.trim() || null;
  if (!value) return null;
  let digits = value.replace(/[\s.-]/g, '');
  if (digits.startsWith('+84')) digits = `0${digits.slice(3)}`;
  else if (digits.startsWith('84') && digits.length === 11) digits = `0${digits.slice(2)}`;
  return digits;
}

export function validateOptionalPhone(raw: string | null | undefined, label = 'Số điện thoại'): string | undefined {
  const digits = normalizeVnPhone(raw);
  if (digits === null) return undefined;
  if (!VN_PHONE.test(digits)) return `${label} không hợp lệ`;
  return undefined;
}

export function validateOptionalTaxCode(raw: string | null | undefined): string | undefined {
  const tax = raw?.trim() || null;
  if (!tax) return undefined;
  if (!TAX_CODE.test(tax)) return 'Mã số thuế không hợp lệ (10–13 chữ số)';
  return undefined;
}
