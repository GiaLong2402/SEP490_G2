import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Check, X } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { approvalStatusMeta, approvalTypeLabels } from '@/shared/domain/enums';
import { dateTime } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, DataTable, FilterBar, FilterSelect, PageHeader, Pagination, SearchBox, StatusChip, Tabs, enumOptions, useListParams, type Column } from '@/shared/ui';
import { approvalsApi } from '../api';
import type { ApprovalDecision, ApprovalListItem } from '../types';

type Tab = 'pending' | 'history';

export default function ApprovalsPage() {
  const [tab, setTab] = useState<Tab>('pending');
  const list = useListParams(['approvalType'] as const);
  const query = { q: list.q, page: list.page, approvalType: list.filters.approvalType, status: tab === 'pending' ? 'pending' : undefined };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['approvals', query], queryFn: ({ signal }) => approvalsApi.list(query, signal), placeholderData: keepPreviousData });
  const [deciding, setDeciding] = useState<{ task: ApprovalListItem; decision: ApprovalDecision['decision'] } | null>(null);
  const decide = useAction((note: string) => approvalsApi.decide(deciding!.task.id, { decision: deciding!.decision, note }), {
    success: deciding?.decision === 'approved' ? 'Đã duyệt' : 'Đã từ chối',
    onDone: () => setDeciding(null),
  });

  const rows = tab === 'history' ? data?.items.filter((a) => a.status !== 'pending') : data?.items;
  const columns: Column<ApprovalListItem>[] = [
    { key: 'type', header: 'Loại', cell: (a) => <Chip tone={a.approvalType === 'over_limit_order' ? 'red' : 'neutral'}>{approvalTypeLabels[a.approvalType]}</Chip> },
    { key: 'title', header: 'Nội dung', cell: (a) => <div className="min-w-[240px]">{a.refLink ? <Link to={a.refLink} className="font-medium text-primary hover:underline">{a.title}</Link> : <span className="font-medium">{a.title}</span>}{a.note && <div className="text-ink-muted">Ghi chú: {a.note}</div>}</div> },
    { key: 'by', header: 'Người gửi', hideOnMobile: true, cell: (a) => a.requesterName ?? '—' },
    { key: 'at', header: 'Gửi lúc', hideOnMobile: true, cell: (a) => <span className="whitespace-nowrap">{dateTime(a.createdAt)}</span> },
    ...(tab === 'history'
      ? ([
          { key: 'status', header: 'Kết quả', cell: (a) => <StatusChip meta={approvalStatusMeta} value={a.status} /> },
          { key: 'decider', header: 'Người duyệt', hideOnMobile: true, cell: (a) => a.deciderName ?? '—' },
          { key: 'decided', header: 'Duyệt lúc', hideOnMobile: true, cell: (a) => <span className="whitespace-nowrap">{dateTime(a.decidedAt)}</span> },
        ] as Column<ApprovalListItem>[])
      : ([
          {
            key: 'actions', header: '', align: 'right',
            cell: (a) => (
              <div className="flex justify-end gap-1.5">
                <Button variant="danger" icon={<X size={14} />} onClick={() => setDeciding({ task: a, decision: 'rejected' })}>Từ chối</Button>
                <Button variant="primary" icon={<Check size={14} />} onClick={() => setDeciding({ task: a, decision: 'approved' })}>Duyệt</Button>
              </div>
            ),
          },
        ] as Column<ApprovalListItem>[])),
  ];

  return (
    <>
      <PageHeader title="Phê duyệt" subtitle="Đơn vượt hạn mức, báo hỏng, giá riêng và chênh lệch kiểm kê" />
      <Card flush>
        <Tabs label="Phê duyệt" value={tab} onChange={(next) => { setTab(next); list.setPage(1); }} tabs={[{ key: 'pending', label: 'Chờ duyệt' }, { key: 'history', label: 'Lịch sử' }]} />
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm nội dung..." />
          <FilterSelect label="Loại" value={list.filters.approvalType} onChange={(v) => list.setFilter('approvalType', v)}>{enumOptions(approvalTypeLabels)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={rows} rowKey={(a) => a.id} loading={isLoading} error={error} onRetry={refetch} emptyText={tab === 'pending' ? 'Không còn việc nào chờ duyệt' : 'Chưa có lịch sử duyệt'} />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <ConfirmDialog
        open={!!deciding}
        danger={deciding?.decision === 'rejected'}
        title={deciding?.decision === 'approved' ? 'Duyệt yêu cầu' : 'Từ chối yêu cầu'}
        message={deciding?.task.title}
        withNote
        noteRequired={deciding?.decision === 'rejected'}
        noteLabel={deciding?.decision === 'rejected' ? 'Lý do từ chối' : 'Ghi chú'}
        confirmLabel={deciding?.decision === 'approved' ? 'Duyệt' : 'Từ chối'}
        loading={decide.isPending}
        onConfirm={(note) => decide.mutate(note)}
        onClose={() => setDeciding(null)}
      />
    </>
  );
}
