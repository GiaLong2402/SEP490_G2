import { useState } from 'react';
import { FileText, Plus } from 'lucide-react';
import { useCustomerOptions } from '@/features/customers';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import { requestStatusMeta, saleChannelLabels, type SaleChannel } from '@/shared/domain/enums';
import { dateTime } from '@/shared/lib/format';
import { Button, Card, ConfirmDialog, DataTable, Field, FilterBar, FilterSelect, Input, LinkButton, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, Textarea, enumOptions, useListParams, type Column } from '@/shared/ui';
import { requestsApi } from '../api';
import { useRequests } from '../hooks';
import type { RequestListItem } from '../types';

export default function RequestsPage() {
  const list = useListParams(['status', 'channel'] as const);
  const { data, isLoading, error, refetch } = useRequests({ q: list.q, page: list.page, status: list.filters.status, channel: list.filters.channel });
  const customers = useCustomerOptions();
  const [creating, setCreating] = useState(false);
  const [touched, setTouched] = useState(false);
  const [closing, setClosing] = useState<ID | null>(null);
  const [form, setForm] = useState({ customerId: '', customerName: '', channel: 'phone' as SaleChannel, summary: '' });

  const create = useAction(() => requestsApi.create({ customerId: form.customerId ? Number(form.customerId) : null, customerName: form.customerName || null, channel: form.channel, summary: form.summary.trim() }), {
    success: (r) => `Đã tạo yêu cầu ${r.code}`,
    onDone: () => setCreating(false),
  });
  const close = useAction((note: string) => requestsApi.update(closing!, { status: 'closed', note }), { success: 'Đã đóng yêu cầu', onDone: () => setClosing(null) });

  const columns: Column<RequestListItem>[] = [
    { key: 'code', header: 'Mã', cell: (r) => <span className="num">{r.code}</span> },
    { key: 'customer', header: 'Khách', cell: (r) => <span className="font-medium">{r.customerName ?? '—'}</span> },
    { key: 'channel', header: 'Kênh', cell: (r) => saleChannelLabels[r.channel] },
    { key: 'summary', header: 'Nội dung', cell: (r) => <span className="line-clamp-2 min-w-[200px]">{r.summary}</span> },
    { key: 'at', header: 'Tiếp nhận', hideOnMobile: true, cell: (r) => <span className="whitespace-nowrap">{dateTime(r.receivedAt)}</span> },
    { key: 'handler', header: 'Phụ trách', hideOnMobile: true, cell: (r) => r.handlerName ?? <span className="text-ink-faint">Chưa có</span> },
    { key: 'status', header: 'Trạng thái', cell: (r) => <StatusChip meta={requestStatusMeta} value={r.status} /> },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) => (r.status === 'new' || r.status === 'quoting') && (
        <div className="flex justify-end gap-1.5">
          {r.customerId && <LinkButton to={`/quotes/new?requestId=${r.id}&customerId=${r.customerId}`} icon={<FileText size={14} />}>Báo giá</LinkButton>}
          <Button variant="ghost" onClick={() => setClosing(r.id)}>Đóng</Button>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Yêu cầu khách hàng" subtitle="Tiếp nhận từ điện thoại, Zalo, website và tại quầy" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => { setForm({ customerId: '', customerName: '', channel: 'phone', summary: '' }); setTouched(false); setCreating(true); }}>Ghi nhận yêu cầu</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, khách, nội dung..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(requestStatusMeta)}</FilterSelect>
          <FilterSelect label="Kênh" value={list.filters.channel} onChange={(v) => list.setFilter('channel', v)}>{enumOptions(saleChannelLabels)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có yêu cầu phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>

      <Modal
        open={creating}
        title="Ghi nhận yêu cầu"
        onClose={() => setCreating(false)}
        onSubmit={() => {
          setTouched(true);
          if (form.summary.trim()) create.mutate();
        }}
        footer={<><Button onClick={() => setCreating(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Lưu</Button></>}
      >
        <div className="space-y-3">
          <Field label="Khách hàng">
            <Select value={form.customerId} onChange={(e) => setForm({ ...form, customerId: e.target.value })}>
              <option value="">Khách vãng lai</option>
              {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
            </Select>
          </Field>
          {!form.customerId && <Field label="Tên khách vãng lai"><Input value={form.customerName} onChange={(e) => setForm({ ...form, customerName: e.target.value })} /></Field>}
          <Field label="Kênh" required><Select value={form.channel} onChange={(e) => setForm({ ...form, channel: e.target.value as SaleChannel })}>{enumOptions(saleChannelLabels)}</Select></Field>
          <Field label="Nội dung yêu cầu" required error={touched && !form.summary.trim() ? 'Vui lòng nhập nội dung' : undefined}><Textarea value={form.summary} onChange={(e) => setForm({ ...form, summary: e.target.value })} /></Field>
        </div>
      </Modal>

      <ConfirmDialog open={closing !== null} title="Đóng yêu cầu" withNote noteLabel="Lý do đóng" confirmLabel="Đóng yêu cầu" loading={close.isPending} onConfirm={(note) => close.mutate(note)} onClose={() => setClosing(null)} />
    </>
  );
}
