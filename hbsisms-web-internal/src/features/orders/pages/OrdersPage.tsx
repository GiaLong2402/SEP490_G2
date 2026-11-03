import { Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { allocationStatusMeta, orderStateMeta, orderTypeLabels, paymentStatusMeta, saleChannelLabels } from '@/shared/domain/enums';
import { dateTime, money } from '@/shared/lib/format';
import { Card, Chip, DataTable, FilterBar, FilterSelect, LinkButton, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { useOrders } from '../hooks';
import type { OrderListItem } from '../types';

export default function OrdersPage() {
  const list = useListParams(['state', 'orderType', 'paymentStatus'] as const);
  const { data, isLoading, error, refetch } = useOrders({ q: list.q, page: list.page, state: list.filters.state, orderType: list.filters.orderType, paymentStatus: list.filters.paymentStatus });

  const columns: Column<OrderListItem>[] = [
    { key: 'code', header: 'Mã đơn', cell: (o) => <Link to={`/orders/${o.id}`} className="num whitespace-nowrap text-primary hover:underline">{o.code}</Link> },
    {
      key: 'customer', header: 'Khách hàng',
      cell: (o) => (
        <div>
          <div className="font-medium">{o.customerName}</div>
          {o.isOverLimit && !o.approvedBy && o.state !== 'cancelled' && <span className="text-micro text-coral">Vượt hạn mức, chờ duyệt</span>}
        </div>
      ),
    },
    { key: 'type', header: 'Loại', hideOnMobile: true, cell: (o) => orderTypeLabels[o.orderType] },
    { key: 'channel', header: 'Kênh', hideOnMobile: true, cell: (o) => (o.channel ? saleChannelLabels[o.channel] : '—') },
    { key: 'placed', header: 'Ngày đặt', hideOnMobile: true, cell: (o) => <span className="whitespace-nowrap">{dateTime(o.placedAt)}</span> },
    { key: 'total', header: 'Tổng tiền', align: 'right', cell: (o) => <span className="num whitespace-nowrap">{money(o.total)}</span> },
    { key: 'payment', header: 'Thanh toán', cell: (o) => <StatusChip meta={paymentStatusMeta} value={o.paymentStatus} /> },
    { key: 'alloc', header: 'Giữ hàng', hideOnMobile: true, cell: (o) => (o.state === 'cancelled' ? <Chip>—</Chip> : <StatusChip meta={allocationStatusMeta} value={o.allocationStatus} />) },
    { key: 'state', header: 'Trạng thái', cell: (o) => <StatusChip meta={orderStateMeta} value={o.state} /> },
  ];

  return (
    <>
      <PageHeader title="Đơn hàng" subtitle="Đơn bán lẻ, B2B và online" actions={<LinkButton to="/orders/new" variant="primary" icon={<Plus size={15} />}>Tạo đơn</LinkButton>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã đơn, tên khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.state} onChange={(v) => list.setFilter('state', v)}>{enumOptions(orderStateMeta)}</FilterSelect>
          <FilterSelect label="Loại đơn" value={list.filters.orderType} onChange={(v) => list.setFilter('orderType', v)}>{enumOptions(orderTypeLabels)}</FilterSelect>
          <FilterSelect label="Thanh toán" value={list.filters.paymentStatus} onChange={(v) => list.setFilter('paymentStatus', v)}>{enumOptions(paymentStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(o) => o.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có đơn hàng phù hợp" emptyAction={<LinkButton to="/orders/new">Tạo đơn đầu tiên</LinkButton>} />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
