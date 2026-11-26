import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { isOwnerOrAdmin, useAuth } from '@/features/auth';
import { orderStateMeta } from '@/shared/domain/enums';
import { money, moneyShort, num } from '@/shared/lib/format';
import { BarChart, Card, ErrorState, PageHeader, PageLoading, StatCard, StatusChip } from '@/shared/ui';
import { dashboardApi } from '../api';
import { PendingTasksCard } from '../components/PendingTasksCard';
import { DeliveriesTodayCard, OverdueDebtCard } from '../components/SideCards';
import { StockAlertsCard } from '../components/StockAlertsCard';

function greeting() {
  const hour = new Date().getHours();
  if (hour < 11) return 'Chào buổi sáng';
  if (hour < 14) return 'Chào buổi trưa';
  if (hour < 18) return 'Chào buổi chiều';
  return 'Chào buổi tối';
}

/** Tên gọi thân mật: tên người Việt = từ cuối; tránh cắt nhầm chức danh / role. */
function greetName(fullName: string | undefined, username: string | undefined, roleName: string | undefined) {
  const name = fullName?.trim();
  if (!name || /nhân viên|quản trị|chủ cửa|khách hàng/i.test(name) || (roleName && name === roleName)) {
    return username?.trim() || roleName || 'bạn';
  }
  const parts = name.split(/\s+/).filter(Boolean);
  return parts[parts.length - 1] ?? name;
}

/** % so với hôm qua — ẩn khi base quá nhỏ (tránh +44900%). */
function revenueChangePct(today: number, yesterday: number): number | null {
  if (yesterday < 10_000) return null;
  return Math.round(((today - yesterday) / yesterday) * 100);
}

const kpiCols: Record<number, string> = { 1: 'lg:grid-cols-1', 2: 'lg:grid-cols-2', 3: 'lg:grid-cols-3', 4: 'lg:grid-cols-4' };
const stripCols: Record<number, string> = { 1: 'sm:grid-cols-1', 2: 'sm:grid-cols-2', 3: 'sm:grid-cols-3', 4: 'sm:grid-cols-4' };

type MiniStat = { key: string; label: string; value: ReactNode; sub?: ReactNode; danger?: boolean };

/**
 * Tổng quan (Inter 4 dòng 8–10). Bố cục: 4 số chính → dải số phụ → [Việc chờ xử lý | Cảnh báo kho, giao hàng, nợ]
 * → [Doanh thu 14 ngày | Đơn mới nhất]. Mỗi khối chỉ hiện khi vai trò được xem (máy chủ trả null nếu không).
 */
export default function DashboardPage() {
  const { user } = useAuth();
  const elevated = isOwnerOrAdmin(user?.roleCode);
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.summary, refetchInterval: 60_000 });
  // Dòng 10: tồn thấp (TP + NVL) và việc chờ xử lý — lỗi thì trang vẫn hiện các khối còn lại.
  const tasks = useQuery({ queryKey: ['dashboard', 'tasks'], queryFn: dashboardApi.tasks, refetchInterval: 60_000 }).data;
  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;
  const { sales, debt, approvals, inventory, deliveries } = data;
  const pending = tasks?.pending ?? null;
  const lowStock = tasks?.lowStock ?? null;
  const change = sales ? revenueChangePct(sales.revenueToday, sales.revenueYesterday) : null;
  const showRevenue = elevated && !!sales;

  // ---- 4 số chính: thứ cần nhìn đầu tiên
  const kpis: ReactNode[] = [];
  const promoted = new Set<string>();
  if (showRevenue) {
    kpis.push(
      <StatCard
        key="revenue"
        label="Doanh thu hôm nay"
        value={moneyShort(sales.revenueToday)}
        hint={change === null ? (sales.revenueYesterday > 0 ? 'So với hôm qua (mốc nhỏ)' : 'Hôm qua chưa có doanh thu') : `${change >= 0 ? '+' : ''}${change}% so với hôm qua`}
        to="/reports"
      />,
    );
  }
  if (sales) {
    kpis.push(<StatCard key="orders" label="Đơn cần xử lý" value={num(sales.ordersToProcess)} hint="Chờ xác nhận đến chờ giao" tone={sales.ordersToProcess ? 'warning' : undefined} to="/orders" />);
  }
  if (pending) {
    kpis.push(<StatCard key="pending" label="Việc chờ xử lý" value={num(pending.total)} hint={pending.urgent > 0 ? `${num(pending.urgent)} việc gấp` : 'Không có việc gấp'} tone={pending.urgent > 0 ? 'danger' : pending.total > 0 ? 'warning' : undefined} />);
  } else if (approvals) {
    kpis.push(<StatCard key="pending" label="Việc chờ duyệt" value={num(approvals.pending)} tone={approvals.pending > 0 ? 'warning' : undefined} to="/approvals" />);
  }
  if (inventory) {
    const low = lowStock ? lowStock.finished.length + lowStock.raw.length : inventory.lowStock.length;
    const near = inventory.nearExpiryLots ?? inventory.nearExpiry.length;
    const expired = inventory.expiredLots ?? 0;
    const alerts = low + near + expired;
    kpis.push(
      <StatCard
        key="stock-alerts"
        label="Cảnh báo kho"
        value={num(alerts)}
        hint={`${num(low)} tồn thấp · ${num(near)} sắp hết hạn${expired > 0 ? ` · ${num(expired)} hết hạn` : ''}`}
        tone={expired > 0 || low > 0 ? 'danger' : near > 0 ? 'warning' : undefined}
      />,
    );
  }
  // Vai trò ít khối (bán hàng, thủ kho): đôn thêm số phụ lên cho đủ hàng.
  if (kpis.length < 3 && deliveries) {
    kpis.push(<StatCard key="deliveries" label="Đợt giao hôm nay" value={num(deliveries.today)} to="/deliveries" />);
    promoted.add('deliveries');
  }
  if (kpis.length < 3 && inventory) {
    kpis.push(<StatCard key="available" label="Khả dụng (khay)" value={num(inventory.available)} to="/inventory" />);
    promoted.add('available');
  }

  // ---- dải số phụ: để biết, không cần làm gì ngay
  const minis: MiniStat[] = [];
  if (inventory && !promoted.has('available')) minis.push({ key: 'available', label: 'Khả dụng', value: `${num(inventory.available)} khay` });
  if (elevated && inventory?.inventoryValue != null) minis.push({ key: 'value', label: 'Giá trị tồn', value: moneyShort(inventory.inventoryValue), sub: 'SL khả dụng × giá vốn lô' });
  if (debt) minis.push({ key: 'debt', label: 'Công nợ quá hạn', value: moneyShort(debt.overdue), sub: `Tổng nợ ${moneyShort(debt.total)}`, danger: debt.overdue > 0 });
  if (deliveries && !promoted.has('deliveries')) minis.push({ key: 'deliveries', label: 'Giao hôm nay', value: `${num(deliveries.today)} đợt` });

  const side: ReactNode[] = [];
  if (inventory) side.push(<StockAlertsCard key="stock" inventory={inventory} lowStock={lowStock} />);
  if (deliveries) side.push(<DeliveriesTodayCard key="deliveries" deliveries={deliveries} />);
  if (debt && debt.overdueCustomers.length > 0) side.push(<OverdueDebtCard key="debt" debt={debt} />);

  const bottom: ReactNode[] = [];
  if (showRevenue && sales.daily.length > 0) {
    bottom.push(
      <Card key="revenue" title="Doanh thu 14 ngày qua" actions={<Link to="/reports" className="text-small text-primary hover:underline">Xem báo cáo</Link>}>
        <BarChart title="Doanh thu 14 ngày qua" data={sales.daily.map((d) => ({ label: `${d.date.slice(8, 10)}/${d.date.slice(5, 7)}`, value: d.revenue, display: money(d.revenue) }))} />
      </Card>,
    );
  }
  if (sales) {
    bottom.push(
      <Card key="orders" title="Đơn hàng mới nhất" flush actions={<Link to="/orders" className="text-small text-primary hover:underline">Xem tất cả</Link>}>
        {sales.recentOrders.length === 0 ? <p className="p-6 text-center text-ink-muted">Chưa có đơn hàng</p> : (
          <ul>
            {sales.recentOrders.map((o) => (
              <li key={o.id} className="border-t border-line first:border-t-0">
                <Link to={`/orders/${o.id}`} className="flex items-center gap-3 px-4 py-2 hover:bg-rowalt">
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{o.customerName}</span>
                    <span className="num block truncate text-micro leading-4 text-primary">{o.code}</span>
                  </span>
                  <span className="shrink-0 text-right tabular-nums">{money(o.total)}</span>
                  {/* Cột trạng thái cố định, chip canh trái: các nhãn thẳng hàng, không tràn khung. */}
                  <span className="flex w-[112px] shrink-0 justify-start"><StatusChip meta={orderStateMeta} value={o.state} /></span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>,
    );
  }

  return (
    <>
      <PageHeader
        title={`${greeting()}, ${greetName(user?.fullName, user?.username, user?.roleName)}`}
        subtitle="Những việc cần chú ý hôm nay"
      />

      {kpis.length > 0 && <div className={`mb-3 grid grid-cols-2 gap-3 ${kpiCols[Math.min(4, kpis.length)]}`}>{kpis}</div>}

      {minis.length > 0 && (
        <div className={`mb-4 grid rounded-md border border-line bg-card shadow-card ${minis.length === 1 ? 'grid-cols-1' : 'grid-cols-2'} ${stripCols[Math.min(4, minis.length)]}`}>
          {minis.map((m, i) => (
            <div key={m.key} className={`min-w-0 px-4 py-3 ${i > 0 ? 'border-l border-line' : ''} ${i >= 2 ? 'max-sm:border-t' : ''} ${i === 2 ? 'max-sm:border-l-0' : ''}`}>
              <div className="text-small text-ink-muted">{m.label}</div>
              <div className={`mt-0.5 text-[17px] font-semibold leading-6 tabular-nums ${m.danger ? 'text-red-fg' : 'text-ink'}`}>{m.value}</div>
              {m.sub && <div className="text-micro text-ink-faint">{m.sub}</div>}
            </div>
          ))}
        </div>
      )}

      {(pending || side.length > 0) && (
        <div className={`mb-4 grid grid-cols-1 items-start gap-4 ${pending && side.length > 0 ? 'lg:grid-cols-[minmax(0,3fr)_minmax(360px,2fr)]' : ''}`}>
          {pending && <PendingTasksCard pending={pending} />}
          {side.length > 0 && <div className={`min-w-0 ${pending ? 'space-y-4' : 'grid grid-cols-1 items-start gap-4 lg:grid-cols-2'}`}>{side}</div>}
        </div>
      )}

      {bottom.length > 0 && (
        <div className={`grid grid-cols-1 items-start gap-4 ${bottom.length > 1 ? 'lg:grid-cols-2' : ''}`}>{bottom}</div>
      )}
    </>
  );
}
