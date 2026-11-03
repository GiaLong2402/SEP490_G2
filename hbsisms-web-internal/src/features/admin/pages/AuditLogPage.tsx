import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { dateTime } from '@/shared/lib/format';
import { Card, DataTable, FilterBar, PageHeader, Pagination, SearchBox, useListParams, type Column } from '@/shared/ui';
import { adminApi } from '../api';
import type { AuditRow } from '../types';

export default function AuditLogPage() {
  const list = useListParams();
  const query = { q: list.q, page: list.page, pageSize: 20 };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['audit', query], queryFn: ({ signal }) => adminApi.audit(query, signal), placeholderData: keepPreviousData });
  const columns: Column<AuditRow>[] = [
    { key: 'at', header: 'Thời gian', cell: (a) => <span className="whitespace-nowrap">{dateTime(a.createdAt)}</span> },
    { key: 'user', header: 'Người thực hiện', cell: (a) => a.userName ?? 'Hệ thống' },
    { key: 'action', header: 'Hành động', cell: (a) => <span className="num">{a.action}</span> },
    { key: 'entity', header: 'Đối tượng', cell: (a) => <span className="num">{a.entityType ?? '—'}{a.entityId ? ` #${a.entityId}` : ''}</span> },
    { key: 'detail', header: 'Chi tiết', hideOnMobile: true, cell: (a) => (a.detail ? <code className="num break-all text-ink-muted">{JSON.stringify(a.detail)}</code> : '—') },
    { key: 'ip', header: 'IP', hideOnMobile: true, cell: (a) => <span className="num">{a.ipAddress ?? '—'}</span> },
  ];
  return (
    <>
      <PageHeader title="Nhật ký hệ thống" subtitle="Chỉ đọc" />
      <Card flush>
        <FilterBar><SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm người, hành động, đối tượng..." /></FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(a) => a.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có nhật ký" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
