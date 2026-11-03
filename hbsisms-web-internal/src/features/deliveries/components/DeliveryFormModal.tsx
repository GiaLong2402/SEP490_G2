import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { Delivery, ID } from '@/shared/domain/entities';
import { fulfillmentLabels, type FulfillmentMethod } from '@/shared/domain/enums';
import { num, todayISO } from '@/shared/lib/format';
import { Button, Field, Input, Modal, Select, enumOptions } from '@/shared/ui';
import { deliveriesApi } from '../api';
import { useCouriers } from '../hooks';

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

export function DeliveryFormModal({ open, order, onClose }: { open: boolean; order: DeliveryOrderContext; onClose: () => void }) {
  const couriers = useCouriers();
  const remaining = (lineId: ID, qtyKhay: number) => qtyKhay - order.deliveries.filter((d) => d.state !== 'failed').flatMap((d) => d.items ?? []).filter((i) => i.orderLineId === lineId).reduce((sum, i) => sum + i.qtyKhay, 0);

  const [method, setMethod] = useState<FulfillmentMethod>(order.fulfillmentMethod);
  const [scheduledDate, setScheduledDate] = useState(todayISO());
  const [timeWindow, setTimeWindow] = useState('08:00 – 11:00');
  const [assignedTo, setAssignedTo] = useState('');
  const [carrier, setCarrier] = useState('');
  const [quantities, setQuantities] = useState<Record<ID, string>>({});
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setMethod(order.fulfillmentMethod);
    setScheduledDate(todayISO());
    setAssignedTo('');
    setCarrier('');
    setQuantities(Object.fromEntries(order.lines.map((l) => [l.id, String(remaining(l.id, l.qtyKhay))])));
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const items = order.lines.map((l) => ({ orderLineId: l.id, qtyKhay: Number(quantities[l.id] ?? 0) }));
  const totalQty = items.reduce((sum, i) => sum + i.qtyKhay, 0);
  const qtyValid = totalQty > 0 && order.lines.every((l) => {
    const qty = Number(quantities[l.id] ?? 0);
    return Number.isInteger(qty) && qty >= 0 && qty <= remaining(l.id, l.qtyKhay);
  });
  const fullyCovered = order.lines.every((l) => Number(quantities[l.id] ?? 0) === remaining(l.id, l.qtyKhay));
  const legLabel = order.legCount === 0 && fullyCovered ? 'Trọn đơn' : `Đợt ${order.legCount + 1}`;

  const create = useAction(
    () =>
      deliveriesApi.create({
        orderId: order.id, legLabel, scheduledDate, timeWindow: timeWindow || null, fulfillmentMethod: method, addressId: method === 'pickup' ? null : order.shipAddressId, inRadius: order.inRadius, distanceKm: order.distanceKm,
        assignedTo: method === 'shop_delivery' && assignedTo ? Number(assignedTo) : null, carrier: method === 'partner_delivery' ? carrier || null : null, items, note: null,
      }),
    { success: (d) => `Đã tạo ${d.legLabel} cho đơn ${order.code}`, onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={`Tạo đợt giao · ${order.code}`}
      width="lg"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (qtyValid && scheduledDate) create.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Tạo {legLabel.toLowerCase()}</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="Hình thức" required><Select value={method} onChange={(e) => setMethod(e.target.value as FulfillmentMethod)}>{enumOptions(fulfillmentLabels)}</Select></Field>
        <Field label="Ngày giao" required error={touched && !scheduledDate ? 'Vui lòng chọn ngày' : undefined}><Input type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} /></Field>
        <Field label="Khung giờ"><Input value={timeWindow} onChange={(e) => setTimeWindow(e.target.value)} /></Field>
        {method === 'shop_delivery' && (
          <Field label="Nhân viên giao" className="sm:col-span-3">
            <Select value={assignedTo} onChange={(e) => setAssignedTo(e.target.value)}>
              <option value="">Chưa phân công</option>
              {couriers.data?.map((c) => <option key={c.id} value={c.id}>{c.fullName}</option>)}
            </Select>
          </Field>
        )}
        {method === 'partner_delivery' && <Field label="Đối tác giao" className="sm:col-span-3"><Input value={carrier} onChange={(e) => setCarrier(e.target.value)} /></Field>}
      </div>
      <h3 className="mb-2 mt-4 text-h2">Số khay của đợt này</h3>
      <ul className="space-y-2">
        {order.lines.map((line) => {
          const left = remaining(line.id, line.qtyKhay);
          return (
            <li key={line.id} className="flex items-center gap-3">
              <span className="min-w-0 flex-1">
                {line.label} <span className="text-small text-ink-faint">còn {num(left)} / {num(line.qtyKhay)} khay</span>
              </span>
              <Input aria-label={`Số khay ${line.label}`} type="number" min={0} max={left} className="!w-24 text-right" value={quantities[line.id] ?? ''} onChange={(e) => setQuantities({ ...quantities, [line.id]: e.target.value })} />
            </li>
          );
        })}
      </ul>
      {touched && !qtyValid && <p className="mt-2 text-small text-red-fg">Số khay phải là số nguyên, không vượt phần còn lại và tổng lớn hơn 0.</p>}
    </Modal>
  );
}
