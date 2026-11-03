import { Link } from 'react-router-dom';
import { useAuth } from '@/features/auth';
import { useAction } from '@/shared/api/useAction';
import { deliveryStateMeta, fulfillmentLabels } from '@/shared/domain/enums';
import { date, num, todayISO } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, FilterBar, FilterSelect, Input, PageHeader, Pagination, SearchBox, Select, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { deliveriesApi } from '../api';
import { DeliveryStateActions } from '../components/DeliveryStateActions';
import { useCouriers, useDeliveries } from '../hooks';
import type { DeliveryListItem } from '../types';

export default function DeliveriesPage() {
  const { user } = useAuth();
  const list = useListParams(['state', 'date', 'assignedTo'] as const);
  const couriers = useCouriers();
  const { data, isLoading, error, refetch } = useDeliveries({ q: list.q, page: list.page, state: list.filters.state, date: list.filters.date, assignedTo: list.filters.assignedTo });
  const assign = useAction(({ id, assignedTo }: { id: number; assignedTo: number | null }) => deliveriesApi.update(id, { assignedTo }), { success: 'Đã phân công người giao' });
  const isCourier = user?.position === 'Giao hàng';

  const columns: Column<DeliveryListItem>[] = [
    { key: 'code', header: 'Mã', cell: (d) => <div><div className="num">{d.code}</div><div className="text-micro font-normal text-ink-faint">{d.legLabel}</div></div> },
    { key: 'order', header: 'Đơn hàng', cell: (d) => (user?.permissions.includes('orders') ? <Link to={`/orders/${d.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{d.orderCode}</Link> : <span className="num whitespace-nowrap">{d.orderCode}</span>) },
    { key: 'customer', header: 'Khách · địa chỉ', cell: (d) => <div className="min-w-[180px]"><div className="font-medium">{d.customerName}</div><div className="text-ink-muted">{d.addressText ?? 'Đến lấy tại kho'}</div></div> },
    { key: 'when', header: 'Lịch giao', cell: (d) => <div className="whitespace-nowrap">{date(d.scheduledDate)}<div className="text-ink-faint">{d.timeWindow}</div></div> },
    { key: 'distance', header: 'Khoảng cách', hideOnMobile: true, cell: (d) => (d.distanceKm !== null ? <Chip tone={d.inRadius ? 'green' : 'amber'}>{d.distanceKm} km</Chip> : '—') },
    { key: 'qty', header: 'Khay', align: 'right', cell: (d) => <span className="num">{num(d.totalKhay)}</span> },
    {
      key: 'assignee', header: 'Người giao',
      cell: (d) =>
        d.fulfillmentMethod === 'shop_delivery' && !isCourier && d.state !== 'delivered' && d.state !== 'failed' ? (
          <Select aria-label={`Người giao ${d.code}`} className="!h-7 !w-[140px] text-small" value={d.assignedTo ?? ''} onChange={(e) => assign.mutate({ id: d.id, assignedTo: e.target.value ? Number(e.target.value) : null })}>
            <option value="">Chưa phân công</option>
            {couriers.data?.map((c) => <option key={c.id} value={c.id}>{c.fullName}</option>)}
          </Select>
        ) : (
          d.assigneeName ?? d.carrier ?? fulfillmentLabels[d.fulfillmentMethod]
        ),
    },
    { key: 'state', header: 'Trạng thái', cell: (d) => <StatusChip meta={deliveryStateMeta} value={d.state} /> },
    { key: 'actions', header: '', align: 'right', cell: (d) => <DeliveryStateActions delivery={d} /> },
  ];

  return (
    <>
      <PageHeader title="Giao hàng" subtitle="Lịch giao, phân công và cập nhật trạng thái từng đợt" />
      <Card flush>
        <FilterBar
          actions={
            <>
              <Button onClick={() => list.setFilter('date', list.filters.date === todayISO() ? '' : todayISO())} variant={list.filters.date === todayISO() ? 'primary' : 'secondary'}>Hôm nay</Button>
              {isCourier && user && <Button onClick={() => list.setFilter('assignedTo', list.filters.assignedTo ? '' : String(user.id))} variant={list.filters.assignedTo ? 'primary' : 'secondary'}>Của tôi</Button>}
            </>
          }
        >
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.state} onChange={(v) => list.setFilter('state', v)}>{enumOptions(deliveryStateMeta)}</FilterSelect>
          <Input aria-label="Ngày giao" type="date" className="!w-[150px]" value={list.filters.date} onChange={(e) => list.setFilter('date', e.target.value)} />
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(d) => d.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có đợt giao phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
