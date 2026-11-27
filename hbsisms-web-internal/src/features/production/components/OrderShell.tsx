import type { InputHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { ProductionOrderStatus } from '@/shared/domain/enums';
import { date, dateTimeFull } from '@/shared/lib/format';
import type { Tone } from '@/shared/domain/enums';
import type { ProductionOrder } from '../types';
import { StepTracker } from './StepTracker';

/** Nhãn trạng thái trên tiêu đề lệnh (theo bản thiết kế). */
const TITLE_STATUS: Record<ProductionOrderStatus, { label: string; tone: Tone }> = {
  draft: { label: 'Chờ duyệt', tone: 'neutral' },
  approved: { label: 'Đã duyệt · chờ xuất nguyên liệu', tone: 'blue' },
  in_progress: { label: 'Đang sản xuất', tone: 'blue' },
  awaiting_receipt: { label: 'Chờ nhập kho', tone: 'amber' },
  completed: { label: 'Hoàn thành', tone: 'green' },
  cancelled: { label: 'Đã hủy', tone: 'red' },
};
const toneClass: Record<Tone, string> = {
  green: 'bg-green-bg text-green-fg',
  amber: 'bg-amber-bg text-amber-fg',
  red: 'bg-red-bg text-red-fg',
  blue: 'bg-blue-bg text-blue-fg',
  neutral: 'bg-neutral-bg text-neutral-fg',
};

/** Khối có tiêu đề + dòng mô tả + phần phải. */
export function Section({ title, subtitle, right, children, flush }: { title: ReactNode; subtitle?: ReactNode; right?: ReactNode; children: ReactNode; flush?: boolean }) {
  return (
    <section className="rounded-lg border border-line bg-card shadow-card">
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-4 py-3">
        <div className="min-w-0">
          <h2 className="text-h2 text-ink">{title}</h2>
          {subtitle && <p className="mt-0.5 text-small text-ink-muted">{subtitle}</p>}
        </div>
        {right && <div className="flex items-center gap-2">{right}</div>}
      </header>
      <div className={flush ? '' : 'p-4'}>{children}</div>
    </section>
  );
}

/** Thanh cuối khối chính: mô tả bên trái, nút bên phải (hoặc xếp dưới khi `stacked`). */
export function ActionBar({ children, actions, stacked }: { children: ReactNode; actions: ReactNode; stacked?: boolean }) {
  return (
    <section className={`flex gap-3 rounded-lg border border-line bg-card px-4 py-3 shadow-card ${stacked ? 'flex-col' : 'flex-wrap items-center justify-between'}`}>
      <div className="flex min-w-0 flex-col gap-0.5 text-small text-ink-muted">{children}</div>
      <div className="flex flex-wrap items-center gap-2">{actions}</div>
    </section>
  );
}

/** Ô nhập + đơn vị tách cột (không đè số). */
export function SuffixInput({
  suffix,
  className = '',
  disabled,
  ...rest
}: InputHTMLAttributes<HTMLInputElement> & { suffix: string }) {
  return (
    <div
      className={`flex h-[34px] w-full overflow-hidden rounded-sm border border-field bg-card focus-within:border-primary focus-within:outline-none focus-within:ring-2 focus-within:ring-primary/20 ${
        disabled ? 'bg-rowalt' : ''
      }`}
    >
      <input
        disabled={disabled}
        className={`h-full min-w-0 flex-1 border-0 bg-transparent px-2.5 text-body tabular-nums text-ink outline-none placeholder:text-ink-faint disabled:text-ink-faint [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none [&::-webkit-outer-spin-button]:appearance-none ${className}`}
        {...rest}
      />
      <span
        aria-hidden
        className="flex shrink-0 items-center border-l border-line bg-rowalt/70 px-2.5 text-small text-ink-muted whitespace-nowrap"
      >
        {suffix}
      </span>
    </div>
  );
}

const asideTitle = 'mb-3 text-micro font-semibold uppercase tracking-wide text-ink-muted';

/** Cột phải: Tổng quan lệnh. `footer` = dòng nổi bật dưới vạch (ví dụ Giá vốn dự tính). */
export function OverviewCard({ items, footer }: { items: [string, ReactNode][]; footer?: [string, ReactNode] }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4 shadow-card">
      <div className={asideTitle}>Tổng quan lệnh</div>
      <dl className="space-y-2.5">
        {items.map(([k, v]) => (
          <div key={k} className="flex items-start justify-between gap-4">
            <dt className="shrink-0 text-ink-muted">{k}</dt>
            <dd className="min-w-0 text-right font-semibold tabular-nums text-ink">{v ?? '—'}</dd>
          </div>
        ))}
      </dl>
      {footer && (
        <div className="mt-3 flex items-baseline justify-between gap-4 border-t border-line pt-3">
          <span className="text-ink-muted">{footer[0]}</span>
          <span className="text-right">{footer[1]}</span>
        </div>
      )}
    </section>
  );
}

/** Một mốc lịch sử: tiêu đề ngắn, các ý chi tiết (mỗi ý một dòng), người làm và thời điểm. */
export interface HistoryEvent { what: string; details?: string[]; who?: string | null; at?: string | null }

/** "2026-11-25" (chỉ ngày) thì hiện ngày; có giờ thì hiện ngày giờ — tránh "07:00:00" giả. */
const whenText = (at: string) => (/^\d{4}-\d{2}-\d{2}$/.test(at) ? date(at) : dateTimeFull(at));

/** Cột phải: Lịch sử hoạt động (mới nhất trên cùng), dạng dòng thời gian. */
export function HistoryCard({ events }: { events: HistoryEvent[] }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4 shadow-card">
      <div className={asideTitle}>Lịch sử hoạt động</div>
      <ol>
        {events.map((e, idx) => (
          <li key={`${idx}-${e.what}`} className="relative flex gap-3 pb-4 last:pb-0">
            {idx < events.length - 1 && <span className="absolute left-[4px] top-4 bottom-0 w-px bg-line" aria-hidden />}
            <span className={`relative mt-1.5 h-2.5 w-2.5 shrink-0 rounded-full ${idx === 0 ? 'bg-primary' : 'bg-ink-faint/60'}`} aria-hidden />
            <div className="min-w-0">
              <div className="font-semibold leading-5 text-ink">{e.what}</div>
              {e.details && e.details.length > 0 && (
                <ul className="mt-1 space-y-0.5 text-small text-ink-muted">
                  {e.details.map((d) => (
                    <li key={d} className="flex gap-1.5"><span className="text-ink-faint" aria-hidden>–</span><span className="min-w-0">{d}</span></li>
                  ))}
                </ul>
              )}
              {(e.who || e.at) && (
                <div className="mt-1 text-small text-ink-faint">
                  {e.who && <span>{e.who}</span>}
                  {e.who && e.at && <span aria-hidden> · </span>}
                  {e.at && <span className="tabular-nums">{whenText(e.at)}</span>}
                </div>
              )}
            </div>
          </li>
        ))}
      </ol>
    </section>
  );
}

/** Bố cục một bước: nội dung bên trái, cột phải (Tổng quan / Lịch sử). */
export function StepLayout({ children, aside }: { children: ReactNode; aside: ReactNode }) {
  return (
    <div className="grid items-start gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
      <div className="min-w-0 space-y-4">{children}</div>
      <aside className="space-y-4">{aside}</aside>
    </div>
  );
}

/** Khung chung: "Lệnh LSX-… [trạng thái]" + "← Về danh sách", thanh 5 bước, rồi nội dung bước. */
export function OrderShell({ order, status, children }: { order: ProductionOrder | null; status: ProductionOrderStatus; children: ReactNode }) {
  const st = TITLE_STATUS[status];
  return (
    <>
      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <h1 className="flex flex-wrap items-center gap-2.5 text-display text-ink">
          Lệnh <span className="font-mono text-primary">{order?.code ?? 'mới'}</span>
          <span className={`inline-flex h-6 items-center rounded-full px-2.5 text-small font-semibold ${toneClass[st.tone]}`}>{st.label}</span>
        </h1>
        <Link to="/production-orders" className="text-small text-primary hover:underline">← Về danh sách</Link>
      </div>
      <StepTracker status={status} />
      {children}
    </>
  );
}

/** Các mốc lịch sử dựng từ dữ liệu lệnh (+ phiếu xuất nếu có). */
export function orderHistory(order: ProductionOrder | null, issue?: { code: string; completedAt: string | null; completerName: string | null } | null, extra: HistoryEvent[] = []): HistoryEvent[] {
  if (!order) return [{ what: 'Lệnh mới, chưa lưu' }];
  return [
    ...extra,
    ...(issue?.completedAt ? [{ what: `Xuất nguyên liệu ${issue.code}`, who: issue.completerName, at: issue.completedAt }] : []),
    ...(order.approvedAt
      ? [{ what: 'Duyệt lệnh', details: order.materialIssueCode ? [`Tự tạo phiếu xuất ${order.materialIssueCode}`] : undefined, who: order.approverName, at: order.approvedAt }]
      : []),
    { what: 'Tạo lệnh sản xuất', who: order.creatorName, at: order.createdAt },
  ];
}
