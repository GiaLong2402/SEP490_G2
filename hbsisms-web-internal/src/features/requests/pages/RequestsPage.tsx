import { useEffect, useState } from 'react';
import { CheckCircle2, FileText, Plus } from 'lucide-react';
import { CustomerSearchField } from '@/features/customers';
import { useAction } from '@/shared/api/useAction';
import { requestStatusMeta, saleChannelLabels, staffSaleChannelLabels, type SaleChannel } from '@/shared/domain/enums';
import { date, dateTime } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, Field, FilterBar, FilterSelect, Input, LinkButton, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, Textarea, enumOptions, useListParams, type Column } from '@/shared/ui';
import { requestsApi } from '../api';
import { useRequest, useRequests } from '../hooks';
import type { RequestListItem } from '../types';

const emptyForm = {
  walkIn: true,
  customerId: null as number | null,
  customerName: '',
  contactPhone: '',
  contactAddress: '',
  channel: 'phone' as SaleChannel,
  summary: '',
};

export default function RequestsPage() {
  const list = useListParams(['status', 'channel'] as const);
  const { data, isLoading, error, refetch } = useRequests({ q: list.q, page: list.page, status: list.filters.status, channel: list.filters.channel });
  const [creating, setCreating] = useState(false);
  const [touched, setTouched] = useState(false);
  const [feasTouched, setFeasTouched] = useState(false);
  const [viewingReject, setViewingReject] = useState<RequestListItem | null>(null);
  const [feasibilityId, setFeasibilityId] = useState<number | null>(null);
  const [feasForm, setFeasForm] = useState({ feasible: true, expectedDate: '', note: '' });
  const [form, setForm] = useState(emptyForm);

  const feasibilityDetail = useRequest(feasibilityId);
  const feasibility = feasibilityDetail.data ?? data?.items.find((r) => r.id === feasibilityId) ?? null;

  useEffect(() => {
    if (!feasibilityDetail.data) return;
    const r = feasibilityDetail.data;
    setFeasForm({
      feasible: r.feasible ?? true,
      expectedDate: r.expectedDate ?? '',
      note: r.rejectReason ?? r.note ?? '',
    });
  }, [feasibilityDetail.data]);

  const walkInOk = form.customerName.trim().length > 0;
  const systemOk = form.customerId != null;
  const customerOk = form.walkIn ? walkInOk : systemOk;
  const summaryOk = form.summary.trim().length > 0;
  const rejectReasonOk = feasForm.note.trim().length > 0;

  const create = useAction(
    () =>
      requestsApi.create({
        customerId: form.walkIn ? null : form.customerId,
        customerName: form.walkIn ? form.customerName.trim() || 'Khách vãng lai' : null,
        contactPhone: form.walkIn ? form.contactPhone.trim() || null : null,
        contactAddress: form.walkIn ? form.contactAddress.trim() || null : null,
        channel: form.channel,
        summary: form.summary.trim(),
      }),
    {
      success: (r) => `Đã tạo yêu cầu ${r.code}`,
      onDone: () => setCreating(false),
    },
  );

  const saveFeasibility = useAction(
    () => {
      if (!feasForm.feasible) {
        return requestsApi.reject(feasibilityId!, { rejectReason: feasForm.note.trim() });
      }
      return requestsApi.feasibility(feasibilityId!, {
        feasible: true,
        expectedDate: feasForm.expectedDate || null,
        note: feasForm.note || null,
        customerAccepted: false,
      });
    },
    {
      success: (r) =>
        'request' in r ? 'Đã ghi khả năng đáp ứng' : `Đã từ chối ${r.code}`,
      onDone: () => setFeasibilityId(null),
    },
  );

  function submitFeasibility() {
    setFeasTouched(true);
    if (!feasForm.feasible && !rejectReasonOk) return;
    saveFeasibility.mutate();
  }

  const columns: Column<RequestListItem>[] = [
    { key: 'code', header: 'Mã', cell: (r) => <span className="num">{r.code}</span> },
    {
      key: 'customer',
      header: 'Khách',
      cell: (r) => (
        <div className="min-w-0">
          <div className="font-medium">{r.customerName ?? '—'}</div>
          {r.contactPhone && <div className="text-small text-ink-muted">{r.contactPhone}</div>}
        </div>
      ),
    },
    { key: 'channel', header: 'Kênh', cell: (r) => saleChannelLabels[r.channel] },
    { key: 'summary', header: 'Nội dung', cell: (r) => <span className="line-clamp-2 min-w-[200px]">{r.summary}</span> },
    {
      key: 'feasible',
      header: 'Đáp ứng',
      hideOnMobile: true,
      cell: (r) =>
        r.status === 'rejected' ? (
          <span className="text-red-700">Không đáp ứng</span>
        ) : r.feasible == null ? (
          <span className="text-ink-faint">Chưa đánh giá</span>
        ) : r.feasible ? (
          <span className="text-green-700">Có</span>
        ) : (
          <span className="text-red-700">Không</span>
        ),
    },
    { key: 'at', header: 'Tiếp nhận', hideOnMobile: true, cell: (r) => <span className="whitespace-nowrap">{dateTime(r.receivedAt)}</span> },
    {
      key: 'neededDate',
      header: 'Ngày cần giao',
      hideOnMobile: true,
      cell: (r) =>
        r.neededDate ? (
          <span className="whitespace-nowrap">{date(r.neededDate)}</span>
        ) : (
          <span className="text-ink-faint">—</span>
        ),
    },
    { key: 'handler', header: 'Phụ trách', hideOnMobile: true, cell: (r) => r.handlerName ?? <span className="text-ink-faint">Chưa có</span> },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (r) => (
        <div className="flex flex-wrap items-center gap-1.5">
          <StatusChip meta={requestStatusMeta} value={r.status} />
          {r.status === 'rejected' && r.rejectReason && (
            <Button variant="ghost" onClick={() => setViewingReject(r)}>Xem lý do</Button>
          )}
        </div>
      ),
    },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) => (r.status === 'new' || r.status === 'quoting') && (
        <div className="flex justify-end gap-1.5">
          <Button variant="ghost" icon={<CheckCircle2 size={14} />} onClick={() => {
            setFeasibilityId(r.id);
            setFeasTouched(false);
          }}>Khả năng đáp ứng</Button>
          <LinkButton
            to={
              r.customerId
                ? `/quotes/new?requestId=${r.id}&customerId=${r.customerId}`
                : `/quotes/new?requestId=${r.id}`
            }
            icon={<FileText size={14} />}
          >
            Báo giá
          </LinkButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Yêu cầu khách hàng" subtitle="Tiếp nhận từ điện thoại, Zalo, website và tại quầy" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => { setForm(emptyForm); setTouched(false); setCreating(true); }}>Ghi nhận yêu cầu</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, khách, nội dung..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(requestStatusMeta)}</FilterSelect>
          <FilterSelect label="Kênh" value={list.filters.channel} onChange={(v) => list.setFilter('channel', v)}>{enumOptions(staffSaleChannelLabels)}</FilterSelect>
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
          if (customerOk && summaryOk) create.mutate();
        }}
        footer={<><Button onClick={() => setCreating(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Lưu</Button></>}
      >
        <div className="space-y-3">
          <div className="flex flex-wrap gap-2">
            <Chip tone={form.walkIn ? 'blue' : 'neutral'}>
              <button
                type="button"
                className="bg-transparent"
                onClick={() => setForm({ ...form, walkIn: true, customerId: null })}
              >
                Khách vãng lai
              </button>
            </Chip>
            <Chip tone={!form.walkIn ? 'blue' : 'neutral'}>
              <button
                type="button"
                className="bg-transparent"
                onClick={() => setForm({ ...form, walkIn: false, customerName: '', contactPhone: '', contactAddress: '' })}
              >
                Khách trên hệ thống
              </button>
            </Chip>
          </div>

          {form.walkIn ? (
            <>
              <Field label="Tên khách" required error={touched && !walkInOk ? 'Nhập tên khách vãng lai' : undefined}>
                <Input
                  value={form.customerName}
                  onChange={(e) => setForm({ ...form, customerName: e.target.value })}
                  placeholder="VD: Anh Minh"
                />
              </Field>
              <Field label="Số ĐT">
                <Input
                  value={form.contactPhone}
                  onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
                  placeholder="09…"
                  inputMode="tel"
                />
              </Field>
              <Field label="Địa chỉ">
                <Input
                  value={form.contactAddress}
                  onChange={(e) => setForm({ ...form, contactAddress: e.target.value })}
                  placeholder="Số nhà, đường, phường/xã…"
                />
              </Field>
            </>
          ) : (
            <Field label="Tìm khách hàng" required error={touched && !systemOk ? 'Chọn khách trên hệ thống' : undefined}>
              <CustomerSearchField
                value={form.customerId}
                onChange={(customerId) => setForm({ ...form, customerId })}
                placeholder="Tìm theo SĐT, tên hoặc mã KH…"
              />
            </Field>
          )}

          <Field label="Kênh" required>
            <Select value={form.channel} onChange={(e) => setForm({ ...form, channel: e.target.value as SaleChannel })}>
              {enumOptions(staffSaleChannelLabels)}
            </Select>
          </Field>
          <Field label="Nội dung yêu cầu" required error={touched && !summaryOk ? 'Vui lòng nhập nội dung' : undefined}>
            <Textarea
              value={form.summary}
              onChange={(e) => setForm({ ...form, summary: e.target.value })}
              placeholder="Ví dụ: số lượng, sản phẩm, ngày nhận…"
            />
          </Field>
        </div>
      </Modal>

      <Modal
        open={feasibilityId !== null}
        title={feasibility ? `Khả năng đáp ứng · ${feasibility.code}` : 'Khả năng đáp ứng'}
        onClose={() => setFeasibilityId(null)}
        onSubmit={submitFeasibility}
        footer={
          <>
            <Button onClick={() => setFeasibilityId(null)}>Hủy</Button>
            <Button type="submit" variant="primary" loading={saveFeasibility.isPending || feasibilityDetail.isLoading}>
              {feasForm.feasible ? 'Lưu kết quả' : 'Từ chối yêu cầu'}
            </Button>
          </>
        }
      >
        <div className="space-y-3">
          {feasibilityDetail.isLoading && <p className="text-small text-ink-muted">Đang tải chi tiết yêu cầu…</p>}
          {feasibility?.summary && (
            <p className="rounded border border-line bg-surface-muted px-3 py-2 text-small text-ink-muted whitespace-pre-wrap">
              {feasibility.summary}
            </p>
          )}
          <Field label="Đáp ứng được?" required>
            <Select
              value={feasForm.feasible ? 'yes' : 'no'}
              onChange={(e) =>
                setFeasForm({
                  ...feasForm,
                  feasible: e.target.value === 'yes',
                })
              }
            >
              <option value="yes">Có</option>
              <option value="no">Không</option>
            </Select>
          </Field>
          {feasForm.feasible ? (
            <>
              <Field label="Ngày dự kiến">
                <Input
                  type="date"
                  value={feasForm.expectedDate}
                  onChange={(e) => setFeasForm({ ...feasForm, expectedDate: e.target.value })}
                />
              </Field>
              <Field label="Ghi chú">
                <Textarea value={feasForm.note} onChange={(e) => setFeasForm({ ...feasForm, note: e.target.value })} />
              </Field>
              <p className="text-sm text-ink-muted">
                Chọn sản phẩm, số lượng và giá khi lập báo giá gửi khách.
              </p>
            </>
          ) : (
            <>
              <Field
                label="Lý do từ chối"
                required
                error={feasTouched && !rejectReasonOk ? 'Vui lòng nhập lý do từ chối' : undefined}
              >
                <Textarea
                  value={feasForm.note}
                  onChange={(e) => setFeasForm({ ...feasForm, note: e.target.value })}
                  placeholder="VD: Hết hàng lâu / Không sản xuất kịp…"
                />
              </Field>
              <p className="text-sm text-ink-muted">
                Yêu cầu sẽ chuyển sang Từ chối. Khách thấy trạng thái và lý do trên portal.
              </p>
            </>
          )}
        </div>
      </Modal>

      <Modal
        open={viewingReject !== null}
        title={viewingReject ? `Lý do từ chối · ${viewingReject.code}` : 'Lý do từ chối'}
        onClose={() => setViewingReject(null)}
        footer={<Button variant="primary" onClick={() => setViewingReject(null)}>Đóng</Button>}
      >
        <p className="whitespace-pre-wrap text-body text-ink">{viewingReject?.rejectReason?.trim() || '—'}</p>
      </Modal>
    </>
  );
}
