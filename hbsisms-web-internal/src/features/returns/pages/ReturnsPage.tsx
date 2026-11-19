import { useEffect, useRef, useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useSearchParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { returnStatusMeta } from '@/shared/domain/enums';
import { date, money } from '@/shared/lib/format';
import { Button, Card, ConfirmDialog, DataTable, Field, FilterBar, FilterSelect, Input, Modal, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { useToast } from '@/shared/ui/Toast';
import { returnsApi } from '../api';
import { ReceiveInspectModal } from '../components/ReceiveInspectModal';
import { ReturnableOrderSearchField } from '../components/ReturnableOrderSearchField';
import { useReturns } from '../hooks';
import type { ReturnInspectLine, ReturnListItem, ReturnStatus, ReturnableOrder } from '../types';

function statusToast(status: ReturnStatus, opts?: { inspected?: boolean; receiveBackHeld?: boolean }) {
  switch (status) {
    case 'received':
      if (opts?.receiveBackHeld) {
        return 'Đã nhận về: còn HOLD → chỉ nhả giữ về kệ (không vào khu hàng trả). F5 Tồn kho.';
      }
      return opts?.inspected
        ? 'Đã kiểm hàng. Đạt → Khả dụng; không đạt → khu hàng trả. F5 Tồn kho để đối chiếu.'
        : 'Đã nhận hàng. HOLD được nhả (nếu còn) hoặc mặc định vào khu hàng trả — F5 Tồn kho.';
    case 'rejected':
      return 'Đã từ chối phiếu — không thay đổi tồn kho.';
    case 'refunded':
      return 'Đã hoàn tiền: ghi phiếu hoàn (TM/CK) hoặc giảm nợ B2B. Tồn không đổi — F5 Thanh toán / Công nợ.';
    default:
      return 'Đã cập nhật phiếu trả';
  }
}

export default function ReturnsPage() {
  const toast = useToast();
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
  const [confirm, setConfirm] = useState<{ id: number; status: Exclude<ReturnStatus, 'received'>; code?: string } | null>(null);
  const [inspectRow, setInspectRow] = useState<ReturnListItem | null>(null);
  const inspectDetailedRef = useRef(false);
  const receiveBackHeldRef = useRef(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [receiveLoading, setReceiveLoading] = useState(false);

  const selected = selectedOrder?.id === orderId ? selectedOrder : null;
  useEffect(() => {
    if (receiveBack) return;
    setQuantities({});
  }, [orderId, receiveBack]);

  useEffect(() => {
    if (!receiveBack) return;
    let cancelled = false;
    setLoadError(null);
    setReceiveLoading(true);
    setSelectedOrder(null);
    returnsApi
      .returnableOrders(undefined, deliveryId!)
      .then(async (rows) => {
        if (cancelled) return;
        const row = rows[0] ?? null;
        if (row) {
          setSelectedOrder(row);
          setOrderId(row.id);
          setQuantities(Object.fromEntries(row.lines.map((l) => [l.variantId, String(l.maxQty)])));
          setReason((prev) => prev.trim() || 'Giao thất bại — nhận hàng về');
          setOpen(true);
          return;
        }
        // Đã tạo phiếu rồi → mở Kiểm nếu còn Chờ nhận; đã xong thì không mở form tạo (nút hết tác dụng tạo).
        const page = await returnsApi.list({ page: 1, pageSize: 50, status: 'pending' });
        if (cancelled) return;
        const existing = page.items.find((r) => r.deliveryId === deliveryId) ?? null;
        setOpen(false);
        setSelectedOrder(null);
        setOrderId(null);
        clearQuery();
        if (existing) {
          setInspectRow(existing);
          return;
        }
        toast.success(`Đợt giao #${deliveryId} đã nhận về xong — xem phiếu trên danh sách (Đã nhận hàng / Hoàn tiền).`);
      })
      .catch(() => {
        if (!cancelled) {
          setLoadError('Không tải được hàng nhận về từ đợt giao thất bại.');
          setOpen(true);
        }
      })
      .finally(() => {
        if (!cancelled) setReceiveLoading(false);
      });
    return () => {
      cancelled = true;
    };
    // clearQuery ổn định theo URL; cố ý không đưa vào deps để tránh loop.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- chỉ chạy lại khi đổi deliveryId
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
    ({ id, status, inspections }: { id: number; status: ReturnStatus; inspections?: ReturnInspectLine[] | null }) =>
      returnsApi.setStatus(id, status, inspections),
    {
      success: () =>
        statusToast(confirm?.status ?? 'received', {
          inspected: inspectDetailedRef.current,
          receiveBackHeld: receiveBackHeldRef.current,
        }),
      onDone: () => {
        setConfirm(null);
        setInspectRow(null);
        inspectDetailedRef.current = false;
        receiveBackHeldRef.current = false;
      },
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
              <Button onClick={() => setInspectRow(r)}>Kiểm / nhận hàng</Button>
            </>
          )}
          {r.status === 'received' && (
            <Button variant="primary" onClick={() => setConfirm({ id: r.id, status: 'refunded', code: r.code })}>Hoàn tiền</Button>
          )}
        </div>
      ),
    },
  ];

  const confirmCopy = confirm?.status === 'rejected'
    ? {
        title: 'Từ chối phiếu trả?',
        message: `Phiếu ${confirm.code ?? ''} sẽ bị từ chối. Tồn kho không thay đổi.`,
        confirmLabel: 'Từ chối phiếu',
        danger: true,
      }
    : {
        title: 'Xác nhận hoàn tiền?',
        message: `Phiếu ${confirm?.code ?? ''}: ghi nhận hoàn theo giá trị phiếu (TM/CK hoặc giảm nợ nếu công nợ). Tồn kho không đổi.`,
        confirmLabel: 'Đã hoàn tiền',
        danger: false,
      };

  return (
    <>
      <PageHeader
        title="Trả hàng"
        subtitle="Chờ nhận → Kiểm (đạt=kệ đã xuất / không đạt=HT-01 cùng kho) → Hoàn tiền. Giao thất bại: Nhận về → nhả HOLD hoặc kiểm."
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

      <ReceiveInspectModal
        open={!!inspectRow}
        row={inspectRow}
        loading={setStatus.isPending}
        onClose={() => setInspectRow(null)}
        onConfirmInspect={(inspections) => {
          if (!inspectRow) return;
          inspectDetailedRef.current = true;
          // Demo FAIL còn HOLD: BE chỉ nhả giữ — không vào CL dù FE chọn cách ly.
          receiveBackHeldRef.current = inspectRow.deliveryId != null;
          setStatus.mutate({ id: inspectRow.id, status: 'received', inspections });
        }}
        onConfirmQuarantineAll={() => {
          if (!inspectRow) return;
          inspectDetailedRef.current = false;
          receiveBackHeldRef.current = inspectRow.deliveryId != null;
          setStatus.mutate({ id: inspectRow.id, status: 'received', inspections: null });
        }}
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
        footer={<><span className="mr-auto self-center text-h2 tabular-nums">{money(amount)}</span><Button onClick={() => { setOpen(false); resetForm(); }}>{loadError ? 'Đóng' : 'Hủy bỏ'}</Button><Button type="submit" variant="primary" loading={create.isPending} disabled={!!loadError || !selected}>{receiveBack ? 'Tạo phiếu nhận về' : 'Tạo phiếu'}</Button></>}
      >
        <div className="space-y-3">
          {loadError && <p className="text-small text-red-fg">{loadError}</p>}
          <Field label="Đơn hàng" required error={touched && !selected && !loadError ? 'Vui lòng chọn đơn đã giao' : undefined}>
            {receiveBack ? (
              <Input
                value={
                  selected
                    ? `${selected.code} · ${selected.customerName ?? 'Khách lẻ'}`
                    : receiveLoading
                      ? 'Đang tải…'
                      : loadError
                        ? '—'
                        : 'Đang tải…'
                }
                readOnly
                disabled
              />
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
            <p className="text-small text-ink-muted">Đợt giao #{deliveryId} — phiếu gắn deliveryId, chưa đụng tồn đến khi kho kiểm / nhận hàng.</p>
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
