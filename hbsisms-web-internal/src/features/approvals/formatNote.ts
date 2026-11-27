import { money } from '@/shared/lib/format';
import type { ApprovalType } from '@/shared/domain/enums';

/**
 * Đưa ghi chú phê duyệt (kể cả bản cũ dạng key=value) về câu tiếng Việt dễ đọc.
 */
export function formatApprovalNote(note: string | null | undefined, approvalType?: ApprovalType): string | null {
  const raw = note?.trim();
  if (!raw) return null;

  if (approvalType === 'order_price_override' || /discount\s*=|subtotal\s*=|ngưỡng\s*%\s*=/i.test(raw)) {
    const discount = matchNumber(raw, /discount\s*=\s*([0-9.]+)/i);
    const subtotal = matchNumber(raw, /subtotal\s*=\s*([0-9.]+)/i);
    const threshold = matchNumber(raw, /ngưỡng\s*%\s*=\s*([0-9.]+)/i);
    if (discount != null || subtotal != null) {
      const parts: string[] = [];
      if (discount != null && subtotal != null && subtotal > 0) {
        const pct = Math.round((discount / subtotal) * 1000) / 10;
        parts.push(
          `Giảm ${money(discount)} trên tạm tính ${money(subtotal)} (${pct}%${
            threshold != null ? ` — vượt ngưỡng ${threshold}%` : ''
          })`,
        );
      } else if (discount != null) {
        parts.push(`Giảm ${money(discount)}`);
      }
      const manual = raw.match(/(\d+)\s*dòng giá nhập tay/i);
      if (manual) parts.push(`Có ${manual[1]} dòng giá nhập tay`);
      if (parts.length) return parts.join('. ');
    }
  }

  // Bỏ tiền tố "Ghi chú:" nếu đã có sẵn trong chuỗi lưu.
  return raw.replace(/^Ghi chú:\s*/i, '');
}

function matchNumber(text: string, re: RegExp): number | null {
  const m = text.match(re);
  if (!m) return null;
  const n = Number(m[1]);
  return Number.isFinite(n) ? n : null;
}
