import { useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { Delivery } from '@/shared/domain/entities';
import type { DeliveryState, OrderState } from '@/shared/domain/enums';
import { Button, ConfirmDialog } from '@/shared/ui';
import { deliveriesApi } from '../api';

const SHIPPING_ORDER_STATES: OrderState[] = ['to_ship', 'partial'];

/** Nút chuyển trạng thái đợt giao, dùng ở cả danh sách giao hàng và chi tiết đơn. */
export function DeliveryStateActions({
  delivery,
  orderState,
}: {
  delivery: Pick<Delivery, 'id' | 'state' | 'legLabel'>;
  /** Khi truyền: chỉ hiện nút nếu đơn đã Chờ giao / Giao một phần. */
  orderState?: OrderState | null;
}) {
  const [failing, setFailing] = useState(false);
  const update = useAction((body: { state: DeliveryState; note?: string }) => deliveriesApi.update(delivery.id, body), { success: 'Đã cập nhật đợt giao', onDone: () => setFailing(false) });
  if (delivery.state === 'delivered' || delivery.state === 'failed') return null;
  if (orderState != null && !SHIPPING_ORDER_STATES.includes(orderState)) return null;
  return (
    <div className="flex justify-end gap-1.5">
      {delivery.state === 'ready' && <Button loading={update.isPending} onClick={() => update.mutate({ state: 'going' })}>Bắt đầu giao</Button>}
      {delivery.state === 'going' && (
        <>
          <Button variant="ghost" onClick={() => setFailing(true)}>Thất bại</Button>
          <Button variant="primary" loading={update.isPending} onClick={() => update.mutate({ state: 'delivered' })}>Đã giao</Button>
        </>
      )}
      <ConfirmDialog open={failing} danger title={`Giao thất bại: ${delivery.legLabel}`} withNote noteRequired noteLabel="Lý do" confirmLabel="Xác nhận thất bại" loading={update.isPending} onConfirm={(note) => update.mutate({ state: 'failed', note })} onClose={() => setFailing(false)} />
    </div>
  );
}
