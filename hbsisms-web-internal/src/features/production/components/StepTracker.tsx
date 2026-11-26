import { Check } from 'lucide-react';
import type { ProductionOrderStatus } from '@/shared/domain/enums';

export const PRODUCTION_STEPS = [
  { name: 'Lập kế hoạch', sub: 'Kế hoạch & định mức' },
  { name: 'Xuất nguyên liệu', sub: 'Phiếu xuất PX' },
  { name: 'Sản xuất', sub: 'Báo sản lượng' },
  { name: 'Nhập thành phẩm', sub: 'Phiếu nhập kho' },
  { name: 'Hoàn thành', sub: 'Tổng kết · tự đóng lệnh' },
] as const;

/** Bước đang làm (0–4); `completed` đứng ở bước 5 (đã xong 4 bước trước). */
export function stepOf(status: ProductionOrderStatus) {
  return { draft: 0, approved: 1, in_progress: 2, awaiting_receipt: 3, completed: 4, cancelled: -1 }[status];
}

/** Thanh "Tiến trình sản xuất" 5 bước trên trang lệnh. */
export function StepTracker({ status }: { status: ProductionOrderStatus }) {
  const current = stepOf(status);
  // Lệnh đã hoàn thành: cả 5 bước đều xong → bước 5 cũng tick xanh, không còn "đang làm".
  const allDone = status === 'completed';
  return (
    <nav aria-label="Tiến trình sản xuất" className="mb-4 rounded-lg border border-line bg-card px-4 pb-4 pt-3 shadow-card">
      <div className="mb-2.5 flex items-center justify-between gap-2">
        <span className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Tiến trình sản xuất</span>
        {allDone && <span className="inline-flex items-center gap-1 text-small font-semibold text-green-fg"><Check size={14} strokeWidth={3} aria-hidden />Đã xong 5/5 bước</span>}
      </div>
      <ol className="grid gap-2 sm:grid-cols-5">
        {PRODUCTION_STEPS.map((s, i) => {
          const done = allDone || current > i;
          const now = !allDone && current === i;
          const last = allDone && i === PRODUCTION_STEPS.length - 1;
          return (
            <li key={s.name} aria-current={now || last ? 'step' : undefined} className={`flex items-start gap-2.5 rounded-lg p-2.5 ${now ? 'bg-primary/10' : last ? 'bg-green-bg' : ''} ${current < 0 ? 'opacity-50' : ''}`}>
              <span
                className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-small font-bold ${
                  done ? 'bg-green-fg text-card' : now ? 'bg-primary text-card' : 'border border-line bg-rowalt text-ink-muted'
                }`}
              >
                {done ? <Check size={16} strokeWidth={3} /> : `0${i + 1}`}
              </span>
              <span className="min-w-0">
                <span className="block font-semibold text-ink">{s.name}</span>
                <span className="block text-small text-ink-muted">{s.sub}</span>
              </span>
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

/** Thanh tiến trình ngắn ở danh sách lệnh (5 vạch). */
export function StepDots({ status }: { status: ProductionOrderStatus }) {
  const current = stepOf(status);
  const text = status === 'cancelled' ? 'Đã dừng' : status === 'completed' ? 'Đã xong 5/5' : `Bước ${current + 1}/5 · ${PRODUCTION_STEPS[current].name}`;
  const doneCount = status === 'completed' ? 5 : current;
  return (
    <div>
      <div className="flex items-center gap-1.5" aria-hidden>
        {PRODUCTION_STEPS.map((s, i) => (
          <span key={s.name} className={`h-1.5 w-7 rounded-full ${i < doneCount ? 'bg-green-fg' : i === current && status !== 'completed' ? 'bg-primary' : 'bg-line'}`} />
        ))}
      </div>
      <div className="mt-1.5 text-small text-ink-muted">{text}</div>
    </div>
  );
}
