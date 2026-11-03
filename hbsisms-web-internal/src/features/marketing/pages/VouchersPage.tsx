import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Pencil, Plus } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { voucherTypeLabels, type VoucherType } from '@/shared/domain/enums';
import { date, money, num } from '@/shared/lib/format';
import { Button, Card, Checkbox, Chip, DataTable, Field, FilterBar, IconButton, Input, Modal, PageHeader, Pagination, SearchBox, Select, enumOptions, useListParams, type Column } from '@/shared/ui';
import { marketingApi } from '../api';
import type { Voucher, VoucherInput } from '../types';

const empty = { code: '', name: '', voucherType: 'percent' as VoucherType, value: '', minOrder: '0', maxDiscount: '', startsAt: '', endsAt: '', usageLimit: '', perCustomerLimit: '', isActive: true };
const nullableNumber = (value: string) => (value === '' ? null : Number(value));

function voucherState(v: Voucher) {
  const now = new Date().toISOString();
  if (!v.isActive) return <Chip>Tắt</Chip>;
  if (v.endsAt && v.endsAt < now) return <Chip>Hết hạn</Chip>;
  if (v.startsAt && v.startsAt > now) return <Chip tone="blue">Sắp diễn ra</Chip>;
  if (v.usageLimit !== null && v.usedCount >= v.usageLimit) return <Chip tone="amber">Hết lượt</Chip>;
  return <Chip tone="green">Đang chạy</Chip>;
}

const describe = (v: Voucher) => (v.voucherType === 'percent' ? `${v.value}%${v.maxDiscount ? `, tối đa ${money(v.maxDiscount)}` : ''}` : money(v.value));

export default function VouchersPage() {
  const list = useListParams();
  const query = { q: list.q, page: list.page };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['vouchers', query], queryFn: ({ signal }) => marketingApi.vouchers(query, signal), placeholderData: keepPreviousData });
  const [editing, setEditing] = useState<Voucher | 'new' | null>(null);
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);

  const openForm = (voucher: Voucher | 'new') => {
    setTouched(false);
    setEditing(voucher);
    setForm(voucher === 'new' ? empty : {
      code: voucher.code, name: voucher.name ?? '', voucherType: voucher.voucherType, value: String(voucher.value), minOrder: String(voucher.minOrder), maxDiscount: voucher.maxDiscount?.toString() ?? '',
      startsAt: voucher.startsAt?.slice(0, 10) ?? '', endsAt: voucher.endsAt?.slice(0, 10) ?? '', usageLimit: voucher.usageLimit?.toString() ?? '', perCustomerLimit: voucher.perCustomerLimit?.toString() ?? '', isActive: voucher.isActive,
    });
  };
  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã' : undefined,
    value: !(Number(form.value) > 0) ? 'Phải lớn hơn 0' : form.voucherType === 'percent' && Number(form.value) > 100 ? 'Tối đa 100%' : undefined,
    endsAt: form.startsAt && form.endsAt && form.endsAt < form.startsAt ? 'Phải sau ngày bắt đầu' : undefined,
  };
  const body = (): VoucherInput => ({
    code: form.code.trim(), name: form.name.trim() || null, voucherType: form.voucherType, value: Number(form.value), minOrder: Number(form.minOrder) || 0, maxDiscount: nullableNumber(form.maxDiscount),
    startsAt: form.startsAt ? new Date(`${form.startsAt}T00:00:00`).toISOString() : null, endsAt: form.endsAt ? new Date(`${form.endsAt}T23:59:59`).toISOString() : null, usageLimit: nullableNumber(form.usageLimit), perCustomerLimit: nullableNumber(form.perCustomerLimit), isActive: form.isActive,
  });
  const save = useAction(() => (editing && editing !== 'new' ? marketingApi.updateVoucher(editing.id, body()) : marketingApi.createVoucher(body())), { success: 'Đã lưu voucher', onDone: () => setEditing(null) });

  const columns: Column<Voucher>[] = [
    { key: 'code', header: 'Mã', cell: (v) => <span className="num font-medium">{v.code}</span> },
    { key: 'name', header: 'Tên', cell: (v) => v.name ?? '—' },
    { key: 'type', header: 'Loại', hideOnMobile: true, cell: (v) => voucherTypeLabels[v.voucherType] },
    { key: 'value', header: 'Giá trị', cell: (v) => <span className="whitespace-nowrap">{describe(v)}</span> },
    { key: 'min', header: 'Đơn tối thiểu', align: 'right', hideOnMobile: true, cell: (v) => <span className="num">{money(v.minOrder)}</span> },
    { key: 'time', header: 'Thời gian', hideOnMobile: true, cell: (v) => <span className="whitespace-nowrap">{date(v.startsAt)} → {date(v.endsAt)}</span> },
    { key: 'used', header: 'Đã dùng', align: 'right', cell: (v) => <span className="num">{num(v.usedCount)}{v.usageLimit !== null ? ` / ${num(v.usageLimit)}` : ''}</span> },
    { key: 'state', header: 'Trạng thái', cell: voucherState },
    { key: 'edit', header: '', align: 'right', cell: (v) => <IconButton label={`Sửa ${v.code}`} onClick={() => openForm(v)}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader title="Voucher" subtitle="Khuyến mãi áp dụng trên cổng B2C" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => openForm('new')}>Thêm voucher</Button>} />
      <Card flush>
        <FilterBar><SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, tên voucher..." /></FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(v) => v.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có voucher" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <Modal
        open={!!editing}
        title={editing === 'new' ? 'Thêm voucher' : 'Sửa voucher'}
        width="lg"
        onClose={() => setEditing(null)}
        onSubmit={() => {
          setTouched(true);
          if (!errors.code && !errors.value && !errors.endsAt) save.mutate();
        }}
        footer={<><Button onClick={() => setEditing(null)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-3">
          <Field label="Mã voucher" required error={touched ? errors.code : undefined}><Input className="uppercase" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
          <Field label="Tên" className="sm:col-span-2"><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
          <Field label="Loại" required><Select value={form.voucherType} onChange={(e) => setForm({ ...form, voucherType: e.target.value as VoucherType })}>{enumOptions(voucherTypeLabels)}</Select></Field>
          <Field label={form.voucherType === 'percent' ? 'Giảm (%)' : 'Giá trị (₫)'} required error={touched ? errors.value : undefined}><Input type="number" min={0} value={form.value} onChange={(e) => setForm({ ...form, value: e.target.value })} /></Field>
          <Field label="Giảm tối đa (₫)"><Input type="number" min={0} disabled={form.voucherType !== 'percent'} value={form.maxDiscount} onChange={(e) => setForm({ ...form, maxDiscount: e.target.value })} /></Field>
          <Field label="Đơn tối thiểu (₫)"><Input type="number" min={0} value={form.minOrder} onChange={(e) => setForm({ ...form, minOrder: e.target.value })} /></Field>
          <Field label="Bắt đầu"><Input type="date" value={form.startsAt} onChange={(e) => setForm({ ...form, startsAt: e.target.value })} /></Field>
          <Field label="Kết thúc" error={touched ? errors.endsAt : undefined}><Input type="date" value={form.endsAt} onChange={(e) => setForm({ ...form, endsAt: e.target.value })} /></Field>
          <Field label="Tổng lượt dùng"><Input type="number" min={0} placeholder="Không giới hạn" value={form.usageLimit} onChange={(e) => setForm({ ...form, usageLimit: e.target.value })} /></Field>
          <Field label="Lượt / khách"><Input type="number" min={0} placeholder="Không giới hạn" value={form.perCustomerLimit} onChange={(e) => setForm({ ...form, perCustomerLimit: e.target.value })} /></Field>
          <div className="self-end pb-2"><Checkbox label="Đang bật" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} /></div>
        </div>
      </Modal>
    </>
  );
}
