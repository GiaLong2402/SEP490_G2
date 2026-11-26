import { ChevronRight } from 'lucide-react';
import { Link } from 'react-router-dom';
import { deliveryStateMeta } from '@/shared/domain/enums';
import { money, num } from '@/shared/lib/format';
import { Card, StatusChip } from '@/shared/ui';
import type { DashboardSummary } from '../types';

/** Các đợt giao hôm nay còn chờ / đang đi (dữ liệu /dashboard/summary). */
export function DeliveriesTodayCard({ deliveries }: { deliveries: NonNullable<DashboardSummary['deliveries']> }) {
  return (
    <Card
      title={`Giao hàng hôm nay · ${num(deliveries.today)} đợt`}
      flush
      actions={<Link to="/deliveries" className="text-small text-primary hover:underline">Xem tất cả</Link>}
    >
      {deliveries.items.length === 0 ? (
        <p className="px-4 py-3 text-small text-ink-muted">{deliveries.today > 0 ? 'Các đợt hôm nay đã giao xong' : 'Hôm nay không có đợt giao nào'}</p>
      ) : (
        <ul>
          {deliveries.items.map((d) => (
            <li key={d.id} className="border-t border-line first:border-t-0">
              <Link to={d.code ? `/deliveries?q=${d.code}` : "/deliveries"} className="flex items-center gap-2 px-4 py-2 hover:bg-rowalt">
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-medium">{d.customerName}</span>
                  <span className="num block truncate text-micro leading-4 text-ink-faint">{[d.code, d.timeWindow].filter(Boolean).join(' · ')}</span>
                </span>
                <StatusChip meta={deliveryStateMeta} value={d.state} />
                <ChevronRight size={14} className="shrink-0 text-ink-faint" aria-hidden />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}

/** Khách B2B nợ quá hạn nhiều nhất (Chủ/Admin). */
export function OverdueDebtCard({ debt }: { debt: NonNullable<DashboardSummary['debt']> }) {
  return (
    <Card title="Khách nợ quá hạn" flush actions={<Link to="/debt" className="text-small text-primary hover:underline">Xem công nợ</Link>}>
      <ul>
        {debt.overdueCustomers.map((c) => (
          <li key={c.customerId} className="border-t border-line first:border-t-0">
            <Link to="/debt" className="flex items-center gap-2 px-4 py-2 hover:bg-rowalt">
              <span className="min-w-0 flex-1 truncate font-medium">{c.name}</span>
              <span className="num shrink-0 text-small font-semibold text-red-fg">{money(c.overdue)}</span>
              <ChevronRight size={14} className="shrink-0 text-ink-faint" aria-hidden />
            </Link>
          </li>
        ))}
      </ul>
    </Card>
  );
}
