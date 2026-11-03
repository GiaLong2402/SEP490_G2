import type { ReactNode } from 'react';
import type { Tone } from '@/shared/domain/enums';

const toneClass: Record<Tone, string> = {
  green: 'bg-green-bg text-green-fg',
  amber: 'bg-amber-bg text-amber-fg',
  red: 'bg-red-bg text-red-fg',
  blue: 'bg-blue-bg text-blue-fg',
  neutral: 'bg-neutral-bg text-neutral-fg',
};

export function Chip({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-flex h-5 items-center whitespace-nowrap rounded-full px-2 text-micro font-semibold ${toneClass[tone]}`}>{children}</span>;
}

/** Chip đọc từ bảng meta của enum, ví dụ <StatusChip meta={orderStateMeta} value={o.state} />. */
export function StatusChip<K extends string>({ meta, value }: { meta: Record<K, { label: string; tone: Tone }>; value: K }) {
  return <Chip tone={meta[value].tone}>{meta[value].label}</Chip>;
}

export function Card({ title, actions, children, className = '', flush }: { title?: ReactNode; actions?: ReactNode; children: ReactNode; className?: string; flush?: boolean }) {
  return (
    <section className={`rounded-md border border-line bg-card shadow-card ${className}`}>
      {(title || actions) && (
        <header className="flex min-h-[46px] items-center justify-between gap-3 border-b border-line px-4 py-2">
          <h2 className="text-h2 text-ink">{title}</h2>
          {actions && <div className="flex items-center gap-2">{actions}</div>}
        </header>
      )}
      <div className={flush ? '' : 'p-4'}>{children}</div>
    </section>
  );
}

export function PageHeader({ title, subtitle, actions, breadcrumb }: { title: ReactNode; subtitle?: ReactNode; actions?: ReactNode; breadcrumb?: ReactNode }) {
  return (
    <div className="mb-4">
      {breadcrumb && <div className="mb-1 text-small text-ink-faint">{breadcrumb}</div>}
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h1 className="text-display text-ink">{title}</h1>
          {subtitle && <p className="mt-0.5 text-body text-ink-muted">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </div>
  );
}

export function StatCard({ label, value, hint, tone }: { label: string; value: ReactNode; hint?: ReactNode; tone?: 'danger' | 'warning' }) {
  const valueClass = tone === 'danger' ? 'text-red-fg' : tone === 'warning' ? 'text-amber-fg' : 'text-ink';
  return (
    <div className="rounded-md border border-line bg-card p-4 shadow-card">
      <div className="text-small text-ink-muted">{label}</div>
      <div className={`mt-1 text-display tabular-nums ${valueClass}`}>{value}</div>
      {hint && <div className="mt-0.5 text-small text-ink-faint">{hint}</div>}
    </div>
  );
}

export function StatGrid({ children, cols = 4 }: { children: ReactNode; cols?: 3 | 4 | 5 }) {
  const c = { 3: 'lg:grid-cols-3', 4: 'lg:grid-cols-4', 5: 'lg:grid-cols-5' }[cols];
  return <div className={`mb-4 grid grid-cols-2 gap-3 ${c}`}>{children}</div>;
}

/** Cặp nhãn - giá trị trong các thẻ chi tiết. */
export function InfoList({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="space-y-2">
      {items.map(([label, value]) => (
        <div key={label} className="flex justify-between gap-4">
          <dt className="shrink-0 text-ink-muted">{label}</dt>
          <dd className="min-w-0 text-right text-ink">{value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}

export function Meter({ value, max, label }: { value: number; max: number; label?: string }) {
  const ratio = max > 0 ? value / max : 0;
  const color = ratio > 1 ? 'bg-coral' : ratio >= 0.8 ? 'bg-amber-fg' : 'bg-primary';
  const pct = Math.round(ratio * 100);
  return (
    <div className="flex items-center gap-2" role="meter" aria-valuenow={pct} aria-valuemin={0} aria-valuemax={100} aria-label={label ?? 'Tỷ lệ sử dụng'}>
      <div className="h-1.5 min-w-[60px] flex-1 overflow-hidden rounded-full bg-rowalt">
        <div className={`h-full rounded-full ${color}`} style={{ width: `${Math.min(100, ratio * 100)}%` }} />
      </div>
      <span className="num w-9 text-right text-ink-muted">{pct}%</span>
    </div>
  );
}

export function Alert({ tone = 'warning', title, children }: { tone?: 'warning' | 'danger' | 'info'; title?: ReactNode; children?: ReactNode }) {
  const cls = { warning: 'border-amber-fg/30 bg-amber-bg text-amber-fg', danger: 'border-coral/50 bg-red-bg text-red-fg', info: 'border-blue-fg/25 bg-blue-bg text-blue-fg' }[tone];
  return (
    <div role="alert" className={`rounded-md border px-3 py-2 text-body ${cls}`}>
      {title && <div className="font-semibold">{title}</div>}
      {children && <div>{children}</div>}
    </div>
  );
}
