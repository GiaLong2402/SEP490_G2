const moneyFmt = new Intl.NumberFormat('vi-VN');

export const money = (value: number | null | undefined) => (value === null || value === undefined ? '—' : `${moneyFmt.format(value)} ₫`);
export const num = (value: number | null | undefined) => (value === null || value === undefined ? '—' : moneyFmt.format(value));

/** Tiền rút gọn cho nhãn hẹp: 18,5 tr */
export function moneyShort(value: number) {
  const abs = Math.abs(value);
  const fmt = (n: number) => n.toLocaleString('vi-VN', { maximumFractionDigits: 1 });
  if (abs >= 1e9) return `${fmt(value / 1e9)} tỷ`;
  if (abs >= 1e6) return `${fmt(value / 1e6)} tr`;
  if (abs >= 1e3) return `${fmt(value / 1e3)} k`;
  return String(value);
}

/** Bỏ dấu tiếng Việt để tìm kiếm phía giao diện. */
export const foldText = (text: string) => text.normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[đĐ]/g, 'd').toLowerCase();

export function date(value: string | null | undefined) {
  if (!value) return '—';
  const d = new Date(value.length === 10 ? `${value}T00:00:00` : value);
  return d.toLocaleDateString('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function dateTime(value: string | null | undefined) {
  if (!value) return '—';
  const d = new Date(value);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(d.getDate())}/${pad(d.getMonth() + 1)} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function daysUntil(value: string | null | undefined) {
  if (!value) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((new Date(`${value.slice(0, 10)}T00:00:00`).getTime() - today.getTime()) / 86_400_000);
}

export function todayISO(offsetDays = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
