import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowDown, ArrowUp, ChevronDown } from 'lucide-react';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  payDirectionLabels,
  paymentMethodLabels,
  paymentReconcileFlagLabels,
  paymentReconcileFlagMeta,
  payTxnStatusMeta,
  saleChannelLabels,
  type PaymentReconcileFlag,
} from '@/shared/domain/enums';
import { dateTime, money } from '@/shared/lib/format';
import {
  Button,
  Card,
  Chip,
  DataTable,
  FilterBar,
  FilterSelect,
  PageHeader,
  Pagination,
  SearchBox,
  Select,
  StatusChip,
  enumOptions,
  useListParams,
  type Column,
} from '@/shared/ui';
import { paymentsApi } from '../api';
import { RefundFormModal } from '../components/RefundFormModal';
import type { PaymentListItem } from '../types';

type RefundTarget = {
  orderId: number;
  orderCode: string | null;
  customerName: string | null;
  maxAmount: number;
  sourcePaymentId: number | null;
  source: PaymentListItem | null;
  refundedAmount: number;
};

export default function PaymentsPage() {
  const list = useListParams(['method', 'direction', 'status', 'channel', 'reconcileFlag', 'sort'] as const);
  const oldestFirst = list.filters.sort === 'asc';
  const query = {
    q: list.q,
    page: list.page,
    method: list.filters.method,
    direction: list.filters.direction,
    status: list.filters.status,
    channel: list.filters.channel,
    reconcileFlag: list.filters.reconcileFlag,
    sort: list.filters.sort,
  };
  const queryClient = useQueryClient();
  const [refundTarget, setRefundTarget] = useState<RefundTarget | null>(null);
  const [openNotes, setOpenNotes] = useState<Set<number>>(() => new Set());
  const toggleNote = (id: number) =>
    setOpenNotes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['payments', query],
    queryFn: ({ signal }) => paymentsApi.list(query, signal),
    placeholderData: keepPreviousData,
  });

  const mark = useMutation({
    mutationFn: ({ id, flag }: { id: number; flag: PaymentReconcileFlag }) => paymentsApi.markReconcile(id, flag),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['payments'] }),
  });

  const netByOrder = useMemo(() => {
    const map = new Map<number, number>();
    for (const p of data?.items ?? []) {
      if (!p.orderId || p.status !== 'success') continue;
      const delta = p.direction === 'refund' ? -p.amount : p.amount;
      map.set(p.orderId, (map.get(p.orderId) ?? 0) + delta);
    }
    return map;
  }, [data?.items]);
  const refundedByOrder = useMemo(() => {
    const map = new Map<number, number>();
    for (const p of data?.items ?? []) {
      if (!p.orderId || p.status !== 'success' || p.direction !== 'refund') continue;
      map.set(p.orderId, (map.get(p.orderId) ?? 0) + p.amount);
    }
    return map;
  }, [data?.items]);

  const openRefund = (p: PaymentListItem) => {
    if (!p.orderId) return;
    const pageNet = netByOrder.get(p.orderId);
    const maxAmount = Math.max(0, pageNet ?? (p.direction === 'in' ? p.amount : 0));
    setRefundTarget({
      orderId: p.orderId,
      orderCode: p.orderCode,
      customerName: p.customerName,
      maxAmount: maxAmount > 0 ? maxAmount : p.amount,
      sourcePaymentId: p.direction === 'in' ? p.id : null,
      source: p.direction === 'in' ? p : null,
      refundedAmount: refundedByOrder.get(p.orderId) ?? 0,
    });
  };

  const columns: Column<PaymentListItem>[] = [
    {
      key: 'code',
      header: (
        <button
          type="button"
          className="inline-flex items-center gap-1 uppercase tracking-wide hover:text-ink"
          onClick={() => list.setFilter('sort', oldestFirst ? '' : 'asc')}
          title={oldestFirst ? 'Đang xếp cũ nhất trước — bấm để xếp mới nhất trước' : 'Đang xếp mới nhất trước — bấm để xếp cũ nhất trước'}
          aria-label={oldestFirst ? 'Mã, cũ nhất trước. Bấm để xếp mới nhất trước' : 'Mã, mới nhất trước. Bấm để xếp cũ nhất trước'}
        >
          Mã {oldestFirst ? <ArrowUp size={13} /> : <ArrowDown size={13} />}
        </button>
      ),
      cell: (p) => <span className="num">{p.code}</span>,
    },
    { key: 'at', header: 'Thời gian', cell: (p) => <span className="whitespace-nowrap">{dateTime(p.paidAt)}</span> },
    { key: 'customer', header: 'Khách hàng', cell: (p) => <span className="font-medium">{p.customerName ?? '—'}</span> },
    {
      key: 'order',
      header: 'Đơn hàng',
      hideOnMobile: true,
      cell: (p) =>
        p.orderId ? (
          <Link to={`/orders/${p.orderId}`} className="num whitespace-nowrap text-primary hover:underline">
            {p.orderCode}
          </Link>
        ) : (
          '—'
        ),
    },
    {
      key: 'channel',
      header: 'Kênh',
      hideOnMobile: true,
      cell: (p) => (p.channel ? saleChannelLabels[p.channel] : '—'),
    },
    { key: 'method', header: 'Phương thức', cell: (p) => paymentMethodLabels[p.method] },
    { key: 'ref', header: 'Tham chiếu', hideOnMobile: true, cell: (p) => <span className="num">{p.reference ?? p.payosOrderCode ?? '—'}</span> },
    { key: 'direction', header: 'Loại', cell: (p) => <Chip tone={p.direction === 'in' ? 'green' : 'amber'}>{payDirectionLabels[p.direction]}</Chip> },
    {
      key: 'amount',
      header: 'Số tiền',
      align: 'right',
      cell: (p) => (
        <span className="num font-medium">
          {p.direction === 'refund' ? '−' : ''}
          {money(p.amount)}
        </span>
      ),
    },
    { key: 'status', header: 'Trạng thái', cell: (p) => <StatusChip meta={payTxnStatusMeta} value={p.status} /> },
    {
      key: 'reconcile',
      header: 'Đối soát',
      cell: (p) => (
        <div className="flex min-w-[8.5rem] flex-col gap-1">
          {p.needsRefund ? <Chip tone="amber">Cần hoàn</Chip> : null}
          <Select
            aria-label={`Đối soát ${p.code}`}
            value={p.reconcileFlag ?? 'none'}
            disabled={mark.isPending}
            onChange={(e) => mark.mutate({ id: p.id, flag: e.target.value as PaymentReconcileFlag })}
          >
            {Object.entries(paymentReconcileFlagLabels).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </Select>
          {(p.reconcileFlag ?? 'none') !== 'none' ? (
            p.note ? (
              // Bấm nhãn để xem / ẩn ghi chú (số liệu lệch tiền do PayOS báo)
              <button
                type="button"
                onClick={() => toggleNote(p.id)}
                aria-expanded={openNotes.has(p.id)}
                title={openNotes.has(p.id) ? 'Ẩn chi tiết' : 'Xem chi tiết'}
                className="self-start rounded-full focus-visible:outline focus-visible:outline-2 focus-visible:outline-primary"
              >
                <Chip tone={paymentReconcileFlagMeta[p.reconcileFlag ?? 'none'].tone}>
                  {paymentReconcileFlagMeta[p.reconcileFlag ?? 'none'].label}
                  <ChevronDown
                    size={12}
                    className={`ml-0.5 transition-transform ${openNotes.has(p.id) ? 'rotate-180' : ''}`}
                    aria-hidden
                  />
                </Chip>
              </button>
            ) : (
              <span className="self-start">
                <StatusChip meta={paymentReconcileFlagMeta} value={p.reconcileFlag ?? 'none'} />
              </span>
            )
          ) : null}
          {(p.reconcileFlag ?? 'none') !== 'none' && p.note && openNotes.has(p.id) ? (
            <div className="max-w-[15rem] whitespace-normal rounded-md border border-line bg-thead px-2 py-1.5 text-small leading-snug text-ink">
              {p.note}
            </div>
          ) : null}
        </div>
      ),
    },
    {
      key: 'actions',
      header: '',
      cell: (p) =>
        p.orderId && p.direction === 'in' && p.status === 'success' ? (
          <Button variant={p.needsRefund ? 'primary' : 'ghost'} onClick={() => openRefund(p)}>
            Hoàn tiền
          </Button>
        ) : null,
    },
  ];

  return (
    <>
      <PageHeader
        title="Thanh toán"
        subtitle="Lịch sử thu/hoàn; đối soát thiếu/thừa/trùng; ghi nhận hoàn tiền khi cần"
      />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, đơn, khách, tham chiếu..." />
          <FilterSelect label="Phương thức" value={list.filters.method} onChange={(v) => list.setFilter('method', v)}>
            {enumOptions(paymentMethodLabels)}
          </FilterSelect>
          <FilterSelect label="Loại" value={list.filters.direction} onChange={(v) => list.setFilter('direction', v)}>
            {enumOptions(payDirectionLabels)}
          </FilterSelect>
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>
            {enumOptions(Object.fromEntries(Object.entries(payTxnStatusMeta).map(([k, m]) => [k, m.label])))}
          </FilterSelect>
          <FilterSelect label="Kênh" value={list.filters.channel} onChange={(v) => list.setFilter('channel', v)}>
            {enumOptions(saleChannelLabels)}
          </FilterSelect>
          <FilterSelect
            label="Đối soát"
            value={list.filters.reconcileFlag}
            onChange={(v) => list.setFilter('reconcileFlag', v)}
          >
            {enumOptions(paymentReconcileFlagLabels)}
          </FilterSelect>
        </FilterBar>
        <DataTable
          columns={columns}
          rows={data?.items}
          rowKey={(p) => p.id}
          loading={isLoading}
          error={error}
          onRetry={refetch}
          emptyText="Chưa có giao dịch"
        />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>

      {refundTarget ? (
        <RefundFormModal
          open
          onClose={() => {
            setRefundTarget(null);
            queryClient.invalidateQueries({ queryKey: ['payments'] });
          }}
          orderId={refundTarget.orderId}
          orderCode={refundTarget.orderCode}
          customerName={refundTarget.customerName}
          maxAmount={refundTarget.maxAmount}
          sourcePaymentId={refundTarget.sourcePaymentId}
          source={refundTarget.source}
          refundedAmount={refundTarget.refundedAmount}
        />
      ) : null}
    </>
  );
}
