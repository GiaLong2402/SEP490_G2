import type { ReactNode } from 'react';
import { ChevronRight, CircleCheck, ClipboardCheck, Factory, PackageOpen, PackagePlus, Tag, TriangleAlert, Warehouse } from 'lucide-react';
import { Link } from 'react-router-dom';
import { date, num } from '@/shared/lib/format';
import { Card, Chip } from '@/shared/ui';
import type { DashboardTasks, PendingTaskGroup } from '../types';

type GroupKey = PendingTaskGroup['key'];
type TaskItem = PendingTaskGroup['items'][number];

/** Tên ngắn trên ô + biểu tượng. Nhóm "Cần duyệt" trước, "Việc kho" sau. */
const groupMeta: Record<GroupKey, { short: string; icon: ReactNode; section: 'approve' | 'warehouse' }> = {
  damage_report: { short: 'Báo hỏng chờ duyệt', icon: <TriangleAlert size={16} />, section: 'approve' },
  customer_price: { short: 'Giá riêng B2B chờ duyệt', icon: <Tag size={16} />, section: 'approve' },
  other_approval: { short: 'Yêu cầu duyệt khác', icon: <ClipboardCheck size={16} />, section: 'approve' },
  production_approval: { short: 'Lệnh SX chờ duyệt', icon: <Factory size={16} />, section: 'approve' },
  raw_receipt: { short: 'Nhập NVL chờ nhập kho', icon: <PackagePlus size={16} />, section: 'warehouse' },
  material_issue: { short: 'Xuất NVL chờ xuất', icon: <PackageOpen size={16} />, section: 'warehouse' },
  finished_receipt: { short: 'Thành phẩm chờ cất kho', icon: <Warehouse size={16} />, section: 'warehouse' },
};

const sections: { key: 'approve' | 'warehouse'; title: string }[] = [
  { key: 'approve', title: 'Cần duyệt' },
  { key: 'warehouse', title: 'Việc kho' },
];

const DAY = 86_400_000;

function daysSince(iso: string | null) {
  if (!iso) return 0;
  return Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / DAY));
}

function daysLate(isoDate: string | null) {
  if (!isoDate) return 0;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return Math.round((today.getTime() - new Date(`${isoDate}T00:00:00`).getTime()) / DAY);
}

/** Vì sao việc này gấp — nói bằng số ngày cho dễ hiểu. */
function urgentReason(key: GroupKey, item: TaskItem) {
  if (key === 'raw_receipt' && item.dueDate) {
    const late = daysLate(item.dueDate);
    return `Trễ ${num(late)} ngày so với dự kiến (${date(item.dueDate)})`;
  }
  if ((key === 'production_approval' || key === 'material_issue') && item.dueDate) {
    const late = daysLate(item.dueDate);
    return late <= 0 ? 'Hạn hoàn thành là hôm nay' : `Quá hạn hoàn thành ${num(late)} ngày`;
  }
  const waited = daysSince(item.createdAt);
  return waited >= 1 ? `Đã chờ ${num(waited)} ngày` : 'Chờ quá 1 ngày';
}

function GroupTile({ group }: { group: PendingTaskGroup }) {
  const meta = groupMeta[group.key];
  const urgent = group.urgentCount > 0;
  return (
    <Link
      to={group.link}
      className={`flex min-w-0 flex-col rounded-md border bg-card p-3 transition-colors hover:bg-rowalt ${urgent ? 'border-red-fg/40 border-l-4 border-l-red-fg' : 'border-line'}`}
    >
      <div className="flex items-center justify-between gap-2">
        <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${urgent ? 'bg-red-bg text-red-fg' : 'bg-amber-bg text-amber-fg'}`} aria-hidden>
          {meta.icon}
        </span>
        <span className="num text-[26px] font-bold leading-none text-ink">{num(group.count)}</span>
      </div>
      <div className="mt-2 text-small font-medium leading-snug text-ink">{meta.short}</div>
      <div className="mt-1 text-micro leading-4">
        {urgent ? <span className="text-red-fg">{num(group.urgentCount)} việc gấp</span> : <span className="text-ink-faint">Không có việc gấp</span>}
      </div>
    </Link>
  );
}

/** Inter 4 dòng 10 — ô đếm theo loại việc + danh sách việc gấp cần làm ngay. */
export function PendingTasksCard({ pending }: { pending: NonNullable<DashboardTasks['pending']> }) {
  const urgentItems = pending.groups.flatMap((g) => g.items.filter((i) => i.urgent).map((i) => ({ group: g, item: i })));
  const hiddenUrgent = pending.urgent - urgentItems.length;

  return (
    <Card
      title="Việc chờ xử lý"
      actions={pending.total > 0 ? (
        <span className="flex items-center gap-1.5 text-small text-ink-muted">
          {num(pending.total)} việc
          {pending.urgent > 0 && <Chip tone="red">{num(pending.urgent)} gấp</Chip>}
        </span>
      ) : undefined}
    >
      {pending.groups.length === 0 ? (
        <div className="flex items-center justify-center gap-2 py-4 text-ink-muted">
          <CircleCheck size={18} className="text-green-fg" aria-hidden />
          Không còn việc nào đang chờ
        </div>
      ) : (
        <div className="space-y-4">
          {sections.map((s) => {
            const groups = pending.groups.filter((g) => groupMeta[g.key].section === s.key);
            if (groups.length === 0) return null;
            return (
              <div key={s.key}>
                <div className="mb-2 text-micro uppercase tracking-wide text-ink-faint">{s.title}</div>
                <div className="grid grid-cols-[repeat(auto-fill,minmax(160px,1fr))] gap-2">
                  {groups.map((g) => <GroupTile key={g.key} group={g} />)}
                </div>
              </div>
            );
          })}

          <div>
            <div className="mb-2 text-micro uppercase tracking-wide text-ink-faint">Cần làm ngay</div>
            {urgentItems.length === 0 ? (
              <p className="rounded-md bg-canvas px-3 py-2.5 text-small text-ink-muted">Không có việc gấp. Bấm vào từng ô ở trên để xem danh sách.</p>
            ) : (
              <ul className="overflow-hidden rounded-md border border-line">
                {urgentItems.map(({ group, item }, idx) => (
                  <li key={`${group.key}-${item.code ?? item.title}-${idx}`} className="border-t border-line first:border-t-0">
                    <Link to={item.link} className="flex items-center gap-3 px-3 py-2.5 hover:bg-rowalt">
                      <span className="h-2 w-2 shrink-0 rounded-full bg-red-fg" aria-hidden />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">
                          {item.code && <span className="num mr-1.5 text-primary">{item.code}</span>}
                          {item.title}
                        </span>
                        <span className="block truncate text-small leading-5 text-ink-muted">
                          {groupMeta[group.key].short} · <span className="text-red-fg">{urgentReason(group.key, item)}</span>
                        </span>
                      </span>
                      <ChevronRight size={14} className="shrink-0 text-ink-faint" aria-hidden />
                    </Link>
                  </li>
                ))}
                {hiddenUrgent > 0 && (
                  <li className="border-t border-line px-3 py-2 text-small text-ink-muted">
                    Còn {num(hiddenUrgent)} việc gấp khác. Bấm vào ô viền đỏ để xem hết.
                  </li>
                )}
              </ul>
            )}
          </div>
        </div>
      )}
    </Card>
  );
}
