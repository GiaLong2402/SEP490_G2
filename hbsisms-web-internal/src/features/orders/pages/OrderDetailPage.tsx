import { useState } from 'react';
import { CheckCircle2, CreditCard, Pencil, Printer, RotateCcw, Truck, XCircle } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/features/auth';
import { DeliveryFormModal, DeliveryStateActions } from '@/features/deliveries';
import { PaymentFormModal } from '@/features/payments';
import { ReceiptDialog } from '@/features/pos/components/ReceiptDialog';
import type { Receipt } from '@/features/pos/types';
import { useAction } from '@/shared/api/useAction';
import { allocationStatusMeta, deliveryStateMeta, fulfillmentLabels, orderStateMeta, payDirectionLabels, paymentMethodLabels, paymentStatusMeta, priceBasisMeta, saleChannelLabels, sellUnitLabels, type OrderState } from '@/shared/domain/enums';
import { date, dateTime, money, num } from '@/shared/lib/format';
import { Alert, Button, Card, Chip, ConfirmDialog, DataTable, ErrorState, InfoList, LinkButton, Meter, PageHeader, PageLoading, StatusChip, type Column } from '@/shared/ui';
import { ordersApi } from '../api';
import { OrderProgress } from '../components/OrderProgress';
import { useOrder } from '../hooks';
import type { OrderDeliveryView, OrderLineView } from '../types';

const NEXT_LABEL: Partial<Record<OrderState, string>> = { confirmed: 'Xác nhận đơn', preparing: 'Bắt đầu chuẩn bị', to_ship: 'Sẵn sàng giao', completed: 'Hoàn tất đơn' };

export default function OrderDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { can } = useAuth();
  const { data: order, isLoading, error, refetch } = useOrder(id);
  const [cancelling, setCancelling] = useState(false);
  const [deliveryOpen, setDeliveryOpen] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [receipt, setReceipt] = useState<Receipt | null>(null);

  const transition = useAction((state: OrderState) => ordersApi.transition(id, state), { success: 'Đã cập nhật trạng thái đơn' });
  const cancel = useAction((reason: string) => ordersApi.cancel(id, reason), { success: 'Đã hủy đơn, hàng giữ đã được nhả', onDone: () => setCancelling(false) });
  const completeCounter = useAction(() => ordersApi.completeAtCounter(id), { success: 'Đã hoàn tất bàn giao tại quầy' });
  const printReceipt = useAction(() => ordersApi.receipt(id), { success: 'Đã tải biên lai', onDone: (r) => setReceipt(r) });

  if (isLoading) return <PageLoading />;
  if (error || !order) return <ErrorState error={error} onRetry={refetch} />;

  const orderedKhay = order.lines.reduce((s, l) => s + l.qtyKhay, 0);
  const scheduledKhay = order.deliveries.filter((d) => d.state !== 'failed').reduce((s, d) => s + (d.items ?? []).reduce((a, i) => a + i.qtyKhay, 0), 0);
  const canDeliver = ['to_ship', 'partial'].includes(order.state) && scheduledKhay < orderedKhay;
  const outstanding = order.total - order.paidAmount;
  // Đơn công nợ chỉ phát sinh nợ sau khi xác nhận, nên chưa thể thu trước đó.
  const canPay = can('debt') && order.state !== 'cancelled' && outstanding > 0 && !(order.paymentMethod === 'credit_net' && order.state === 'pending');
  const canReturn = ['delivered', 'completed', 'partial'].includes(order.state);
  const canCompleteCounter =
    order.fulfillmentMethod === 'pickup'
    && order.state !== 'completed'
    && order.state !== 'cancelled'
    && order.state !== 'pending'
    && order.paymentStatus === 'paid';
  const canEdit = ['pending', 'confirmed'].includes(order.state) && !['paid', 'partial'].includes(order.paymentStatus);
  const canPrintReceipt = order.paymentStatus === 'paid';
  const waitingForPrepare = order.state === 'confirmed' && !order.nextStates.includes('preparing');
  const prepareBlockReason =
    order.paymentStatus !== 'paid'
      ? 'Cần thanh toán đủ trước khi chuẩn bị hàng.'
      : order.allocationStatus !== 'full'
        ? 'Chưa phân bổ đủ hàng — không thể chuẩn bị.'
        : null;

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
    { key: 'leg', header: 'Đợt', cell: (d) => <div><div className="font-medium">{d.legLabel}</div><div className="num text-ink-faint">{d.code}</div></div> },
    { key: 'when', header: 'Lịch giao', cell: (d) => <span className="whitespace-nowrap">{date(d.scheduledDate)}{d.timeWindow ? ` · ${d.timeWindow}` : ''}</span> },
    { key: 'who', header: 'Người giao', cell: (d) => d.assigneeName ?? d.carrier ?? fulfillmentLabels[d.fulfillmentMethod] },
    { key: 'qty', header: 'Khay', align: 'right', cell: (d) => <span className="num">{num((d.items ?? []).reduce((s, i) => s + i.qtyKhay, 0))}</span> },
    { key: 'state', header: 'Trạng thái', cell: (d) => <StatusChip meta={deliveryStateMeta} value={d.state} /> },
    { key: 'actions', header: '', align: 'right', cell: (d) => <DeliveryStateActions delivery={d} orderState={order.state} /> },
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
            {order.canCancel && <Button variant="danger" icon={<XCircle size={14} />} onClick={() => setCancelling(true)}>Hủy đơn</Button>}
            {canReturn && <LinkButton to={`/returns?orderId=${order.id}`} icon={<RotateCcw size={14} />}>Trả hàng</LinkButton>}
            {canPay && <Button icon={<CreditCard size={14} />} onClick={() => setPaymentOpen(true)}>Ghi nhận thanh toán</Button>}
            {canDeliver && <Button variant="primary" icon={<Truck size={14} />} onClick={() => setDeliveryOpen(true)}>Tạo đợt giao</Button>}
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
      {order.isOverLimit && !order.approvedBy && order.state !== 'cancelled' && (
        <div className="mb-4">
          <Alert tone="danger" title={`Vượt hạn mức ${money(order.overLimitAmount)}, đơn đang chờ duyệt`}>
            Chưa giữ hàng cho đến khi được duyệt. {can('approve') && <Link to="/approvals" className="font-semibold underline">Đi tới Phê duyệt</Link>}
          </Alert>
        </div>
      )}
      {waitingForPrepare && prepareBlockReason && (
        <div className="mb-4">
          <Alert tone="warning" title="Chưa thể chuyển sang Chuẩn bị">
            {prepareBlockReason} {order.paymentStatus !== 'paid' && canPay && 'Hãy ghi nhận thanh toán đủ trước.'}
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
          <Card title="Các đợt giao" flush>
            <DataTable columns={deliveryColumns} rows={order.deliveries} rowKey={(d) => d.id} emptyText="Chưa có đợt giao nào" />
          </Card>
          <Card title="Thanh toán" flush actions={<span className="text-small text-ink-muted">Đã thu <span className="num text-ink">{money(order.paidAmount)}</span> / {money(order.total)}</span>}>
            {order.payments.length === 0 ? (
              <p className="p-4 text-center text-ink-muted">Chưa có giao dịch thanh toán</p>
            ) : (
              <ul>
                {order.payments.map((p) => (
                  <li key={p.id} className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-4 py-2 first:border-t-0">
                    <span><span className="num">{p.code}</span> · {paymentMethodLabels[p.method]} · {dateTime(p.paidAt)}</span>
                    <span className="flex items-center gap-2">
                      <Chip tone={p.direction === 'in' ? 'green' : 'amber'}>{payDirectionLabels[p.direction]}</Chip>
                      <span className="num font-medium">{money(p.amount)}</span>
                    </span>
                  </li>
                ))}
              </ul>
            )}
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
          </Card>
        </aside>
      </div>

      <ConfirmDialog open={cancelling} danger title={`Hủy đơn ${order.code}?`} message={order.paymentStatus === 'paid' || order.paymentStatus === 'partial' ? 'Hàng đang giữ sẽ được nhả. Đơn đã thu tiền sẽ chuyển sang Chờ hoàn tiền.' : 'Hàng đang giữ sẽ được nhả. Lý do hủy là bắt buộc.'} withNote noteRequired noteLabel="Lý do hủy" confirmLabel="Hủy đơn" loading={cancel.isPending} onConfirm={(reason) => cancel.mutate(reason)} onClose={() => setCancelling(false)} />
      <DeliveryFormModal open={deliveryOpen} onClose={() => setDeliveryOpen(false)} order={{ id: order.id, code: order.code, fulfillmentMethod: order.fulfillmentMethod, shipAddressId: order.shipAddressId, inRadius: order.address?.inDeliveryRadius ?? false, distanceKm: order.address?.distanceKm ?? null, legCount: order.deliveries.filter((d) => d.state !== 'failed').length, lines: order.lines.map((l) => ({ id: l.id, label: `${l.productName} · ${l.packLabel}`, qtyKhay: l.qtyKhay })), deliveries: order.deliveries }} />
      <PaymentFormModal open={paymentOpen} onClose={() => setPaymentOpen(false)} customerId={order.customerId} customerName={order.customerName} order={{ id: order.id, code: order.code, outstanding }} defaultMethod={order.paymentMethod === 'cash' || order.paymentMethod === 'bank_transfer' ? order.paymentMethod : 'bank_transfer'} />
      <ReceiptDialog receipt={receipt} onClose={() => setReceipt(null)} />
    </>
  );
}
