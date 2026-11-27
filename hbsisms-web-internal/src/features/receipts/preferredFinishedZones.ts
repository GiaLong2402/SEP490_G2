/**
 * Gợi ý khu kho TP theo mã/tên sản phẩm (quy ước sắp xếp — không khóa cứng).
 * Khớp mã vị trí / tên khu: CHA, CA, MUC, TOM…
 */
export function preferredFinishedZones(
  productCode?: string | null,
  productName?: string | null,
): string[] {
  const t = `${productCode ?? ''} ${productName ?? ''}`
    .normalize('NFC')
    .toUpperCase();
  const out: string[] = [];
  const add = (...codes: string[]) => {
    for (const c of codes) {
      if (!out.includes(c)) out.push(c);
    }
  };

  // Chả trước (tránh “CHA” trong từ khác) — SP-CHA-CA, Chả cá…
  if (/\bCHA\b|CHẢ|CHA-CA|CHACA/.test(t) || t.includes('CHẢ')) {
    add('CHA', 'CHẢ');
  }
  if (/\bMUC\b|MỰC|MUC-/.test(t) || t.includes('MỰC')) {
    add('MUC', 'MỰC');
  }
  if (/\bTOM\b|TÔM|TOM-/.test(t) || t.includes('TÔM')) {
    add('TOM', 'TÔM');
  }
  // Cá thuần (không phải Chả cá): SP-CA-… nhưng không có CHA
  if (!out.includes('CHA') && (/\bCA\b|CÁ/.test(t) || t.includes('CÁ'))) {
    add('CA', 'CÁ');
  }
  return out;
}

/** Vị trí có mã/khu khớp một trong các gợi ý (CHA, Chả, …). */
export function locationMatchesPreferred(
  location: { code: string; zone: string | null },
  preferred: string[],
): boolean {
  if (preferred.length === 0) return true;
  const hay = `${location.code} ${location.zone ?? ''}`
    .normalize('NFC')
    .toUpperCase();
  return preferred.some((p) => {
    const token = p.toUpperCase();
    return (
      hay === token
      || hay.startsWith(`${token}-`)
      || hay.startsWith(`${token} `)
      || hay.includes(` ${token}`)
      || hay.includes(`${token} ·`)
      || new RegExp(`(?:^|[^A-Z0-9])${token}(?:[^A-Z0-9]|$)`).test(hay)
    );
  });
}
