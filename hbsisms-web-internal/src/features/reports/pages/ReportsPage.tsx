import { useQuery } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { money, num } from '@/shared/lib/format';
import { BarChart, Card, ErrorState, PageHeader, PageLoading, RankBars, Select, StatCard, StatGrid } from '@/shared/ui';
import { reportsApi } from '../api';
import type { RankRow } from '../types';

const RANGES = [7, 14, 30, 60];
const toBars = (rows: RankRow[]) => rows.map((r) => ({ label: r.label, value: r.value, display: money(r.value) }));

function growthHint(pct: number | null) {
  if (pct == null) return 'Không so sánh được với kỳ trước';
  const sign = pct > 0 ? '+' : '';
  return `${sign}${pct}% so với kỳ trước`;
}

export default function ReportsPage() {
  const [params, setParams] = useSearchParams();
  const days = RANGES.includes(Number(params.get('days'))) ? Number(params.get('days')) : 30;
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['report-sales', days],
    queryFn: () => reportsApi.sales(days),
  });

  return (
    <>
      <PageHeader
        title="Báo cáo bán hàng"
        subtitle="Đơn hoàn tất (COMPLETED) — có % so với kỳ liền trước cùng độ dài"
        actions={
          <Select
            aria-label="Khoảng thời gian"
            className="!w-[150px]"
            value={days}
            onChange={(e) => setParams({ days: e.target.value })}
          >
            {RANGES.map((r) => (
              <option key={r} value={r}>
                {r} ngày qua
              </option>
            ))}
          </Select>
        }
      />
      {isLoading && <PageLoading />}
      {!!error && <ErrorState error={error} onRetry={refetch} />}
      {data && (
        <>
          <StatGrid>
            <StatCard label="Doanh thu" value={money(data.revenue)} hint={growthHint(data.revenueGrowthPct)} />
            <StatCard label="Lợi nhuận" value={money(data.profit)} hint={growthHint(data.profitGrowthPct)} />
            <StatCard label="Số đơn" value={num(data.orderCount)} hint={growthHint(data.orderCountGrowthPct)} />
            <StatCard label="Giá trị đơn trung bình" value={money(data.avgOrder)} />
          </StatGrid>
          <Card title="Doanh thu theo ngày" className="mb-4">
            <BarChart
              title="Doanh thu theo ngày"
              height={240}
              data={data.daily.map((p) => ({
                label: `${p.date.slice(8, 10)}/${p.date.slice(5, 7)}`,
                value: p.revenue,
                display: `${money(p.revenue)} · ${p.orders} đơn`,
              }))}
            />
          </Card>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card title="Theo sản phẩm">
              <RankBars data={toBars(data.topProducts)} />
            </Card>
            <Card title="Theo khách hàng">
              <RankBars data={toBars(data.topCustomers)} />
            </Card>
          </div>
        </>
      )}
    </>
  );
}
