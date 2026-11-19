import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PaymentFormModal } from '@/features/payments';
import { returnsApi } from '@/features/returns/api';
import { useAction } from '@/shared/api/useAction';
import type { Delivery, ID } from '@/shared/domain/entities';
import type { DeliveryState, FulfillmentMethod, OrderState, PaymentMethod, PaymentStatus } from '@/shared/domain/enums';
import { Button, ConfirmDialog, LinkButton } from '@/shared/ui';
import { isCollectOnDelivery } from '@/features/orders/cod';
import { deliveriesApi } from '../api';

const SHIPPING_ORDER_STATES: OrderState[] = ['to_ship', 'partial'];
const BEFORE_SHIP_STATES: OrderState[] = ['pending', 'confirmed', 'preparing'];
/** Nút gọn cho dòng bảng (cao bằng ô chọn người giao). */
const ROW_BTN = '!h-7 !px-2.5 text-small';

type CodCollectContext = {
  paymentMethod: PaymentMethod;
  fulfillmentMethod: FulfillmentMethod;
  paymentStatus: PaymentStatus;
  outstanding: number;
  customerId: ID | null;
  customerName: string | null;
  orderId: ID;
  orderCode: string;
};

/** Nút chuyển trạng thái giao hàng, dùng ở cả danh sách giao hàng và chi tiết đơn. */
export function DeliveryStateActions({
  delivery,
  orderState,
  codCollect,
}: {
  delivery: Pick<Delivery, 'id' | 'orderId' | 'state' | 'legLabel' | 'fulfillmentMethod'>;
  /** Khi truyền: chỉ hiện nút nếu đơn đã Chờ giao / Giao một phần. */
  orderState?: OrderState | null;
  /** Khi có: COD chưa thu → mở ghi nhận TT trước khi Đã giao. */
  codCollect?: CodCollectContext | null;
}) {
  const [failing, setFailing] = useState(false);
  const [paymentOpen, setPaymentOpen] = useState(false);
  const update = useAction((body: { state: DeliveryState; note?: string }) => deliveriesApi.update(delivery.id, body), {
    success: 'Đã cập nhật giao hàng',
    onDone: () => {
      setFailing(false);
      setPaymentOpen(false);
    },
  });

  const receiveHref = `/returns?orderId=${delivery.orderId}&deliveryId=${delivery.id}`;
  const failed = delivery.state === 'failed';
  const { data: returnable, isLoading: returnableLoading } = useQuery({
    queryKey: ['returnable-delivery', delivery.id],
    queryFn: () => returnsApi.returnableOrders(undefined, delivery.id),
    enabled: failed,
    staleTime: 15_000,
  });
  const canCreateReceiveBack = (returnable?.length ?? 0) > 0;

  /** Hết SL tạo phiếu → xem còn phiếu Chờ nhận không; đã nhận/hoàn tiền thì ẩn nút. */
  const { data: pendingReceive, isLoading: pendingLoading } = useQuery({
    queryKey: ['pending-receive-delivery', delivery.id],
    queryFn: async () => {
      const page = await returnsApi.list({ page: 1, pageSize: 50, status: 'pending' });
      return page.items.find((r) => r.deliveryId === delivery.id) ?? null;
    },
    enabled: failed && !returnableLoading && !canCreateReceiveBack,
    staleTime: 15_000,
  });

  if (delivery.state === 'delivered') return null;

  // Task 29: thất bại → Nhận về (tạo) / Kiểm (chờ nhận) / ẩn khi đã xong.
  if (failed) {
    const loading = returnableLoading || (!canCreateReceiveBack && pendingLoading);
    if (loading) {
      return <span className="whitespace-nowrap text-small text-ink-faint">…</span>;
    }
    if (canCreateReceiveBack) {
      return (
        <div className="flex justify-end gap-1.5">
          <LinkButton className={ROW_BTN} to={receiveHref} variant="primary" title="Tạo phiếu nhận hàng về">
            Nhận về
          </LinkButton>
        </div>
      );
    }
    if (pendingReceive) {
      return (
        <div className="flex justify-end gap-1.5">
          <LinkButton
            className={ROW_BTN}
            to={receiveHref}
            variant="primary"
            title={`Kiểm / nhận hàng phiếu ${pendingReceive.code}`}
          >
            Kiểm nhận về
          </LinkButton>
        </div>
      );
    }
    return <span className="whitespace-nowrap text-small text-ink-faint">Đã nhận về</span>;
  }

  if (orderState != null && !SHIPPING_ORDER_STATES.includes(orderState)) {
    if (!BEFORE_SHIP_STATES.includes(orderState)) return null;
    return <span className="whitespace-nowrap text-small text-ink-faint">Chờ đơn sang Chờ giao</span>;
  }
  // Khách lấy tại quầy: không có bước "Bắt đầu giao".
  const pickup = delivery.fulfillmentMethod === 'pickup';

  const needsCodPayment =
    !!codCollect
    && isCollectOnDelivery(codCollect.paymentMethod, codCollect.fulfillmentMethod)
    && codCollect.paymentStatus !== 'paid'
    && codCollect.outstanding > 0;

  const markDelivered = () => {
    if (needsCodPayment) {
      setPaymentOpen(true);
      return;
    }
    update.mutate({ state: 'delivered' });
  };

  return (
    <div className="flex justify-end gap-1.5">
      {delivery.state === 'ready' && !pickup && <Button className={ROW_BTN} loading={update.isPending} onClick={() => update.mutate({ state: 'going' })}>Bắt đầu giao</Button>}
      {(delivery.state === 'going' || (pickup && delivery.state === 'ready')) && (
        <>
          <Button variant="ghost" className={ROW_BTN} onClick={() => setFailing(true)}>{pickup ? 'Không đến' : 'Thất bại'}</Button>
          <Button variant="primary" className={ROW_BTN} loading={update.isPending} onClick={markDelivered}>{pickup ? 'Đã nhận' : 'Đã giao'}</Button>
        </>
      )}
      <ConfirmDialog open={failing} danger title={`${pickup ? 'Khách không đến lấy' : 'Giao thất bại'}: ${delivery.legLabel}`} withNote noteRequired noteLabel="Lý do" confirmLabel="Xác nhận thất bại" loading={update.isPending} onConfirm={(note) => update.mutate({ state: 'failed', note })} onClose={() => setFailing(false)} />
      {codCollect && (
        <PaymentFormModal
          open={paymentOpen}
          onClose={() => setPaymentOpen(false)}
          customerId={codCollect.customerId}
          customerName={codCollect.customerName}
          order={{ id: codCollect.orderId, code: codCollect.orderCode, outstanding: codCollect.outstanding }}
          defaultMethod="cash"
          onSuccess={() => update.mutate({ state: 'delivered' })}
        />
      )}
    </div>
  );
}
