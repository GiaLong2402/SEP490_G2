import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList } from 'lucide-react';
import { dateTime } from '@/shared/lib/format';
import { Button, Chip, Drawer, ErrorState } from '@/shared/ui';
import { useOwnerInbox } from '../hooks';
import type { OwnerInboxType } from '../types';

const TYPE_META: Record<OwnerInboxType, { label: string; tone: 'amber' | 'red' | 'blue' | 'neutral' }> = {
  discount_or_limit_approval: { label: 'Chờ duyệt', tone: 'amber' },
  needs_refund: { label: 'Hoàn tiền', tone: 'red' },
  payment_mismatch: { label: 'Lệch tiền', tone: 'amber' },
  late_payos_refund: { label: 'PayOS muộn', tone: 'red' },
  delivery_failed_open: { label: 'Giao thất bại', tone: 'red' },
  return_pending_inspect: { label: 'Trả hàng', tone: 'blue' },
};

/** Nút badge + drawer việc cần Chủ — Inter 3 dòng 13 sóng B (API dòng 11). */
export function OwnerInboxPanel() {
  const [open, setOpen] = useState(false);
  const { data, isLoading, error, refetch } = useOwnerInbox(true);
  const items = data?.items ?? [];
  const count = items.length;

  return (
    <>
      <Button
        type="button"
        icon={<ClipboardList size={15} />}
        onClick={() => setOpen(true)}
        aria-expanded={open}
        aria-haspopup="dialog"
      >
        Việc cần xử lý
        {count > 0 && (
          <span className="ml-0.5 rounded-full bg-coral/15 px-1.5 text-micro tabular-nums text-coral">{count}</span>
        )}
      </Button>

      <Drawer
        open={open}
        onClose={() => setOpen(false)}
        width={420}
        title={
          <span className="inline-flex items-center gap-2">
            Việc cần xử lý
            {count > 0 && (
              <span className="rounded-full bg-coral/15 px-1.5 text-micro tabular-nums text-coral">{count}</span>
            )}
          </span>
        }
      >
        {isLoading && <p className="py-6 text-center text-ink-muted">Đang tải…</p>}
        {error && <ErrorState error={error} onRetry={refetch} />}
        {!isLoading && !error && items.length === 0 && (
          <p className="py-6 text-center text-ink-muted">Không có việc cần Chủ xử lý</p>
        )}
        {!isLoading && !error && items.length > 0 && (
          <ul className="-mx-4 -mt-1">
            {items.map((item, i) => {
              const meta = TYPE_META[item.type as OwnerInboxType] ?? { label: item.type, tone: 'neutral' as const };
              return (
                <li
                  key={`${item.type}-${item.refId ?? i}-${item.createdAt}`}
                  className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-4 py-2.5 first:border-t-0"
                >
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <Chip tone={meta.tone}>{meta.label}</Chip>
                      <Link
                        to={item.link}
                        className="font-medium text-primary hover:underline"
                        onClick={() => setOpen(false)}
                      >
                        {item.title}
                      </Link>
                    </div>
                    <div className="mt-0.5 text-small text-ink-faint">
                      {item.orderCode ? <span className="num">{item.orderCode}</span> : null}
                      {item.orderCode ? ' · ' : ''}
                      {dateTime(item.createdAt)}
                    </div>
                  </div>
                  <Link
                    to={item.link}
                    className="shrink-0 text-small text-primary hover:underline"
                    onClick={() => setOpen(false)}
                  >
                    Xem
                  </Link>
                </li>
              );
            })}
          </ul>
        )}
      </Drawer>
    </>
  );
}
