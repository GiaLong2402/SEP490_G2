import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { Delivery, ID } from '@/shared/domain/entities';
import { fulfillmentLabels, type FulfillmentMethod } from '@/shared/domain/enums';
import { num, todayISO } from '@/shared/lib/format';
import { Button, Field, Input, Modal, Select } from '@/shared/ui';
import { deliveriesApi } from '../api';
import { useCouriers } from '../hooks';
import { DELIVERY_TIME_WINDOWS } from '../timeWindows';

export interface DeliveryOrderContext {
  id: ID;
  code: string;
  fulfillmentMethod: FulfillmentMethod;
  shipAddressId: ID | null;
  inRadius: boolean;
  distanceKm: number | null;
  legCount: number;
  lines: { id: ID; label: string; qtyKhay: number }[];
  deliveries: Pick<Delivery, 'items' | 'state'>[];
}

/** Một đơn = một lần giao trọn; hình thức lấy từ đơn (không cho đổi). */
export function DeliveryFormModal({ open, order, onClose }: { open: boolean; order: DeliveryOrderContext; onClose: () => void }) {
  const couriers = useCouriers();
  const method = order.fulfillmentMethod;
  const remaining = (lineId: ID, qtyKhay: number) =>
    qtyKhay
    - order.deliveries
      .filter((d) => d.state !== 'failed')
      .flatMap((d) => d.items ?? [])
      .filter((i) => i.orderLineId === lineId)
      .reduce((sum, i) => sum + i.qtyKhay, 0);

  const [scheduledDate, setScheduledDate] = useState(todayISO());
  const [timeWindow, setTimeWindow] = useState('');
  const [assignedTo, setAssignedTo] = useState('');
  const [carrier, setCarrier] = useState('');
  const [touched, setTouched] = useState(false);

  const activeLegs = order.deliveries.filter((d) => d.state !== 'failed').length;
  const alreadyScheduled = activeLegs > 0;
  const items = order.lines.map((l) => ({ orderLineId: l.id, qtyKhay: remaining(l.id, l.qtyKhay) }));
  const totalQty = items.reduce((sum, i) => sum + i.qtyKhay, 0);
  const qtyValid = !alreadyScheduled && totalQty > 0 && items.every((i) => Number.isInteger(i.qtyKhay) && i.qtyKhay >= 0);
  const legLabel = 'Trọn đơn';

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setScheduledDate(todayISO());
    setTimeWindow('');
    setAssignedTo('');
    setCarrier('');
  }, [open]);

  const create = useAction(
    () =>
      deliveriesApi.create({
        orderId: order.id,
        legLabel,
        scheduledDate,
        timeWindow: timeWindow || null,
        fulfillmentMethod: method,
        addressId: method === 'pickup' ? null : order.shipAddressId,
        inRadius: order.inRadius,
        distanceKm: order.distanceKm,
        assignedTo: method === 'shop_delivery' && assignedTo ? Number(assignedTo) : null,
        carrier: method === 'partner_delivery' ? carrier || null : null,
        items: items.filter((i) => i.qtyKhay > 0),
        note: null,
      }),
    { success: (d) => `Đã tạo giao hàng ${d.legLabel} cho đơn ${order.code}`, onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={`Tạo giao hàng · ${order.code}`}
      width="lg"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (qtyValid && scheduledDate) create.mutate();
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={create.isPending} disabled={alreadyScheduled}>
            Tạo giao hàng
          </Button>
        </>
      }
    >
      {alreadyScheduled ? (
        <p className="text-body text-ink-muted">Đơn đã có chuyến giao. Mỗi đơn chỉ giao một lần (trọn đơn).</p>
      ) : (
        <>
          <div className="grid gap-3 sm:grid-cols-3">
            <Field label="Hình thức">
              <p className="flex h-[34px] items-center text-body text-ink">{fulfillmentLabels[method]}</p>
            </Field>
            <Field label="Ngày giao" required error={touched && !scheduledDate ? 'Vui lòng chọn ngày' : undefined}>
              <Input type="date" min={todayISO()} value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} />
            </Field>
            <Field label="Khung giờ">
              <Select value={timeWindow} onChange={(e) => setTimeWindow(e.target.value)}>
                <option value="">Chưa chọn</option>
                {DELIVERY_TIME_WINDOWS.map((w) => <option key={w} value={w}>{w}</option>)}
              </Select>
            </Field>
            {method === 'shop_delivery' && (
              <Field label="Nhân viên giao" className="sm:col-span-3">
                <Select value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)}>
                  <option value="">Chưa phân công</option>
                  {couriers.data?.map((c) => (
                    <option key={c.id} value={c.id}>{c.fullName}</option>
                  ))}
                </Select>
              </Field>
            )}
            {method === 'partner_delivery' && (
              <Field label="Đối tác giao" className="sm:col-span-3">
                <Input value={carrier} onChange={(e) => setCarrier(e.target.value)} />
              </Field>
            )}
          </div>
          <h3 className="mb-2 mt-4 text-h2">Số khay giao (trọn đơn)</h3>
          <p className="mb-2 text-small text-ink-muted">Giao đủ toàn bộ khay còn lại trong một lần — không tách đợt.</p>
          <ul className="space-y-2">
            {order.lines.map((line) => {
              const left = remaining(line.id, line.qtyKhay);
              return (
                <li key={line.id} className="flex items-center gap-3">
                  <span className="min-w-0 flex-1">
                    {line.label}{' '}
                    <span className="text-small text-ink-faint">{num(left)} / {num(line.qtyKhay)} khay</span>
                  </span>
                  <span className="num w-24 text-right font-medium">{num(left)}</span>
                </li>
              );
            })}
          </ul>
          {touched && !qtyValid && (
            <p className="mt-2 text-small text-red-fg">Không còn khay để giao hoặc đơn đã có chuyến giao.</p>
          )}
        </>
      )}
    </Modal>
  );
}
