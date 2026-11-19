import { useEffect, useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { returnStatusMeta } from '@/shared/domain/enums';
import { date, money } from '@/shared/lib/format';
import { Button, Card, ConfirmDialog, DataTable, Field, FilterBar, FilterSelect, Input, Modal, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { returnsApi } from '../api';
import { ReturnableOrderSearchField } from '../components/ReturnableOrderSearchField';
import { useReturns } from '../hooks';
import type { ReturnListItem, ReturnStatus, ReturnableOrder } from '../types';

function statusToast(status: ReturnStatus) {
  switch (status) {
    case 'received':
      return 'Đã nhận hàng. Cột Cách ly (theo SKU) đã tăng — F5 Tồn kho để đối chiếu.';
    case 'rejected':
      return 'Đã từ chối phiếu — không thay đổi tồn kho.';
    case 'refunded':
      return 'Đã ghi hoàn tiền. Tồn/Cách ly không đổi (đã cộng lúc Đã nhận hàng).';
    default:
      return 'Đã cập nhật phiếu trả';
  }
}

export default function ReturnsPage() {
  const list = useListParams(['status'] as const);
  const [params, setSearchParams] = useSearchParams();
  const presetOrder = params.get('orderId');
  const presetDelivery = params.get('deliveryId');
  const deliveryId = presetDelivery ? Number(presetDelivery) : null;
  const receiveBack = deliveryId != null && Number.isFinite(deliveryId);

  const { data, isLoading, error, refetch } = useReturns({ q: list.q, page: list.page, status: list.filters.status });
  const [open, setOpen] = useState(!!presetOrder || receiveBack);
  const [selectedOrder, setSelectedOrder] = useState<ReturnableOrder | null>(null);
  const [orderId, setOrderId] = useState<number | null>(presetOrder ? Number(presetOrder) : null);
  const [reason, setReason] = useState(receiveBack ? 'Giao thất bại — nhận hàng về' : '');
  const [quantities, setQuantities] = useState<Record<number, string>>({});
  const [touched, setTouched] = useState(false);
  const [confirm, setConfirm] = useState<{ id: number; status: ReturnStatus; code?: string } | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);

  const selected = selectedOrder?.id === orderId ? selectedOrder : null;
  useEffect(() => {
    if (receiveBack) return;
    setQuantities({});
  }, [orderId, receiveBack]);

  // Prefill phiếu nhận về từ đợt giao thất bại (?deliveryId=).
  useEffect(() => {
    if (!receiveBack) return;
    let cancelled = false;
    setLoadError(null);
    returnsApi
      .returnableOrders(undefined, deliveryId!)
      .then((rows) => {
        if (cancelled) return;
        const row = rows[0] ?? null;
        if (!row) {
          setLoadError('Đợt giao này không còn hàng để nhận về (đã tạo phiếu hoặc không phải thất bại).');
          setSelectedOrder(null);
          setOrderId(null);
          return;
        }
        setSelectedOrder(row);
        setOrderId(row.id);
        setQuantities(Object.fromEntries(row.lines.map((l) => [l.variantId, String(l.maxQty)])));
        setReason((prev) => prev.trim() || 'Giao thất bại — nhận hàng về');
        setOpen(true);
      })
      .catch(() => {
        if (!cancelled) setLoadError('Không tải được hàng nhận về từ đợt giao thất bại.');
      });
    return () => {
      cancelled = true;
    };
  }, [receiveBack, deliveryId]);

  const items = selected?.lines.map((l) => ({ variantId: l.variantId, qty: Number(quantities[l.variantId] ?? 0) })) ?? [];
  const qtyValid = items.some((i) => i.qty > 0) && (selected?.lines.every((l) => {
    const qty = Number(quantities[l.variantId] ?? 0);
    return Number.isInteger(qty) && qty >= 0 && qty <= l.maxQty;
  }) ?? false);
  const amount = selected?.lines.reduce((sum, l) => sum + Number(quantities[l.variantId] ?? 0) * l.unitPrice, 0) ?? 0;

  const clearQuery = () => {
    if (!presetOrder && !presetDelivery) return;
    const next = new URLSearchParams(params);
    next.delete('orderId');
    next.delete('deliveryId');
    setSearchParams(next, { replace: true });
  };

  const resetForm = () => {
    setOrderId(null);
    setSelectedOrder(null);
    setReason('');
    setQuantities({});
    setTouched(false);
    setLoadError(null);
    clearQuery();
  };

  const create = useAction(
    () =>
      returnsApi.create({
        orderId: orderId!,
        deliveryId: receiveBack ? deliveryId : null,
        reason: reason.trim(),
        items,
      }),
    {
      success: (r) =>
        receiveBack
          ? `Đã tạo phiếu nhận về ${r.code} — Chờ nhận, chưa đụng tồn`
          : `Đã tạo phiếu ${r.code} — trạng thái Chờ nhận, chưa đổi tồn kho`,
      onDone: () => {
        setOpen(false);
        resetForm();
      },
    },
  );
  const setStatus = useAction(
    ({ id, status }: { id: number; status: ReturnStatus }) => returnsApi.setStatus(id, status),
    {
      success: () => statusToast(confirm?.status ?? 'received'),
      onDone: () => setConfirm(null),
    },
  );

  const columns: Column<ReturnListItem>[] = [
    { key: 'code', header: 'Mã', cell: (r) => <span className="num">{r.code}</span> },
    { key: 'order', header: 'Đơn hàng', cell: (r) => (r.orderId ? <Link to={`/orders/${r.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{r.orderCode}</Link> : '—') },
    { key: 'customer', header: 'Khách hàng', cell: (r) => <span className="font-medium">{r.customerName ?? '—'}</span> },
    { key: 'reason', header: 'Lý do', hideOnMobile: true, cell: (r) => r.reason ?? '—' },
    {
      key: 'items',
      header: 'Hàng trả',
      hideOnMobile: true,
      cell: (r) => {
        const label = r.items.map((i) => `${i.qty} × ${i.sku}`).join(', ');
        const sku = r.items[0]?.sku;
        if ((r.status === 'received' || r.status === 'refunded') && sku) {
          return (
            <span>
              {label}{' '}
              <Link to={`/inventory?q=${encodeURIComponent(sku)}`} className="text-micro text-primary hover:underline">
                Tồn
              </Link>
            </span>
          );
        }
        return label || '—';
      },
    },
    { key: 'at', header: 'Ngày tạo', hideOnMobile: true, cell: (r) => date(r.createdAt) },
    { key: 'amount', header: 'Giá trị', align: 'right', cell: (r) => <span className="num">{money(r.amount)}</span> },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (r) => <StatusChip meta={returnStatusMeta} value={r.status} />,
    },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) => (
        <div className="flex justify-end gap-1.5">
          {r.status === 'pending' && (
            <>
              <Button variant="ghost" onClick={() => setConfirm({ id: r.id, status: 'rejected', code: r.code })}>Từ chối</Button>
              <Button onClick={() => setConfirm({ id: r.id, status: 'received', code: r.code })}>Đã nhận hàng</Button>
            </>
          )}
          {r.status === 'received' && (
            <Button variant="primary" onClick={() => setConfirm({ id: r.id, status: 'refunded', code: r.code })}>Hoàn tiền</Button>
          )}
        </div>
      ),
    },
  ];

  const confirmCopy = confirm?.status === 'received'
    ? {
        title: 'Xác nhận đã nhận hàng?',
        message: `Phiếu ${confirm.code ?? ''} sẽ chuyển sang Đã nhận hàng. Hệ thống mặc định cộng hàng vào cột Cách ly trên Tồn kho (chưa đưa vào Khả dụng để bán lại).`,
        confirmLabel: 'Nhận hàng & cập nhật tồn',
        danger: false,
      }
    : confirm?.status === 'rejected'
      ? {
          title: 'Từ chối phiếu trả?',
          message: `Phiếu ${confirm.code ?? ''} sẽ bị từ chối. Tồn kho không thay đổi.`,
          confirmLabel: 'Từ chối phiếu',
          danger: true,
        }
      : {
          title: 'Xác nhận hoàn tiền?',
          message: `Phiếu ${confirm?.code ?? ''} đánh dấu đã hoàn tiền. Không đụng tồn: Cách ly đã cộng lúc Đã nhận hàng vẫn giữ nguyên.`,
          confirmLabel: 'Đã hoàn tiền',
          danger: false,
        };

  return (
    <>
      <PageHeader
        title="Trả hàng"
        subtitle="Chờ nhận → Đã nhận (+Cách ly) → Hoàn tiền (chỉ tiền). Từ chối không đổi tồn."
        actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => { resetForm(); setOpen(true); }}>Tạo phiếu trả</Button>}
      />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(returnStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu trả hàng" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>

      <ConfirmDialog
        open={!!confirm}
        title={confirmCopy.title}
        message={confirmCopy.message}
        confirmLabel={confirmCopy.confirmLabel}
        danger={confirmCopy.danger}
        loading={setStatus.isPending}
        onClose={() => setConfirm(null)}
        onConfirm={() => confirm && setStatus.mutate({ id: confirm.id, status: confirm.status })}
      />

      <Modal
        open={open}
        title={receiveBack ? 'Nhận hàng về (giao thất bại)' : 'Tạo phiếu trả hàng'}
        width="lg"
        onClose={() => { setOpen(false); resetForm(); }}
        onSubmit={() => {
          setTouched(true);
          if (selected && reason.trim() && qtyValid) create.mutate();
        }}
        footer={<><span className="mr-auto self-center text-h2 tabular-nums">{money(amount)}</span><Button onClick={() => { setOpen(false); resetForm(); }}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>{receiveBack ? 'Tạo phiếu nhận về' : 'Tạo phiếu'}</Button></>}
      >
        <div className="space-y-3">
          {loadError && <p className="text-small text-red-fg">{loadError}</p>}
          <Field label="Đơn hàng" required error={touched && !selected ? 'Vui lòng chọn đơn đã giao' : undefined}>
            {receiveBack ? (
              <Input value={selected ? `${selected.code} · ${selected.customerName ?? 'Khách lẻ'}` : 'Đang tải…'} readOnly disabled />
            ) : (
              <ReturnableOrderSearchField
                value={orderId}
                onChange={(o) => {
                  setSelectedOrder(o);
                  setOrderId(o?.id ?? null);
                }}
              />
            )}
          </Field>
          {receiveBack && (
            <p className="text-small text-ink-muted">Đợt giao #{deliveryId} — phiếu gắn deliveryId, chưa đụng tồn đến khi kho bấm Đã nhận hàng.</p>
          )}
          <Field label="Lý do" required error={touched && !reason.trim() ? 'Vui lòng nhập lý do' : undefined}><Input value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
          {selected && (
            <ul className="space-y-2 border-t border-line pt-3">
              {selected.lines.map((l) => (
                <li key={l.variantId} className="flex items-center gap-3">
                  <span className="min-w-0 flex-1">{l.productName} · {l.packLabel} <span className="text-small text-ink-faint">tối đa {l.maxQty} khay · {money(l.unitPrice)}</span></span>
                  <Input aria-label={`Số khay ${receiveBack ? 'nhận về' : 'trả'} ${l.sku}`} type="number" min={0} max={l.maxQty} className="!w-24 text-right" value={quantities[l.variantId] ?? ''} onChange={(e) => setQuantities({ ...quantities, [l.variantId]: e.target.value })} />
                </li>
              ))}
            </ul>
          )}
          {touched && selected && !qtyValid && <p className="text-small text-red-fg">Nhập số khay là số nguyên, không vượt tối đa và có ít nhất một dòng.</p>}
        </div>
      </Modal>
    </>
  );
}
