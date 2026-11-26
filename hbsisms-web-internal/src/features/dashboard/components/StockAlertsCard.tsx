import type { ReactNode } from 'react';
import { ChevronRight, CircleCheck, Clock, Package, PackageMinus, TriangleAlert } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useUnitLabel } from '@/features/receipts';
import { num } from '@/shared/lib/format';
import { Card, Chip } from '@/shared/ui';
import type { DashboardSummary, DashboardTasks } from '../types';

const PREVIEW = 4;
const qty = (v: number) => v.toLocaleString('vi-VN', { maximumFractionDigits: 2 });

/** Tiêu đề nhóm: biểu tượng + tên + số, bên phải là "Xem" — một dòng, không xuống hàng. */
function Section({ icon, title, count, link, children }: { icon: ReactNode; title: string; count: number; link: string; children: ReactNode }) {
  return (
    <section className="border-t border-line px-4 py-3 first:border-t-0">
      <div className="mb-2 flex items-center gap-2">
        <span className="shrink-0 text-ink-faint" aria-hidden>{icon}</span>
        <span className="min-w-0 flex-1 truncate text-small leading-5 font-semibold text-ink">{title}</span>
        <Chip tone={count > 0 ? 'red' : 'green'}>{num(count)}</Chip>
        <Link to={link} className="flex shrink-0 items-center whitespace-nowrap text-small text-primary hover:underline">
          Xem<ChevronRight size={14} aria-hidden />
        </Link>
      </div>
      {children}
    </section>
  );
}

function AllGood({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-center gap-2 text-small text-ink-muted">
      <CircleCheck size={15} className="shrink-0 text-green-fg" aria-hidden />
      {children}
    </p>
  );
}

function More({ count, what, to }: { count: number; what: string; to: string }) {
  if (count <= 0) return null;
  return <Link to={to} className="mt-1 block text-small text-ink-muted hover:text-primary">+ {num(count)} {what} khác</Link>;
}

/** Thiếu hàng: dòng 1 tên + nhãn thiếu, dòng 2 thanh còn / mức, dòng 3 mã · khu và con số. */
function ShortRow({ to, name, sub, have, need, unit }: { to: string; name: string; sub: string; have: number; need: number; unit: string }) {
  const ratio = need > 0 ? Math.max(0, Math.min(1, have / need)) : 0;
  const critical = have <= 0 || ratio < 0.25;
  return (
    <li>
      <Link to={to} className="-mx-2 block rounded px-2 py-1.5 hover:bg-rowalt">
        <div className="flex items-center gap-2">
          <span className="min-w-0 flex-1 truncate text-small leading-5 font-medium text-ink">{name}</span>
          <Chip tone={critical ? 'red' : 'amber'}>{have <= 0 ? 'Hết' : `Thiếu ${qty(need - have)} ${unit}`}</Chip>
        </div>
        <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-line/70" role="meter" aria-valuenow={Math.round(ratio * 100)} aria-valuemin={0} aria-valuemax={100} aria-label={`${name}: còn ${qty(have)} trên mức ${qty(need)} ${unit}`}>
          <div className={`h-full rounded-full ${critical ? 'bg-red-fg' : 'bg-amber-fg'}`} style={{ width: `${Math.max(ratio * 100, have > 0 ? 3 : 0)}%` }} />
        </div>
        <div className="mt-1 flex items-center justify-between gap-2 text-micro leading-4 text-ink-faint">
          <span className="truncate">{sub}</span>
          <span className="shrink-0 tabular-nums">Còn {qty(have)} / {qty(need)} {unit}</span>
        </div>
      </Link>
    </li>
  );
}

/**
 * Cảnh báo kho gộp một chỗ: hạn sử dụng (task 9 — Khôi) + tồn thấp nguyên liệu / thành phẩm (dòng 10).
 * {@code lowStock} null khi chưa tải xong /dashboard/tasks — tạm dùng danh sách tồn thấp của /dashboard/summary.
 */
export function StockAlertsCard({ inventory, lowStock }: { inventory: NonNullable<DashboardSummary['inventory']>; lowStock: DashboardTasks['lowStock'] }) {
  const unitLabel = useUnitLabel();
  const nearCount = inventory.nearExpiryLots ?? inventory.nearExpiry.length;
  const expired = inventory.expiredLots ?? 0;
  const finished = lowStock?.finished ?? inventory.lowStock;
  const raw = lowStock?.raw ?? [];
  const total = nearCount + expired + finished.length + raw.length;
  const nearShown = inventory.nearExpiry.slice(0, PREVIEW);

  return (
    <Card title="Cảnh báo kho" flush actions={total > 0 ? <Chip tone="red">{num(total)} cảnh báo</Chip> : <Chip tone="green">Ổn</Chip>}>
      <Section icon={<Clock size={15} />} title="Hạn sử dụng" count={nearCount + expired} link="/inventory?tab=expiry">
        {nearCount + expired === 0 ? (
          <AllGood>Không có lô nào sắp hết hạn</AllGood>
        ) : (
          <>
            {expired > 0 && (
              <Link to="/inventory?tab=expiry" className="mb-2 flex items-center gap-2 rounded-md bg-red-bg px-2.5 py-1.5 text-small font-medium text-red-fg hover:opacity-90">
                <TriangleAlert size={14} className="shrink-0" aria-hidden />
                <span className="min-w-0 flex-1">{num(expired)} lô đã hết hạn, chưa tiêu hủy</span>
                <ChevronRight size={14} className="shrink-0" aria-hidden />
              </Link>
            )}
            {nearShown.length > 0 && (
              <ul>
                {nearShown.map((l) => (
                  <li key={l.lotCode}>
                    <Link to="/inventory?tab=expiry" className="-mx-2 flex items-center gap-2 rounded px-2 py-1.5 hover:bg-rowalt">
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-small leading-5 font-medium text-ink">{l.productName}</span>
                        <span className="block truncate text-micro leading-4 text-ink-faint">{l.lotCode} · {num(l.qty)} khay</span>
                      </span>
                      <Chip tone={l.daysLeft <= 1 ? 'red' : 'amber'}>{l.daysLeft < 0 ? `Quá ${-l.daysLeft} ngày` : l.daysLeft === 0 ? 'Hết hạn hôm nay' : `Còn ${l.daysLeft} ngày`}</Chip>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
            <More count={nearCount - nearShown.length} what="lô sắp hết hạn" to="/inventory?tab=expiry" />
          </>
        )}
      </Section>

      {lowStock && (
        <Section icon={<PackageMinus size={15} />} title="Nguyên liệu tồn thấp" count={raw.length} link="/receipts?tab=raw">
          {raw.length === 0 ? (
            <AllGood>Nguyên liệu đều trên mức đặt lại</AllGood>
          ) : (
            <>
              <ul className="space-y-1">
                {raw.slice(0, PREVIEW).map((m) => (
                  <ShortRow
                    key={m.id}
                    to="/receipts?tab=raw"
                    name={m.name}
                    sub={[m.code, m.locationCode].filter(Boolean).join(' · ')}
                    have={m.qtyOnHand}
                    need={m.reorderLevel}
                    unit={unitLabel(m.unit)}
                  />
                ))}
              </ul>
              <More count={raw.length - PREVIEW} what="nguyên liệu" to="/receipts?tab=raw" />
            </>
          )}
        </Section>
      )}

      <Section icon={<Package size={15} />} title="Thành phẩm tồn thấp" count={finished.length} link="/inventory">
        {finished.length === 0 ? (
          <AllGood>{lowStock ? `Mọi quy cách còn từ ${num(lowStock.threshold)} khay trở lên` : 'Không có quy cách nào tồn thấp'}</AllGood>
        ) : (
          <>
            <ul className="space-y-1">
              {finished.slice(0, PREVIEW).map((s) =>
                lowStock ? (
                  <ShortRow key={s.sku} to="/inventory" name={s.productName} sub={s.sku} have={s.available} need={lowStock.threshold} unit="khay" />
                ) : (
                  <li key={s.sku}>
                    <Link to="/inventory" className="-mx-2 flex items-center gap-2 rounded px-2 py-1.5 hover:bg-rowalt">
                      <span className="min-w-0 flex-1 truncate text-small leading-5 font-medium text-ink">{s.productName}</span>
                      <Chip tone="red">Còn {num(s.available)} khay</Chip>
                    </Link>
                  </li>
                ),
              )}
            </ul>
            <More count={finished.length - PREVIEW} what="quy cách" to="/inventory" />
          </>
        )}
      </Section>
    </Card>
  );
}
