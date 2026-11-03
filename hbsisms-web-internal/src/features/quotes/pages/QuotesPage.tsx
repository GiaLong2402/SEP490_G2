import { Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { quoteStatusMeta } from '@/shared/domain/enums';
import { date, money } from '@/shared/lib/format';
import { Card, DataTable, FilterBar, FilterSelect, LinkButton, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { useQuotes } from '../hooks';
import type { QuoteListItem } from '../types';

export default function QuotesPage() {
  const list = useListParams(['status'] as const);
  const { data, isLoading, error, refetch } = useQuotes({ q: list.q, page: list.page, status: list.filters.status });
  const columns: Column<QuoteListItem>[] = [
    { key: 'code', header: 'Mã', cell: (q) => <Link to={`/quotes/${q.id}`} className="num text-primary hover:underline">{q.code}</Link> },
    { key: 'customer', header: 'Khách hàng', cell: (q) => <span className="font-medium">{q.customerName}</span> },
    { key: 'created', header: 'Ngày lập', hideOnMobile: true, cell: (q) => date(q.createdAt) },
    { key: 'valid', header: 'Hiệu lực đến', cell: (q) => date(q.validUntil) },
    { key: 'creator', header: 'Người lập', hideOnMobile: true, cell: (q) => q.creatorName ?? '—' },
    { key: 'total', header: 'Tổng tiền', align: 'right', cell: (q) => <span className="num">{money(q.total)}</span> },
    { key: 'status', header: 'Trạng thái', cell: (q) => <StatusChip meta={quoteStatusMeta} value={q.status} /> },
    { key: 'order', header: 'Đơn hàng', hideOnMobile: true, cell: (q) => (q.convertedOrderId ? <Link to={`/orders/${q.convertedOrderId}`} className="text-primary hover:underline">Xem đơn</Link> : '—') },
  ];
  return (
    <>
      <PageHeader title="Báo giá" subtitle="Lập báo giá cho khách và chuyển thành đơn khi chốt" actions={<LinkButton to="/quotes/new" variant="primary" icon={<Plus size={15} />}>Lập báo giá</LinkButton>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, tên khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(quoteStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(q) => q.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có báo giá phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
