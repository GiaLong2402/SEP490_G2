import { useState } from 'react';
import { PaymentFormModal } from '@/features/payments';
import { useAction } from '@/shared/api/useAction';
import type { Delivery, ID } from '@/shared/domain/entities';
import type { DeliveryState, FulfillmentMethod, OrderState, PaymentMethod, PaymentStatus } from '@/shared/domain/enums';
import { Button, ConfirmDialog } from '@/shared/ui';
import { isCollectOnDelivery } from '@/features/orders/cod';
import { deliveriesApi } from '../api';

const SHIPPING_ORDER_STATES: OrderState[] = ['to_ship', 'partial'];

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
  delivery: Pick<Delivery, 'id' | 'state' | 'legLabel'>;
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

  if (delivery.state === 'delivered' || delivery.state === 'failed') return null;
  if (orderState != null && !SHIPPING_ORDER_STATES.includes(orderState)) return null;

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
      {delivery.state === 'ready' && <Button loading={update.isPending} onClick={() => update.mutate({ state: 'going' })}>Bắt đầu giao</Button>}
      {delivery.state === 'going' && (
        <>
          <Button variant="ghost" onClick={() => setFailing(true)}>Thất bại</Button>
          <Button variant="primary" loading={update.isPending} onClick={markDelivered}>Đã giao</Button>
        </>
      )}
      <ConfirmDialog open={failing} danger title={`Giao thất bại: ${delivery.legLabel}`} withNote noteRequired noteLabel="Lý do" confirmLabel="Xác nhận thất bại" loading={update.isPending} onConfirm={(note) => update.mutate({ state: 'failed', note })} onClose={() => setFailing(false)} />
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
