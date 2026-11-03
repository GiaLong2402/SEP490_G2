import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { payDirectionLabels, paymentMethodLabels, payTxnStatusMeta } from '@/shared/domain/enums';
import { dateTime, money } from '@/shared/lib/format';
import { Card, Chip, DataTable, FilterBar, FilterSelect, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { paymentsApi } from '../api';
import type { PaymentListItem } from '../types';

export default function PaymentsPage() {
  const list = useListParams(['method', 'direction'] as const);
  const query = { q: list.q, page: list.page, method: list.filters.method, direction: list.filters.direction };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['payments', query], queryFn: ({ signal }) => paymentsApi.list(query, signal), placeholderData: keepPreviousData });

  const columns: Column<PaymentListItem>[] = [
    { key: 'code', header: 'Mã', cell: (p) => <span className="num">{p.code}</span> },
    { key: 'at', header: 'Thời gian', cell: (p) => <span className="whitespace-nowrap">{dateTime(p.paidAt)}</span> },
    { key: 'customer', header: 'Khách hàng', cell: (p) => <span className="font-medium">{p.customerName ?? '—'}</span> },
    { key: 'order', header: 'Đơn hàng', hideOnMobile: true, cell: (p) => (p.orderId ? <Link to={`/orders/${p.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{p.orderCode}</Link> : '—') },
    { key: 'method', header: 'Phương thức', cell: (p) => paymentMethodLabels[p.method] },
    { key: 'ref', header: 'Tham chiếu', hideOnMobile: true, cell: (p) => <span className="num">{p.reference ?? p.payosOrderCode ?? '—'}</span> },
    { key: 'direction', header: 'Loại', cell: (p) => <Chip tone={p.direction === 'in' ? 'green' : 'amber'}>{payDirectionLabels[p.direction]}</Chip> },
    { key: 'amount', header: 'Số tiền', align: 'right', cell: (p) => <span className="num font-medium">{p.direction === 'refund' ? '−' : ''}{money(p.amount)}</span> },
    { key: 'status', header: 'Trạng thái', cell: (p) => <StatusChip meta={payTxnStatusMeta} value={p.status} /> },
  ];

  return (
    <>
      <PageHeader title="Thanh toán" subtitle="Giao dịch thu và hoàn tiền; ghi nhận thu từ chi tiết đơn hoặc màn hình Công nợ" />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách, tham chiếu..." />
          <FilterSelect label="Phương thức" value={list.filters.method} onChange={(v) => list.setFilter('method', v)}>{enumOptions(paymentMethodLabels)}</FilterSelect>
          <FilterSelect label="Loại" value={list.filters.direction} onChange={(v) => list.setFilter('direction', v)}>{enumOptions(payDirectionLabels)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(p) => p.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có giao dịch" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
