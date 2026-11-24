import { ArrowDown, Plus } from 'lucide-react';
import { useQueries } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { productionOrderStatusMeta } from '@/shared/domain/enums';
import { date, dateTimeFull, num } from '@/shared/lib/format';
import { Card, DataTable, FilterBar, FilterSelect, InfoTip, LinkButton, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { StepDots } from '../components/StepTracker';
import { productionApi } from '../api';
import { useProductionOrders } from '../hooks';
import type { ProductionOrder } from '../types';

/** Người / thời điểm của thay đổi gần nhất theo trạng thái. */
function lastAction(o: ProductionOrder) {
  if (o.status === 'draft' || o.status === 'cancelled') return { verb: 'Tạo bởi', who: o.creatorName, at: o.createdAt };
  return { verb: 'Duyệt bởi', who: o.approverName, at: o.completedAt ?? o.approvedAt };
}

const STAT_STATUSES = [
  { status: 'draft', label: 'Chờ duyệt', dot: 'bg-ink-faint' },
  { status: 'approved', label: 'Chờ xuất nguyên liệu', dot: 'bg-blue-fg' },
  { status: 'in_progress', label: 'Đang sản xuất', dot: 'bg-primary' },
  { status: 'awaiting_receipt', label: 'Chờ nhập kho', dot: 'bg-amber-fg' },
  { status: 'completed', label: 'Hoàn thành', dot: 'bg-green-fg' },
] as const;

/** A · Danh sách lệnh sản xuất (Duy, dòng 48). */
export default function ProductionOrdersPage() {
  const list = useListParams(['status'] as const);
  const { data, isLoading, error, refetch } = useProductionOrders({ q: list.q, page: list.page, status: list.filters.status });
  /** Đếm theo trạng thái: mỗi ô một lần gọi list (pageSize 1) lấy `total`. */
  const counts = useQueries({
    queries: STAT_STATUSES.map((st) => ({
      queryKey: ['production-orders', 'count', st.status],
      queryFn: ({ signal }: { signal: AbortSignal }) => productionApi.list({ status: st.status, page: 1, pageSize: 1 }, signal),
    })),
  });

  const columns: Column<ProductionOrder>[] = [
    { key: 'code', header: <span className="inline-flex items-center gap-1">Mã lệnh <ArrowDown size={13} aria-label="mới nhất trước" /></span>, cell: (o) => <Link to={`/production-orders/${o.id}`} className="whitespace-nowrap font-mono text-primary hover:underline">{o.code}</Link> },
    { key: 'product', header: 'Sản phẩm', cell: (o) => <div className="min-w-[160px]"><div>{o.productName}</div><div className="text-small text-ink-faint">{o.productCode}</div></div> },
    { key: 'qty', header: 'Số lượng', align: 'right', cell: (o) => <span className="num whitespace-nowrap">{num(o.plannedQty)} khay</span> },
    { key: 'due', header: 'Hạn hoàn thành', hideOnMobile: true, cell: (o) => <span className="whitespace-nowrap">{date(o.plannedDueDate)}</span> },
    { key: 'step', header: 'Tiến trình', hideOnMobile: true, cell: (o) => <StepDots status={o.status} /> },
    {
      key: 'status', header: 'Trạng thái',
      cell: (o) => (
        <div className="flex flex-col items-start gap-1">
          <StatusChip meta={productionOrderStatusMeta} value={o.status} />
          {o.status === 'draft' && !o.allMaterialsSufficient && <span className="text-small text-red-fg">Thiếu nguyên liệu</span>}
        </div>
      ),
    },
    {
      key: 'at', header: 'Cập nhật', hideOnMobile: true,
      cell: (o) => {
        const a = lastAction(o);
        return (
          <div className="whitespace-nowrap">
            <div><span className="text-ink-muted">{a.verb}</span> {a.who ?? '—'}</div>
            <div className="text-small tabular-nums text-ink-faint">{dateTimeFull(a.at)}</div>
          </div>
        );
      },
    },
  ];

  return (
    <>
      <PageHeader
        title={
          <span className="inline-flex items-center gap-2">
            Lệnh sản xuất
            <InfoTip label="Ghi chú danh sách lệnh">
              <span className="block">Cột Tiến trình: 5 chấm theo 5 bước (xanh lá = xong, xanh = đang làm, xám = chưa tới). Bấm mã lệnh để mở đúng bước đang làm.</span>
              <span className="block">Lệnh chờ duyệt thiếu nguyên liệu hiện chữ đỏ ngay ở danh sách.</span>
            </InfoTip>
          </span>
        }
        subtitle="Lập, duyệt và theo dõi sản xuất từ nguyên liệu ra thành phẩm"
        actions={<LinkButton to="/production-orders/new" variant="primary" icon={<Plus size={15} />}>Tạo lệnh sản xuất</LinkButton>}
      />
      <div className="mb-4 grid grid-cols-2 gap-3 lg:grid-cols-5">
        {STAT_STATUSES.map((st, i) => (
          <Link key={st.status} to={`/production-orders?status=${st.status}`} className="block rounded-lg border border-line bg-card p-4 shadow-card transition hover:border-primary/40 hover:bg-rowalt">
            <div className="flex items-center gap-1.5 text-small text-ink-muted"><span className={`h-2 w-2 rounded-full ${st.dot}`} aria-hidden />{st.label}</div>
            <div className="mt-1.5 text-display tabular-nums text-ink">{counts[i].data ? num(counts[i].data.total) : '—'}</div>
          </Link>
        ))}
      </div>
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã lệnh, sản phẩm..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(productionOrderStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(o) => o.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có lệnh sản xuất" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
