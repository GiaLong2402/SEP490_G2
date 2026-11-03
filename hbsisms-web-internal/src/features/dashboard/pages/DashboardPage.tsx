import type { ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, ClipboardCheck, Clock, HandCoins, PackageMinus, Truck } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/features/auth';
import { deliveryStateMeta, orderStateMeta } from '@/shared/domain/enums';
import { money, moneyShort, num } from '@/shared/lib/format';
import { BarChart, Card, Chip, ErrorState, PageHeader, PageLoading, StatCard, StatusChip } from '@/shared/ui';
import { dashboardApi } from '../api';

function AttentionItem({ icon, title, detail, to, chip }: { icon: ReactNode; title: ReactNode; detail?: ReactNode; to: string; chip?: ReactNode }) {
  return (
    <li>
      <Link to={to} className="flex items-center gap-2.5 border-t border-line px-4 py-2.5 hover:bg-rowalt">
        <span className="shrink-0 text-ink-faint" aria-hidden>{icon}</span>
        <span className="min-w-0 flex-1">
          <span className="block truncate font-medium">{title}</span>
          {detail && <span className="block truncate text-small text-ink-muted">{detail}</span>}
        </span>
        {chip}
        <ChevronRight size={14} className="shrink-0 text-ink-faint" aria-hidden />
      </Link>
    </li>
  );
}

function greeting() {
  const hour = new Date().getHours();
  if (hour < 11) return 'Chào buổi sáng';
  if (hour < 14) return 'Chào buổi trưa';
  if (hour < 18) return 'Chào buổi chiều';
  return 'Chào buổi tối';
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['dashboard'], queryFn: dashboardApi.summary, refetchInterval: 60_000 });
  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;
  const { sales, debt, approvals, inventory, deliveries } = data;
  const change = sales && sales.revenueYesterday > 0 ? Math.round(((sales.revenueToday - sales.revenueYesterday) / sales.revenueYesterday) * 100) : null;

  const stats: ReactNode[] = [];
  if (sales) {
    stats.push(<StatCard key="revenue" label="Doanh thu hôm nay" value={moneyShort(sales.revenueToday)} hint={change === null ? 'Hôm qua chưa có doanh thu' : `${change >= 0 ? '+' : ''}${change}% so với hôm qua`} />);
    stats.push(<StatCard key="orders" label="Đơn cần xử lý" value={num(sales.ordersToProcess)} hint="Chờ xác nhận đến chờ giao" />);
  }
  if (debt) stats.push(<StatCard key="debt" label="Công nợ quá hạn" value={moneyShort(debt.overdue)} hint={`Tổng nợ ${moneyShort(debt.total)}`} tone={debt.overdue > 0 ? 'danger' : undefined} />);
  if (approvals) stats.push(<StatCard key="approvals" label="Việc chờ duyệt" value={num(approvals.pending)} tone={approvals.pending > 0 ? 'warning' : undefined} />);
  if (inventory) {
    stats.push(<StatCard key="stock" label="Khả dụng (khay)" value={num(inventory.available)} />);
    stats.push(<StatCard key="low" label="Quy cách tồn thấp" value={num(inventory.lowStock.length)} tone={inventory.lowStock.length ? 'danger' : undefined} />);
    stats.push(<StatCard key="expiry" label="Lô sắp hết hạn" value={num(inventory.nearExpiry.length)} tone={inventory.nearExpiry.length ? 'warning' : undefined} />);
  }
  if (deliveries) stats.push(<StatCard key="deliveries" label="Đợt giao hôm nay" value={num(deliveries.today)} />);

  const hasAttention = !!(approvals?.items.length || inventory?.nearExpiry.length || inventory?.lowStock.length || debt?.overdueCustomers.length || deliveries?.items.length);

  return (
    <>
      <PageHeader title={`${greeting()}, ${user?.fullName.split(' ').slice(-1)[0]}`} subtitle="Những việc cần chú ý hôm nay" />
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-4">{stats.slice(0, 8)}</div>
      <div className="grid grid-cols-1 items-start gap-4 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="min-w-0 space-y-4">
          {sales && (
            <>
              <Card title="Doanh thu 14 ngày qua">
                <BarChart title="Doanh thu 14 ngày qua" data={sales.daily.map((d) => ({ label: `${d.date.slice(8, 10)}/${d.date.slice(5, 7)}`, value: d.revenue, display: money(d.revenue) }))} />
              </Card>
              <Card title="Đơn hàng mới nhất" flush actions={<Link to="/orders" className="text-small text-primary hover:underline">Xem tất cả</Link>}>
                <ul>
                  {sales.recentOrders.map((o) => (
                    <li key={o.id} className="flex items-center gap-3 border-t border-line px-4 py-2 first:border-t-0">
                      <Link to={`/orders/${o.id}`} className="num shrink-0 text-primary hover:underline">{o.code}</Link>
                      <span className="min-w-0 flex-1 truncate">{o.customerName}</span>
                      <span className="num shrink-0">{money(o.total)}</span>
                      <span className="hidden w-[104px] shrink-0 text-right sm:block"><StatusChip meta={orderStateMeta} value={o.state} /></span>
                    </li>
                  ))}
                </ul>
              </Card>
            </>
          )}
          {!sales && inventory && (
            <Card title="Lô sắp hết hạn" flush actions={<Link to="/inventory?tab=expiry" className="text-small text-primary hover:underline">Xem tất cả</Link>}>
              {inventory.nearExpiry.length === 0 ? <p className="p-6 text-center text-ink-muted">Không có lô nào sắp hết hạn</p> : (
                <ul>
                  {inventory.nearExpiry.map((l) => (
                    <li key={l.lotCode} className="flex items-center gap-3 border-t border-line px-4 py-2 first:border-t-0">
                      <span className="num shrink-0">{l.lotCode}</span>
                      <span className="min-w-0 flex-1 truncate">{l.productName}</span>
                      <span className="num shrink-0">{num(l.qty)} khay</span>
                      <Chip tone={l.daysLeft <= 1 ? 'red' : 'amber'}>{l.daysLeft < 0 ? `Quá hạn ${-l.daysLeft} ngày` : `Còn ${l.daysLeft} ngày`}</Chip>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          )}
        </div>
        <Card title="Cần chú ý" flush>
          {!hasAttention && <p className="p-6 text-center text-ink-muted">Không có việc gì cần chú ý</p>}
          <ul className="-mt-px">
            {approvals?.items.map((a) => <AttentionItem key={`a${a.id}`} icon={<ClipboardCheck size={15} />} title={a.title} to="/approvals" chip={<Chip tone="amber">Chờ duyệt</Chip>} />)}
            {sales && inventory?.nearExpiry.map((l) => <AttentionItem key={`e${l.lotCode}`} icon={<Clock size={15} />} title={l.productName} detail={`${l.lotCode} · ${num(l.qty)} khay`} to="/inventory?tab=expiry" chip={<Chip tone={l.daysLeft <= 1 ? 'red' : 'amber'}>{l.daysLeft < 0 ? 'Quá hạn' : `Còn ${l.daysLeft} ngày`}</Chip>} />)}
            {inventory?.lowStock.map((s) => <AttentionItem key={`l${s.sku}`} icon={<PackageMinus size={15} />} title={s.productName} detail={`${s.sku} · còn ${num(s.available)} khay`} to="/inventory" chip={<Chip tone="red">Tồn thấp</Chip>} />)}
            {debt?.overdueCustomers.map((c) => <AttentionItem key={`d${c.customerId}`} icon={<HandCoins size={15} />} title={c.name} detail={`Quá hạn ${money(c.overdue)}`} to="/debt" chip={<Chip tone="red">Quá hạn</Chip>} />)}
            {deliveries?.items.map((d) => <AttentionItem key={`g${d.id}`} icon={<Truck size={15} />} title={d.customerName} detail={`${d.code} · ${d.timeWindow ?? ''}`} to="/deliveries" chip={<StatusChip meta={deliveryStateMeta} value={d.state} />} />)}
          </ul>
        </Card>
      </div>
    </>
  );
}
