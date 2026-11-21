import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CheckCircle2, ClipboardList, CreditCard, Pencil, Printer, RotateCcw, Truck, XCircle } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { approvalsApi } from '@/features/approvals/api';
import { useAuth } from '@/features/auth';
import { DeliveryFormModal, DeliveryStateActions } from '@/features/deliveries';
import { PaymentFormModal } from '@/features/payments';
import { PickingSlipDialog, pickingApi } from '@/features/picking';
import type { OrderPickingSlip } from '@/features/picking';
import { ReceiptDialog } from '@/features/pos/components/ReceiptDialog';
import type { Receipt } from '@/features/pos/types';
import { useAction } from '@/shared/api/useAction';
import type { SalesReturn } from '@/shared/domain/entities';
import { allocationStatusMeta, deliveryStateMeta, fulfillmentLabels, fulfillmentMeta, orderStateMeta, payDirectionLabels, paymentMethodLabels, paymentStatusMeta, payTxnStatusMeta, priceBasisMeta, returnStatusMeta, saleChannelLabels, sellUnitLabels, type OrderState } from '@/shared/domain/enums';
import { date, dateTime, money, num } from '@/shared/lib/format';
import { Alert, Button, Card, Chip, ConfirmDialog, DataTable, ErrorState, InfoList, LinkButton, Meter, PageHeader, PageLoading, StatusChip, type Column } from '@/shared/ui';
import { ordersApi } from '../api';
import { isCollectOnDelivery } from '../cod';
import { OrderProgress } from '../components/OrderProgress';
import { useOrder } from '../hooks';
import type { OrderAuditTrailItem, OrderDeliveryView, OrderLineView } from '../types';

const NEXT_LABEL: Partial<Record<OrderState, string>> = { confirmed: 'Xác nhận đơn', preparing: 'Bắt đầu chuẩn bị', to_ship: 'Sẵn sàng giao', completed: 'Hoàn tất đơn' };

const AUDIT_ACTION_LABEL: Record<string, string> = {
  create: 'Tạo đơn',
  update: 'Sửa đơn',
  cancel: 'Hủy đơn',
  print_receipt: 'In biên lai',
  complete_counter: 'Hoàn tất quầy',
};

export default function OrderDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { can } = useAuth();
  const { data: order, isLoading, error, refetch } = useOrder(id);
  const [cancelling, setCancelling] = useState(false);
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);
  const [pickingOpen, setPickingOpen] = useState(false);
  const [pickingSlip, setPickingSlip] = useState<OrderPickingSlip | null>(null);
  const [deciding, setDeciding] = useState<'approved' | 'rejected' | null>(null);

  const needsOrderApproval = !!order && order.isOverLimit && !order.approvedBy && order.state !== 'cancelled';
  const pendingApproval = useQuery({
    queryKey: ['approvals', 'pending-for-order', id],
    queryFn: ({ signal }) => approvalsApi.list({ status: 'pending', pageSize: 50 }, signal),
    enabled: can('approve') && needsOrderApproval,
    select: (page) => page.items.find((a) => a.refType === 'sales_order' && a.refId === id),
  });

  const transition = useAction((state: OrderState) => ordersApi.transition(id, state), { success: 'Đã cập nhật trạng thái đơn' });
  const cancel = useAction((reason: string) => ordersApi.cancel(id, reason), { success: 'Đã hủy đơn, hàng giữ đã được nhả', onDone: () => setCancelling(false) });
  const completeCounter = useAction(() => ordersApi.completeAtCounter(id), { success: 'Đã hoàn tất bàn giao tại quầy' });
  const printReceipt = useAction(() => ordersApi.receipt(id), { success: 'Đã tải biên lai', onDone: (r) => setReceipt(r) });
  const printPicking = useAction(() => pickingApi.orderSlip(id), {
    success: 'Đã tải phiếu soạn',
    onDone: (slip) => setPickingSlip(slip),
  });
  const decideApproval = useAction(
    (note: string) => approvalsApi.decide(pendingApproval.data!.id, { decision: deciding!, note }),
    {
      success: deciding === 'approved' ? 'Đã duyệt đơn vượt hạn mức' : 'Đã từ chối đơn vượt hạn mức',
      onDone: () => setDeciding(null),
    },
  );

  if (isLoading) return <PageLoading />;
  if (error || !order) return <ErrorState error={error} onRetry={refetch} />;

  const orderedKhay = order.lines.reduce((s, l) => s + l.qtyKhay, 0);
  // Đợt "Trọn đơn" (checkout web) có items rỗng = đã xếp lịch cả đơn.
  const scheduledKhay = order.deliveries.filter((d) => d.state !== 'failed').reduce((s, d) => s + ((d.items ?? []).length === 0 ? orderedKhay : (d.items ?? []).reduce((a, i) => a + i.qtyKhay, 0)), 0);
  const canDeliver = ['to_ship', 'partial'].includes(order.state) && scheduledKhay < orderedKhay;
  const outstanding = order.total - order.paidAmount;
  // Inter 3 dòng 8: thu = orders|debt; chặn khi đang có link PayOS chờ thanh toán.
  const hasPendingPayos =
    order.payments.some((p) => p.method === 'payos' && p.status === 'pending')
    || (order.paymentMethod === 'payos' && order.paymentStatus === 'waiting');
  const canPay =
    (can('orders') || can('debt'))
    && order.state !== 'cancelled'
    && outstanding > 0
    && !(order.paymentMethod === 'credit_net' && order.state === 'pending')
    && !hasPendingPayos;
  const canReturn = ['delivered', 'completed', 'partial'].includes(order.state);
  const canCompleteCounter =
    order.fulfillmentMethod === 'pickup'
    && order.state !== 'completed'
    && order.state !== 'cancelled'
    && order.state !== 'pending'
    && order.paymentStatus === 'paid';
  const canEdit = ['pending', 'confirmed'].includes(order.state) && !['paid', 'partial'].includes(order.paymentStatus);
  const canPrintReceipt = order.paymentStatus === 'paid';
  const canPrintPicking =
    order.state !== 'cancelled'
    && order.state !== 'pending'
    && (order.allocationStatus === 'full' || order.allocationStatus === 'held');
  const collectOnDelivery = isCollectOnDelivery(order.paymentMethod, order.fulfillmentMethod);
  const waitingForPrepare = order.state === 'confirmed' && !order.nextStates.includes('preparing');
  const prepareBlockReason =
    !collectOnDelivery && order.paymentStatus !== 'paid'
      ? 'Cần thanh toán đủ trước khi chuẩn bị hàng.'
      : order.allocationStatus !== 'full'
        ? 'Chưa phân bổ đủ hàng — không thể chuẩn bị.'
        : null;
  const showCodHint =
    collectOnDelivery
    && order.paymentStatus !== 'paid'
    && !['cancelled', 'completed'].includes(order.state)
    && outstanding > 0;

  const lineColumns: Column<OrderLineView>[] = [
    { key: 'product', header: 'Sản phẩm', cell: (l) => <div><div className="font-medium">{l.productName}</div><div className="num text-ink-faint">{l.sku} · {l.packLabel}</div></div> },
    { key: 'qty', header: 'SL', align: 'right', cell: (l) => <span className="num whitespace-nowrap">{num(l.qty)} {sellUnitLabels[l.unit]}</span> },
    { key: 'khay', header: 'Khay', align: 'right', cell: (l) => <span className="num">{num(l.qtyKhay)}</span> },
    { key: 'shipped', header: 'Đã giao', align: 'right', hideOnMobile: true, cell: (l) => <span className="num">{num(l.deliveredKhay)}</span> },
    { key: 'price', header: 'Đơn giá', align: 'right', cell: (l) => <span className="num">{money(l.unitPrice)}</span> },
    { key: 'basis', header: 'Nguồn giá', hideOnMobile: true, cell: (l) => <StatusChip meta={priceBasisMeta} value={l.priceBasis} /> },
    { key: 'total', header: 'Thành tiền', align: 'right', cell: (l) => <span className="num font-medium">{money(l.lineTotal)}</span> },
  ];
  const deliveryColumns: Column<OrderDeliveryView>[] = [
    { key: 'leg', header: 'Giao hàng', cell: (d) => <div><div className="font-medium">{d.legLabel ?? 'Trọn đơn'}</div><div className="num text-ink-faint">{d.code}</div></div> },
    { key: 'when', header: 'Lịch giao', cell: (d) => <span className="whitespace-nowrap">{date(d.scheduledDate)}{d.timeWindow ? ` · ${d.timeWindow}` : ''}</span> },
    { key: 'who', header: 'Cách giao', cell: (d) => <StatusChip meta={fulfillmentMeta} value={d.fulfillmentMethod} /> },
    { key: 'qty', header: 'Khay', align: 'right', cell: (d) => <span className="num">{num((d.items ?? []).reduce((s, i) => s + i.qtyKhay, 0))}</span> },
    { key: 'state', header: 'Trạng thái', cell: (d) => <StatusChip meta={deliveryStateMeta} value={d.state} /> },
    {
      key: 'note',
      header: 'Ghi chú / lý do',
      hideOnMobile: true,
      cell: (d) => (d.note ? <span className={d.state === 'failed' ? 'text-coral' : 'text-ink-muted'}>{d.note}</span> : '—'),
    },
    {
      key: 'actions',
      header: '',
      align: 'right',
      cell: (d) => (
        <DeliveryStateActions
          delivery={d}
          orderState={order.state}
          codCollect={{
            paymentMethod: order.paymentMethod,
            fulfillmentMethod: order.fulfillmentMethod,
            paymentStatus: order.paymentStatus,
            outstanding,
            customerId: order.customerId,
            customerName: order.customerName,
            orderId: order.id,
            orderCode: order.code,
          }}
        />
      ),
    },
  ];

  const returnColumns: Column<SalesReturn>[] = [
    { key: 'code', header: 'Mã phiếu', cell: (r) => <span className="num">{r.code}</span> },
    { key: 'reason', header: 'Lý do', cell: (r) => r.reason ?? '—' },
    { key: 'amount', header: 'Số tiền', align: 'right', cell: (r) => <span className="num">{money(r.amount)}</span> },
    { key: 'status', header: 'Trạng thái', cell: (r) => <StatusChip meta={returnStatusMeta} value={r.status} /> },
    { key: 'at', header: 'Ngày tạo', hideOnMobile: true, cell: (r) => <span className="whitespace-nowrap">{dateTime(r.createdAt)}</span> },
  ];

  const auditColumns: Column<OrderAuditTrailItem>[] = [
    {
      key: 'action',
      header: 'Hành động',
      cell: (a) => AUDIT_ACTION_LABEL[a.action] ?? a.action,
    },
    { key: 'who', header: 'Người thực hiện', cell: (a) => a.actorName ?? '—' },
    { key: 'at', header: 'Thời điểm', cell: (a) => <span className="whitespace-nowrap">{dateTime(a.createdAt)}</span> },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/orders" className="hover:underline">Đơn hàng</Link> / {order.code}</>}
        title={
          <span className="flex flex-wrap items-center gap-2">
            <span className="font-mono">{order.code}</span>
            <StatusChip meta={orderStateMeta} value={order.state} />
            <StatusChip meta={paymentStatusMeta} value={order.paymentStatus} />
            {order.state !== 'cancelled' && <StatusChip meta={allocationStatusMeta} value={order.allocationStatus} />}
          </span>
        }
        actions={
          <>
            {canEdit && <LinkButton to={`/orders/${order.id}/edit`} icon={<Pencil size={14} />}>Sửa</LinkButton>}
            {canPrintReceipt && (
              <Button icon={<Printer size={14} />} loading={printReceipt.isPending} onClick={() => printReceipt.mutate()}>
                In biên lai
              </Button>
            )}
            {canPrintPicking && (
              <Button
                icon={<ClipboardList size={14} />}
                loading={printPicking.isPending}
                onClick={() => {
                  setPickingSlip(null);
                  setPickingOpen(true);
                  printPicking.mutate();
                }}
              >
                In phiếu soạn
              </Button>
            )}
            {order.canCancel && <Button variant="danger" icon={<XCircle size={14} />} onClick={() => setCancelling(true)}>Hủy đơn</Button>}
            {canReturn && <LinkButton to={`/returns?orderId=${order.id}`} icon={<RotateCcw size={14} />}>Trả hàng</LinkButton>}
            {canPay && <Button icon={<CreditCard size={14} />} onClick={() => setPaymentOpen(true)}>Ghi nhận thanh toán</Button>}
            {canDeliver && <Button variant="primary" icon={<Truck size={14} />} onClick={() => setDeliveryOpen(true)}>Tạo giao hàng</Button>}
            {canCompleteCounter && (
              <Button variant="primary" icon={<CheckCircle2 size={14} />} loading={completeCounter.isPending} onClick={() => completeCounter.mutate()}>
                Hoàn tất quầy
              </Button>
            )}
            {order.nextStates.map((state) => (
              <Button key={state} variant="primary" loading={transition.isPending} onClick={() => transition.mutate(state)}>{NEXT_LABEL[state] ?? state}</Button>
            ))}
          </>
        }
      />
      {needsOrderApproval && (
        <div className="mb-4">
          <Alert tone="danger" title={`Vượt hạn mức ${money(order.overLimitAmount)}, đơn đang chờ duyệt`}>
            Chưa giữ hàng cho đến khi được duyệt.
            {can('approve') && pendingApproval.data ? (
              <span className="ml-2 inline-flex flex-wrap gap-2">
                <Button variant="primary" onClick={() => setDeciding('approved')}>Duyệt</Button>
                <Button variant="danger" onClick={() => setDeciding('rejected')}>Từ chối</Button>
              </span>
            ) : can('approve') ? (
              <>{' '}<Link to="/approvals" className="font-semibold underline">Đi tới Phê duyệt</Link></>
            ) : null}
          </Alert>
        </div>
      )}
      {waitingForPrepare && prepareBlockReason && (
        <div className="mb-4">
          <Alert tone="warning" title="Chưa thể chuyển sang Chuẩn bị">
            {prepareBlockReason} {!collectOnDelivery && order.paymentStatus !== 'paid' && canPay && 'Hãy ghi nhận thanh toán đủ trước.'}
          </Alert>
        </div>
      )}
      {showCodHint && (
        <div className="mb-4">
          <Alert tone="info" title="Đơn COD — thu tiền khi giao">
            Có thể chuẩn bị và giao khi chưa thu. Ghi nhận thanh toán đủ trước khi xác nhận Đã giao.
          </Alert>
        </div>
      )}
      <OrderProgress state={order.state} />

      <div className="grid items-start gap-4 lg:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-4">
          <Card title="Dòng hàng" flush>
            <DataTable
              columns={lineColumns}
              rows={order.lines}
              rowKey={(l) => l.id}
              footer={
                <>
                  {[['Tạm tính', order.subtotal], ['Chiết khấu', order.discountTotal ? -order.discountTotal : 0], ['Phí giao', order.shippingFee]].map(([label, value]) => (
                    <tr key={label} className="border-t border-line text-ink-muted"><td colSpan={7} className="px-3 py-1.5 text-right"><span className="mr-3">{label}</span><span className="num">{money(value as number)}</span></td></tr>
                  ))}
                  <tr className="border-t border-line text-h2"><td colSpan={7} className="px-3 py-2 text-right"><span className="mr-3">Tổng cộng</span><span className="tabular-nums">{money(order.total)}</span></td></tr>
                </>
              }
            />
          </Card>
          <Card title="Giao hàng" flush>
            <DataTable columns={deliveryColumns} rows={order.deliveries} rowKey={(d) => d.id} emptyText="Chưa tạo giao hàng (một lần, trọn đơn)" />
          </Card>
          <Card title="Thanh toán / hoàn tiền" flush actions={<span className="text-small text-ink-muted">Đã thu <span className="num text-ink">{money(order.paidAmount)}</span> / {money(order.total)}</span>}>
            {order.payments.length === 0 ? (
              <p className="p-4 text-center text-ink-muted">Chưa có giao dịch thanh toán</p>
            ) : (
              <ul>
                {order.payments.map((p) => (
                  <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-4 py-2 first:border-t-0">
                    <span className="min-w-0">
                      <span className="num">{p.code}</span> · {paymentMethodLabels[p.method]}
                      {p.paidAt ? ` · ${dateTime(p.paidAt)}` : ''}
                      {p.note ? <span className="mt-0.5 block text-small text-ink-faint">{p.note}</span> : null}
                    </span>
                    <span className="flex flex-wrap items-center gap-2">
                      <StatusChip meta={payTxnStatusMeta} value={p.status} />
                      <Chip tone={p.direction === 'in' ? 'green' : 'amber'}>{payDirectionLabels[p.direction]}</Chip>
                      <span className="num font-medium">{money(p.amount)}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </Card>
          <Card title="Phiếu trả hàng" flush>
            <DataTable
              columns={returnColumns}
              rows={order.returns ?? []}
              rowKey={(r) => r.id}
              emptyText="Chưa có phiếu trả hàng"
            />
          </Card>
        </div>

        <aside className="space-y-4">
          <Card title="Khách hàng" actions={order.customer && <button type="button" className="text-small text-primary hover:underline" onClick={() => navigate(`/customers/${order.customer!.id}`)}>Xem hồ sơ</button>}>
            <InfoList items={[['Tên', order.customerName], ['Điện thoại', order.customer?.phone], ['Kênh', order.channel ? saleChannelLabels[order.channel] : null], ['Thanh toán', paymentMethodLabels[order.paymentMethod]], ...(order.paymentTermDays ? ([['Kỳ hạn', `NET ${order.paymentTermDays} ngày`]] as [string, string][]) : [])]} />
            {order.customer?.customerType === 'b2b' && (
              <div className="mt-3 border-t border-line pt-3">
                <div className="mb-1.5 flex justify-between text-small"><span className="text-ink-muted">Nợ / hạn mức</span><span className="num">{money(order.customer.currentDebt)} / {money(order.customer.creditLimit)}</span></div>
                <Meter value={order.customer.currentDebt} max={order.customer.creditLimit} label="Tỷ lệ sử dụng hạn mức" />
              </div>
            )}
          </Card>
          <Card title="Giao nhận">
            <InfoList items={[['Hình thức', fulfillmentLabels[order.fulfillmentMethod]], ['Khoảng cách', order.address?.distanceKm != null ? `${order.address.distanceKm} km` : null]]} />
            {order.address && <p className="mt-2 border-t border-line pt-2 text-ink-muted">{[order.address.line1, order.address.ward, order.address.province || order.address.district].filter(Boolean).join(', ')}</p>}
          </Card>
          {order.note && <Card title="Ghi chú"><p className="text-ink-muted">{order.note}</p></Card>}
          <Card title="Dòng thời gian">
            {(order.events ?? []).length === 0 ? (
              <p className="text-ink-muted">Chưa có sự kiện</p>
            ) : (
              <ol className="space-y-3">
                {order.events.map((e, i) => (
                  <li key={e.id} className="relative pl-5">
                    <span className={`absolute left-0 top-1 h-2 w-2 rounded-full ${i === 0 ? 'bg-primary' : 'bg-field'}`} aria-hidden />
                    {i < order.events.length - 1 && <span className="absolute left-[3px] top-3 h-full w-px bg-line" aria-hidden />}
                    <div>{e.description}</div>
                    <div className="text-small text-ink-faint">{dateTime(e.createdAt)}{e.actorName ? ` · ${e.actorName}` : ''}</div>
                  </li>
                ))}
              </ol>
            )}
          </Card>
          <Card title="Ai sửa lúc nào" flush>
            <DataTable
              columns={auditColumns}
              rows={order.auditTrail ?? []}
              rowKey={(a) => a.id}
              emptyText="Chưa có nhật ký chỉnh sửa"
            />
          </Card>
        </aside>
      </div>

      <ConfirmDialog open={cancelling} danger title={`Hủy đơn ${order.code}?`} message={order.paymentStatus === 'paid' || order.paymentStatus === 'partial' ? 'Hàng đang giữ sẽ được nhả. Đơn đã thu tiền sẽ chuyển sang Chờ hoàn tiền.' : 'Hàng đang giữ sẽ được nhả. Lý do hủy là bắt buộc.'} withNote noteRequired noteLabel="Lý do hủy" confirmLabel="Hủy đơn" loading={cancel.isPending} onConfirm={(reason) => cancel.mutate(reason)} onClose={() => setCancelling(false)} />
      <ConfirmDialog
        open={deciding != null}
        danger={deciding === 'rejected'}
        title={deciding === 'approved' ? `Duyệt đơn ${order.code}?` : `Từ chối đơn ${order.code}?`}
        message={deciding === 'approved' ? 'Đơn vượt hạn mức sẽ được duyệt và giữ hàng (nếu còn đủ).' : 'Đơn sẽ bị từ chối / hủy theo quy trình phê duyệt.'}
        withNote
        noteLabel="Ghi chú"
        confirmLabel={deciding === 'approved' ? 'Duyệt' : 'Từ chối'}
        loading={decideApproval.isPending}
        onConfirm={(note) => decideApproval.mutate(note)}
        onClose={() => setDeciding(null)}
      />
      <DeliveryFormModal open={deliveryOpen} onClose={() => setDeliveryOpen(false)} order={{ id: order.id, code: order.code, fulfillmentMethod: order.fulfillmentMethod, shipAddressId: order.shipAddressId, inRadius: order.address?.inDeliveryRadius ?? false, distanceKm: order.address?.distanceKm ?? null, legCount: order.deliveries.filter((d) => d.state !== 'failed').length, lines: order.lines.map((l) => ({ id: l.id, label: `${l.productName} · ${l.packLabel}`, qtyKhay: l.qtyKhay })), deliveries: order.deliveries }} />
      <PaymentFormModal
        open={paymentOpen}
        onClose={() => setPaymentOpen(false)}
        customerId={order.customerId}
        customerName={order.customerName}
        order={{ id: order.id, code: order.code, outstanding }}
        defaultMethod={collectOnDelivery || order.paymentMethod === 'cash' ? 'cash' : order.paymentMethod === 'bank_transfer' ? 'bank_transfer' : 'cash'}
      />
      <ReceiptDialog receipt={receipt} onClose={() => setReceipt(null)} />
      <PickingSlipDialog
        open={pickingOpen}
        slip={pickingSlip}
        loading={printPicking.isPending}
        error={printPicking.error}
        onClose={() => {
          setPickingOpen(false);
          setPickingSlip(null);
        }}
        onRetry={() => printPicking.mutate()}
      />
    </>
  );
}
