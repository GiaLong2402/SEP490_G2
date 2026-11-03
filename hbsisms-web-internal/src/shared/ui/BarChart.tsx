import { useState } from 'react';
import { moneyShort } from '@/shared/lib/format';

export interface BarDatum {
  label: string;
  value: number;
  /** Chữ hiện trong tooltip, mặc định là giá trị rút gọn. */
  display?: string;
}

function niceMax(value: number) {
  if (value <= 0) return 1;
  const mag = 10 ** Math.floor(Math.log10(value));
  const step = [1, 2, 2.5, 5, 10].find((s) => s * mag >= value) ?? 10;
  return step * mag;
}

/**
 * Biểu đồ cột một chuỗi: một màu, không cần chú giải vì tiêu đề thẻ đã gọi tên.
 * Có tooltip khi rê/focus và bảng số liệu thay thế cho trình đọc màn hình.
 */
export function BarChart({ data, height = 200, title }: { data: BarDatum[]; height?: number; title: string }) {
  const [active, setActive] = useState<number | null>(null);
  const max = niceMax(Math.max(...data.map((d) => d.value), 0));
  const ticks = [0, 0.5, 1].map((t) => t * max);
  const labelEvery = Math.ceil(data.length / 7);

  return (
    <figure className="m-0">
      <div className="relative flex" style={{ height }} onMouseLeave={() => setActive(null)}>
        <div className="relative w-12 shrink-0 text-right text-micro font-normal text-ink-faint" aria-hidden>
          {ticks.map((t) => (
            <span key={t} className="absolute right-2 -translate-y-1/2" style={{ bottom: `calc(${(t / max) * 100}% - 7px + ${t === 0 ? 7 : 0}px)` }}>
              {moneyShort(t)}
            </span>
          ))}
        </div>
        <div className="relative flex flex-1 items-end gap-[2px] border-b border-field">
          {ticks.slice(1).map((t) => (
            <div key={t} className="absolute inset-x-0 border-t border-line" style={{ bottom: `${(t / max) * 100}%` }} aria-hidden />
          ))}
          {data.map((d, i) => (
            <button
              key={d.label}
              type="button"
              aria-label={`${d.label}: ${d.display ?? moneyShort(d.value)}`}
              onMouseEnter={() => setActive(i)}
              onFocus={() => setActive(i)}
              onBlur={() => setActive(null)}
              className="relative z-[1] flex h-full flex-1 items-end justify-center rounded-t-[4px] hover:bg-rowalt/60"
            >
              <span className={`w-full max-w-[28px] rounded-t-[4px] ${active === i ? 'bg-primary-hover' : 'bg-primary'}`} style={{ height: `${(d.value / max) * 100}%`, minHeight: d.value > 0 ? 2 : 0 }} />
            </button>
          ))}
          {active !== null && (
            <div
              role="status"
              className="pointer-events-none absolute top-0 z-10 -translate-x-1/2 whitespace-nowrap rounded-sm border border-line bg-card px-2 py-1 text-small shadow-pop"
              style={{ left: `${Math.min(88, Math.max(12, ((active + 0.5) / data.length) * 100))}%` }}
            >
              <span className="text-ink-muted">{data[active].label}: </span>
              <span className="font-semibold tabular-nums">{data[active].display ?? moneyShort(data[active].value)}</span>
            </div>
          )}
        </div>
      </div>
      <div className="ml-12 mt-1 flex gap-[2px] text-micro font-normal text-ink-faint" aria-hidden>
        {data.map((d, i) => (
          <span key={d.label} className="flex w-0 flex-1 justify-center whitespace-nowrap">
            {i % labelEvery === 0 ? d.label : ''}
          </span>
        ))}
      </div>
      <details className="mt-2 text-small text-ink-muted">
        <summary className="cursor-pointer select-none">Xem bảng số liệu</summary>
        <table className="mt-2 w-full">
          <caption className="sr-only">{title}</caption>
          <tbody>
            {data.map((d) => (
              <tr key={d.label} className="border-t border-line">
                <th scope="row" className="py-1 text-left font-normal">
                  {d.label}
                </th>
                <td className="num py-1 text-right text-ink">{d.display ?? moneyShort(d.value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </details>
    </figure>
  );
}

/** Xếp hạng theo cột ngang: nhãn và số luôn dùng màu chữ, không dùng màu cột. */
export function RankBars({ data }: { data: BarDatum[] }) {
  const max = Math.max(...data.map((d) => d.value), 1);
  return (
    <ul className="space-y-2.5">
      {data.map((d) => (
        <li key={d.label}>
          <div className="mb-1 flex justify-between gap-3 text-small">
            <span className="min-w-0 truncate text-ink">{d.label}</span>
            <span className="num text-ink-muted">{d.display ?? moneyShort(d.value)}</span>
          </div>
          <div className="h-2 rounded-r-[4px] bg-rowalt">
            <div className="h-full rounded-r-[4px] bg-primary" style={{ width: `${(d.value / max) * 100}%` }} />
          </div>
        </li>
      ))}
    </ul>
  );
}
