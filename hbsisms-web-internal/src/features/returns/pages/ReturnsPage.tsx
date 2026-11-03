import { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { returnStatusMeta } from '@/shared/domain/enums';
import { date, money } from '@/shared/lib/format';
import { Button, Card, DataTable, Field, FilterBar, FilterSelect, Input, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { returnsApi } from '../api';
import { useReturnableOrders, useReturns } from '../hooks';
import type { ReturnListItem, ReturnStatus } from '../types';

export default function ReturnsPage() {
  const list = useListParams(['status'] as const);
  const [params] = useSearchParams();
  const presetOrder = params.get('orderId');
  const { data, isLoading, error, refetch } = useReturns({ q: list.q, page: list.page, status: list.filters.status });
  const [open, setOpen] = useState(!!presetOrder);
  const orders = useReturnableOrders(open);
  const [orderId, setOrderId] = useState(presetOrder ?? '');
  const [reason, setReason] = useState('');
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [touched, setTouched] = useState(false);

  const selected = orders.data?.find((o) => o.id === Number(orderId));
  useEffect(() => setQuantities({}), [orderId]);

  const items = selected?.lines.map((l) => ({ variantId: l.variantId, qty: Number(quantities[l.variantId] ?? 0) })) ?? [];
  const qtyValid = items.some((i) => i.qty > 0) && (selected?.lines.every((l) => {
    const qty = Number(quantities[l.variantId] ?? 0);
    return Number.isInteger(qty) && qty >= 0 && qty <= l.maxQty;
  }) ?? false);
  const amount = selected?.lines.reduce((sum, l) => sum + Number(quantities[l.variantId] ?? 0) * l.unitPrice, 0) ?? 0;

  const create = useAction(() => returnsApi.create({ orderId: Number(orderId), reason: reason.trim(), items }), { success: (r) => `Đã tạo phiếu ${r.code}`, onDone: () => setOpen(false) });
  const setStatus = useAction(({ id, status }: { id: number; status: ReturnStatus }) => returnsApi.setStatus(id, status), { success: 'Đã cập nhật phiếu trả' });

  const columns: Column<ReturnListItem>[] = [
    { key: 'code', header: 'Mã', cell: (r) => <span className="num">{r.code}</span> },
    { key: 'order', header: 'Đơn hàng', cell: (r) => (r.orderId ? <Link to={`/orders/${r.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{r.orderCode}</Link> : '—') },
    { key: 'customer', header: 'Khách hàng', cell: (r) => <span className="font-medium">{r.customerName ?? '—'}</span> },
    { key: 'reason', header: 'Lý do', hideOnMobile: true, cell: (r) => r.reason ?? '—' },
    { key: 'items', header: 'Hàng trả', hideOnMobile: true, cell: (r) => r.items.map((i) => `${i.qty} × ${i.sku}`).join(', ') },
    { key: 'at', header: 'Ngày tạo', hideOnMobile: true, cell: (r) => date(r.createdAt) },
    { key: 'amount', header: 'Giá trị', align: 'right', cell: (r) => <span className="num">{money(r.amount)}</span> },
    { key: 'status', header: 'Trạng thái', cell: (r) => <StatusChip meta={returnStatusMeta} value={r.status} /> },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) => (
        <div className="flex justify-end gap-1.5">
          {r.status === 'pending' && (
            <>
              <Button variant="ghost" onClick={() => setStatus.mutate({ id: r.id, status: 'rejected' })}>Từ chối</Button>
              <Button onClick={() => setStatus.mutate({ id: r.id, status: 'received' })}>Đã nhận hàng</Button>
            </>
          )}
          {r.status === 'received' && <Button variant="primary" onClick={() => setStatus.mutate({ id: r.id, status: 'refunded' })}>Hoàn tiền</Button>}
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Trả hàng" subtitle="Hàng trả về vào khu cách ly; hoàn tiền hoặc giảm công nợ sau khi nhận" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => { setOrderId(''); setReason(''); setTouched(false); setOpen(true); }}>Tạo phiếu trả</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(returnStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu trả hàng" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>

      <Modal
        open={open}
        title="Tạo phiếu trả hàng"
        width="lg"
        onClose={() => setOpen(false)}
        onSubmit={() => {
          setTouched(true);
          if (selected && reason.trim() && qtyValid) create.mutate();
        }}
        footer={<><span className="mr-auto self-center text-h2 tabular-nums">{money(amount)}</span><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Tạo phiếu</Button></>}
      >
        <div className="space-y-3">
          <Field label="Đơn hàng" required error={touched && !selected ? 'Vui lòng chọn đơn đã giao' : undefined}>
            <Select value={orderId} onChange={(e) => setOrderId(e.target.value)}>
              <option value="">Chọn đơn đã giao...</option>
              {orders.data?.map((o) => <option key={o.id} value={o.id}>{o.code} · {o.customerName}</option>)}
            </Select>
          </Field>
          <Field label="Lý do" required error={touched && !reason.trim() ? 'Vui lòng nhập lý do' : undefined}><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
          {selected && (
            <ul className="space-y-2 border-t border-line pt-3">
              {selected.lines.map((l) => (
                <li key={l.variantId} className="flex items-center gap-3">
                  <span className="min-w-0 flex-1">{l.productName} · {l.packLabel} <span className="text-small text-ink-faint">tối đa {l.maxQty} khay · {money(l.unitPrice)}</span></span>
                  <Input aria-label={`Số khay trả ${l.sku}`} type="number" min={0} max={l.maxQty} className="!w-24 text-right" value={quantities[l.variantId] ?? ''} onChange={(e) => setQuantities({ ...quantities, [l.variantId]: e.target.value })} />
                </li>
              ))}
            </ul>
          )}
          {touched && selected && !qtyValid && <p className="text-small text-red-fg">Nhập số khay trả là số nguyên, không vượt tối đa và có ít nhất một dòng.</p>}
        </div>
      </Modal>
    </>
  );
}
