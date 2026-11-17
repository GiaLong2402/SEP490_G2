import { useEffect, useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import {
  allocationStatusMeta,
  fulfillmentLabels,
  orderListAllocationStatusMeta,
  orderListPaymentStatusMeta,
  orderListSaleChannelLabels,
  orderStateMeta,
  orderTypeLabels,
  paymentStatusMeta,
  saleChannelLabels,
} from '@/shared/domain/enums';
import { dateTime, money } from '@/shared/lib/format';
import {
  Button,
  Card,
  DataTable,
  FilterBar,
  FilterSelect,
  Input,
  LinkButton,
  PageHeader,
  Pagination,
  StatusChip,
  Tabs,
  enumOptions,
  useListParams,
  type Column,
} from '@/shared/ui';
import { OrderSearchField } from '../components/OrderSearchField';
import { useOrderCounts, useOrders } from '../hooks';
import type { OrderCounts, OrderListGroup, OrderListItem } from '../types';

type TabKey = 'all' | OrderListGroup;

const ORDER_TABS: { key: TabKey; label: string; countKey?: keyof OrderCounts }[] = [
  { key: 'all', label: 'Tất cả' },
  { key: 'awaiting_payment', label: 'Chờ thanh toán', countKey: 'awaitingPayment' },
  { key: 'awaiting_prepare', label: 'Chờ chuẩn bị', countKey: 'awaitingPrepare' },
  { key: 'preparing', label: 'Đang chuẩn bị', countKey: 'preparing' },
  { key: 'ready', label: 'Chờ giao / lấy', countKey: 'ready' },
  { key: 'delivering', label: 'Đang giao', countKey: 'delivering' },
  { key: 'delivery_failed', label: 'Giao thất bại', countKey: 'deliveryFailed' },
  { key: 'needs_refund', label: 'Cần hoàn tiền', countKey: 'needsRefund' },
  { key: 'completed', label: 'Hoàn tất', countKey: 'completed' },
  { key: 'cancelled', label: 'Đã hủy', countKey: 'cancelled' },
];

export default function OrdersPage() {
  const filterKeys = [
    'group',
    'state',
    'orderType',
    'paymentStatus',
    'channel',
    'allocationStatus',
    'fulfillmentMethod',
    'customerId',
    'placedFrom',
    'placedTo',
  ] as const;
  const list = useListParams(filterKeys);
  const [searchKey, setSearchKey] = useState(0);

  const activeTab: TabKey = (ORDER_TABS.some((t) => t.key === list.filters.group)
    ? list.filters.group
    : 'all') as TabKey;

  // Link/URL lệch khoảng ngày → bỏ "Đến" để tránh lọc trống sai.
  useEffect(() => {
    const { placedFrom, placedTo } = list.filters;
    if (placedFrom && placedTo && placedFrom > placedTo) {
      list.setFilter('placedTo', '');
    }
  }, [list.filters.placedFrom, list.filters.placedTo]);

  const sharedQuery = useMemo(
    () => ({
      q: list.q || undefined,
      orderType: list.filters.orderType || undefined,
      paymentStatus: list.filters.paymentStatus || undefined,
      channel: list.filters.channel || undefined,
      allocationStatus: list.filters.allocationStatus || undefined,
      fulfillmentMethod: list.filters.fulfillmentMethod || undefined,
      customerId: list.filters.customerId || undefined,
      placedFrom: list.filters.placedFrom || undefined,
      placedTo: list.filters.placedTo || undefined,
    }),
    [list.q, list.filters],
  );

  const listQuery = {
    ...sharedQuery,
    page: list.page,
    // Tab nhóm dùng group; filter "Trạng thái" chỉ khi đang ở Tất cả.
    group: activeTab === 'all' ? undefined : activeTab,
    state: activeTab === 'all' ? (list.filters.state || undefined) : undefined,
  };

  const { data, isLoading, isFetching, error, refetch } = useOrders(listQuery);
  const counts = useOrderCounts(sharedQuery);

  const hasFilters =
    !!list.q
    || filterKeys.some((key) => key !== 'group' && !!list.filters[key])
    || activeTab !== 'all';

  const clearFilters = () => {
    list.clearFilters();
    setSearchKey((k) => k + 1);
  };

  const setTab = (key: TabKey) => {
    if (key === 'all') {
      list.setFilters({ group: '', state: list.filters.state });
    } else {
      list.setFilters({ group: key, state: '' });
    }
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

  const tabItems = ORDER_TABS.map((tab) => {
    const n = tab.countKey && counts.data ? counts.data[tab.countKey] : undefined;
    return {
      key: tab.key,
      label: (
        <span className="inline-flex items-center gap-1.5">
          {tab.label}
          {tab.countKey != null && (
            <span className={`rounded-full px-1.5 text-micro tabular-nums ${activeTab === tab.key ? 'bg-primary/10 text-primary' : 'bg-thead text-ink-muted'}`}>
              {n ?? '—'}
            </span>
          )}
        </span>
      ),
    };
  });

  return (
    <>
      <PageHeader title="Đơn hàng" subtitle="Đơn bán lẻ, B2B và online" actions={<LinkButton to="/orders/new" variant="primary" icon={<Plus size={15} />}>Tạo đơn</LinkButton>} />
      <Card flush>
        <Tabs label="Nhóm đơn" tabs={tabItems} value={activeTab} onChange={setTab} />
        {activeTab !== 'all' && (
          <div className="border-b border-line bg-thead/40 px-3 py-1.5 text-small text-ink-muted">
            Đang xem:{' '}
            <span className="font-medium text-ink">
              {ORDER_TABS.find((t) => t.key === activeTab)?.label}
            </span>
            {data && (
              <>
                {' '}
                · <span className="tabular-nums">{data.total}</span> đơn
              </>
            )}
          </div>
        )}
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
          {activeTab === 'all' && (
            <FilterSelect label="Trạng thái" value={list.filters.state} onChange={(v) => list.setFilter('state', v)}>{enumOptions(orderStateMeta)}</FilterSelect>
          )}
          <FilterSelect label="Loại đơn" value={list.filters.orderType} onChange={(v) => list.setFilter('orderType', v)}>{enumOptions(orderTypeLabels)}</FilterSelect>
          <FilterSelect label="Thanh toán" value={list.filters.paymentStatus} onChange={(v) => list.setFilter('paymentStatus', v)}>{enumOptions(orderListPaymentStatusMeta)}</FilterSelect>
          <FilterSelect label="Kênh" value={list.filters.channel} onChange={(v) => list.setFilter('channel', v)}>{enumOptions(orderListSaleChannelLabels)}</FilterSelect>
          <FilterSelect label="Giữ hàng" value={list.filters.allocationStatus} onChange={(v) => list.setFilter('allocationStatus', v)}>{enumOptions(orderListAllocationStatusMeta)}</FilterSelect>
          <FilterSelect label="Cách nhận" value={list.filters.fulfillmentMethod} onChange={(v) => list.setFilter('fulfillmentMethod', v)}>{enumOptions(fulfillmentLabels)}</FilterSelect>
          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto" role="group" aria-label="Ngày đặt">
            <span className="text-small text-ink-muted">Ngày đặt</span>
            <label className="flex items-center gap-1.5 text-small text-ink-muted">
              <span>Từ</span>
              <Input
                aria-label="Từ ngày đặt"
                type="date"
                className="w-[150px]"
                value={list.filters.placedFrom}
                max={list.filters.placedTo || undefined}
                onChange={(e) => {
                  const value = e.target.value;
                  if (value && list.filters.placedTo && value > list.filters.placedTo) {
                    list.setFilters({ placedFrom: value, placedTo: '' });
                  } else {
                    list.setFilter('placedFrom', value);
                  }
                }}
              />
            </label>
            <label className="flex items-center gap-1.5 text-small text-ink-muted">
              <span>Đến</span>
              <Input
                aria-label="Đến ngày đặt"
                type="date"
                className="w-[150px]"
                value={list.filters.placedTo}
                min={list.filters.placedFrom || undefined}
                onChange={(e) => {
                  const value = e.target.value;
                  if (value && list.filters.placedFrom && value < list.filters.placedFrom) {
                    list.setFilters({ placedTo: value, placedFrom: '' });
                  } else {
                    list.setFilter('placedTo', value);
                  }
                }}
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
