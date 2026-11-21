import { ArrowDown, ArrowUp } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '@/features/auth';
import { deliveryStateMeta, fulfillmentMeta } from '@/shared/domain/enums';
import { date, dateTime, num, todayISO } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, FilterBar, FilterSelect, Input, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { DeliveryStateActions } from '../components/DeliveryStateActions';
import { useDeliveries } from '../hooks';
import type { DeliveryListItem } from '../types';

export default function DeliveriesPage() {
  const { user } = useAuth();
  const list = useListParams(['state', 'method', 'date', 'sort'] as const);
  const oldestFirst = list.filters.sort === 'asc';
  const { data, isLoading, error, refetch } = useDeliveries({ q: list.q, page: list.page, state: list.filters.state, fulfillmentMethod: list.filters.method, date: list.filters.date, sort: list.filters.sort });

  const columns: Column<DeliveryListItem>[] = [
    {
      key: 'code',
      header: (
        <button
          type="button"
          className="inline-flex items-center gap-1 uppercase tracking-wide hover:text-ink"
          onClick={() => list.setFilter('sort', oldestFirst ? '' : 'asc')}
          title={oldestFirst ? 'Đang xếp ngày giao cũ nhất trước — bấm để xếp mới nhất trước' : 'Đang xếp ngày giao mới nhất trước — bấm để xếp cũ nhất trước'}
          aria-label={oldestFirst ? 'Mã, cũ nhất trước. Bấm để xếp mới nhất trước' : 'Mã, mới nhất trước. Bấm để xếp cũ nhất trước'}
        >
          Mã {oldestFirst ? <ArrowUp size={13} /> : <ArrowDown size={13} />}
        </button>
      ),
      cell: (d) => <div><div className="num">{d.code}</div><div className="text-micro font-normal text-ink-faint">{d.legLabel}</div></div> },
    { key: 'order', header: 'Đơn hàng', cell: (d) => (user?.permissions.includes('orders') ? <Link to={`/orders/${d.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{d.orderCode}</Link> : <span className="num whitespace-nowrap">{d.orderCode}</span>) },
    { key: 'customer', header: 'Khách · địa chỉ', cell: (d) => <div className="min-w-[180px]"><div className="font-medium">{d.customerName}</div><div className="text-ink-muted">{d.addressText ?? 'Đến lấy tại kho'}</div></div> },
    { key: 'when', header: 'Lịch giao', cell: (d) => <div className="whitespace-nowrap">{date(d.scheduledDate)}<div className="text-ink-faint">{d.timeWindow}</div></div> },
    { key: 'distance', header: 'Khoảng cách', hideOnMobile: true, cell: (d) => (d.distanceKm !== null ? <Chip tone={d.inRadius ? 'green' : 'amber'}>{d.distanceKm} km</Chip> : '—') },
    { key: 'qty', header: 'Khay', align: 'right', cell: (d) => <span className="num">{num(d.totalKhay)}</span> },
    { key: 'method', header: 'Cách giao', cell: (d) => <StatusChip meta={fulfillmentMeta} value={d.fulfillmentMethod} /> },
    {
      key: 'state', header: 'Trạng thái',
      cell: (d) => (
        <div className="whitespace-nowrap">
          <StatusChip meta={deliveryStateMeta} value={d.state} />
          {d.state === 'going' && d.startedAt && <div className="mt-0.5 text-micro text-ink-faint">Bắt đầu {dateTime(d.startedAt)}</div>}
          {d.state === 'delivered' && d.deliveredAt && <div className="mt-0.5 text-micro text-ink-faint">Lúc {dateTime(d.deliveredAt)}</div>}
        </div>
      ),
    },
    { key: 'actions', header: '', align: 'right', cell: (d) => <DeliveryStateActions delivery={d} orderState={d.orderState} /> },
  ];

  return (
    <>
      <PageHeader title="Giao hàng" subtitle="Lịch giao và cập nhật trạng thái (một lần / đơn) · Shop tự giao / Khách đến lấy / Đối tác giao" />
      <Card flush>
        <FilterBar
          actions={
            <Button onClick={() => list.setFilter('date', list.filters.date === todayISO() ? '' : todayISO())} variant={list.filters.date === todayISO() ? 'primary' : 'secondary'}>Hôm nay</Button>
          }
        >
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.state} onChange={(v) => list.setFilter('state', v)}>{enumOptions(deliveryStateMeta)}</FilterSelect>
          <FilterSelect label="Cách giao" value={list.filters.method} onChange={(v) => list.setFilter('method', v)}>{enumOptions(fulfillmentMeta)}</FilterSelect>
          <Input aria-label="Ngày giao" type="date" className="!w-[150px]" value={list.filters.date} onChange={(e) => list.setFilter('date', e.target.value)} />
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(d) => d.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có giao hàng phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
