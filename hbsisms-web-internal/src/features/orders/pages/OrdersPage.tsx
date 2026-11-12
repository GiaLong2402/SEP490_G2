import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { allocationStatusMeta, orderStateMeta, orderTypeLabels, paymentStatusMeta, saleChannelLabels } from '@/shared/domain/enums';
import { dateTime, money } from '@/shared/lib/format';
import { Button, Card, DataTable, FilterBar, FilterSelect, Input, LinkButton, PageHeader, Pagination, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { OrderSearchField } from '../components/OrderSearchField';
import { useOrders } from '../hooks';
import type { OrderListItem } from '../types';

export default function OrdersPage() {
  const filterKeys = [
    'state',
    'orderType',
    'paymentStatus',
    'channel',
    'allocationStatus',
    'customerId',
    'placedFrom',
    'placedTo',
  ] as const;
  const list = useListParams(filterKeys);
  const [searchKey, setSearchKey] = useState(0);
  const { data, isLoading, isFetching, error, refetch } = useOrders({
    q: list.q,
    page: list.page,
    state: list.filters.state || undefined,
    orderType: list.filters.orderType || undefined,
    paymentStatus: list.filters.paymentStatus || undefined,
    channel: list.filters.channel || undefined,
    allocationStatus: list.filters.allocationStatus || undefined,
    customerId: list.filters.customerId || undefined,
    placedFrom: list.filters.placedFrom || undefined,
    placedTo: list.filters.placedTo || undefined,
  });

  const hasFilters =
    !!list.q
    || filterKeys.some((key) => !!list.filters[key]);

  const clearFilters = () => {
    list.clearFilters();
    setSearchKey((k) => k + 1);
  };

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
    { key: 'alloc', header: 'Giữ hàng', hideOnMobile: true, cell: (o) => <StatusChip meta={allocationStatusMeta} value={o.allocationStatus} /> },
    { key: 'state', header: 'Trạng thái', cell: (o) => <StatusChip meta={orderStateMeta} value={o.state} /> },
  ];

  return (
    <>
      <PageHeader title="Đơn hàng" subtitle="Đơn bán lẻ, B2B và online" actions={<LinkButton to="/orders/new" variant="primary" icon={<Plus size={15} />}>Tạo đơn</LinkButton>} />
      <Card flush>
        <FilterBar
          actions={
            hasFilters ? (
              <Button type="button" variant="ghost" onClick={clearFilters}>
                Xóa bộ lọc
              </Button>
            ) : undefined
          }
        >
          <OrderSearchField
            key={searchKey}
            q={list.q}
            customerId={list.filters.customerId ? Number(list.filters.customerId) : null}
            onQChange={list.setQ}
            onCustomerChange={(id) => list.setFilter('customerId', id == null ? '' : String(id))}
            placeholder="Tìm mã đơn, tên, SĐT, mã khách…"
          />
          <FilterSelect label="Trạng thái" value={list.filters.state} onChange={(v) => list.setFilter('state', v)}>{enumOptions(orderStateMeta)}</FilterSelect>
          <FilterSelect label="Loại đơn" value={list.filters.orderType} onChange={(v) => list.setFilter('orderType', v)}>{enumOptions(orderTypeLabels)}</FilterSelect>
          <FilterSelect label="Thanh toán" value={list.filters.paymentStatus} onChange={(v) => list.setFilter('paymentStatus', v)}>{enumOptions(paymentStatusMeta)}</FilterSelect>
          <FilterSelect label="Kênh" value={list.filters.channel} onChange={(v) => list.setFilter('channel', v)}>{enumOptions(saleChannelLabels)}</FilterSelect>
          <FilterSelect label="Giữ hàng" value={list.filters.allocationStatus} onChange={(v) => list.setFilter('allocationStatus', v)}>{enumOptions(allocationStatusMeta)}</FilterSelect>
          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto" role="group" aria-label="Ngày đặt">
            <span className="text-small text-ink-muted">Ngày đặt</span>
            <label className="flex items-center gap-1.5 text-small text-ink-muted">
              <span>Từ</span>
              <Input
                aria-label="Từ ngày đặt"
                type="date"
                className="w-[150px]"
                value={list.filters.placedFrom}
                onChange={(e) => list.setFilter('placedFrom', e.target.value)}
              />
            </label>
            <label className="flex items-center gap-1.5 text-small text-ink-muted">
              <span>Đến</span>
              <Input
                aria-label="Đến ngày đặt"
                type="date"
                className="w-[150px]"
                value={list.filters.placedTo}
                onChange={(e) => list.setFilter('placedTo', e.target.value)}
              />
            </label>
          </div>
        </FilterBar>
        <DataTable
          columns={columns}
          rows={data?.items}
          rowKey={(o) => o.id}
          loading={isLoading || isFetching}
          error={error}
          onRetry={refetch}
          emptyText="Không có đơn hàng phù hợp"
          emptyAction={<LinkButton to="/orders/new">Tạo đơn đầu tiên</LinkButton>}
        />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
