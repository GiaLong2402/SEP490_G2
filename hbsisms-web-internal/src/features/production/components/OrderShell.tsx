import type { InputHTMLAttributes, ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { ProductionOrderStatus } from '@/shared/domain/enums';
import { dateTimeFull } from '@/shared/lib/format';
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

/** Ô nhập có đơn vị ở cuối (khay, ₫, kg…). */
export function SuffixInput({ suffix, className = '', ...rest }: InputHTMLAttributes<HTMLInputElement> & { suffix: string }) {
  return (
    <div className="relative">
      <input className={`field-control pr-12 ${className}`} {...rest} />
      <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-ink-faint">{suffix}</span>
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
          <div key={k} className="flex justify-between gap-4">
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

export interface HistoryEvent { what: string; who?: string | null; at?: string | null }

/** Cột phải: Lịch sử hoạt động (mới nhất trên cùng). */
export function HistoryCard({ events }: { events: HistoryEvent[] }) {
  return (
    <section className="rounded-lg border border-line bg-card p-4 shadow-card">
      <div className={asideTitle}>Lịch sử hoạt động</div>
      <ol className="space-y-3">
        {events.map((e) => (
          <li key={e.what} className="flex gap-2.5">
            <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-primary" aria-hidden />
            <div>
              <div className="font-medium text-ink">{e.what}</div>
              {(e.who || e.at) && <div className="text-small text-ink-faint">{[e.who, e.at && dateTimeFull(e.at)].filter(Boolean).join(' · ')}</div>}
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
    ...(order.approvedAt ? [{ what: `Duyệt lệnh${order.materialIssueCode ? ` · tự tạo ${order.materialIssueCode}` : ''}`, who: order.approverName, at: order.approvedAt }] : []),
    { what: 'Tạo lệnh sản xuất', who: order.creatorName, at: order.createdAt },
  ];
}
