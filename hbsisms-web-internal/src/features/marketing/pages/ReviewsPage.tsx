import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Star } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { reviewStatusMeta, type ReviewStatus } from '@/shared/domain/enums';
import { date } from '@/shared/lib/format';
import { Button, Card, DataTable, FilterBar, FilterSelect, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { marketingApi } from '../api';
import type { ReviewListItem } from '../types';

function Rating({ value }: { value: number }) {
  return (
    <span className="inline-flex items-center gap-1 whitespace-nowrap" aria-label={`${value} trên 5 sao`}>
      <Star size={13} className="fill-amber-fg text-amber-fg" aria-hidden />
      <span className="num">{value}/5</span>
    </span>
  );
}

export default function ReviewsPage() {
  const list = useListParams(['status', 'rating'] as const);
  const query = { q: list.q, page: list.page, status: list.filters.status, rating: list.filters.rating };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['reviews', query], queryFn: ({ signal }) => marketingApi.reviews(query, signal), placeholderData: keepPreviousData });
  const setStatus = useAction(({ id, status }: { id: number; status: ReviewStatus }) => marketingApi.setReviewStatus(id, status), { success: 'Đã cập nhật đánh giá' });

  const columns: Column<ReviewListItem>[] = [
    { key: 'product', header: 'Sản phẩm', cell: (r) => <span className="font-medium">{r.productName}</span> },
    { key: 'customer', header: 'Khách', hideOnMobile: true, cell: (r) => r.customerName ?? '—' },
    { key: 'rating', header: 'Điểm', cell: (r) => <Rating value={r.rating} /> },
    { key: 'body', header: 'Nội dung', cell: (r) => <div className="min-w-[240px]"><div className="font-medium">{r.title}</div><div className="text-ink-muted">{r.body}</div></div> },
    { key: 'at', header: 'Ngày', hideOnMobile: true, cell: (r) => date(r.createdAt) },
    { key: 'status', header: 'Trạng thái', cell: (r) => <StatusChip meta={reviewStatusMeta} value={r.status} /> },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) => (
        <div className="flex justify-end gap-1.5">
          {r.status !== 'rejected' && <Button variant="ghost" onClick={() => setStatus.mutate({ id: r.id, status: 'rejected' })}>Ẩn</Button>}
          {r.status !== 'approved' && <Button variant="primary" onClick={() => setStatus.mutate({ id: r.id, status: 'approved' })}>Duyệt</Button>}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Đánh giá sản phẩm" subtitle="Kiểm duyệt trước khi hiển thị trên cổng B2C" />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm sản phẩm, nội dung, khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(reviewStatusMeta)}</FilterSelect>
          <FilterSelect label="Điểm" value={list.filters.rating} onChange={(v) => list.setFilter('rating', v)}>{[5, 4, 3, 2, 1].map((n) => <option key={n} value={n}>{n} sao</option>)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có đánh giá phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
