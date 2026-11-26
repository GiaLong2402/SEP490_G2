import { useEffect, useId, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { Button, Field, Input, Modal } from '@/shared/ui';
import { warehousesApi } from '../api';
import type { WarehouseDetail, WarehouseLocation } from '../types';

const empty = { code: '', zone: '', capacity: '', note: '' };

export function LocationFormModal({ open, warehouse, location, onClose }: { open: boolean; warehouse?: WarehouseDetail; location?: WarehouseLocation; onClose: () => void }) {
  const zonesId = useId();
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(location ? { code: location.code, zone: location.zone ?? '', capacity: String(location.capacity), note: location.note ?? '' } : empty);
  }, [open, location]);

  /** Kho nguyên liệu: vị trí là khu cất (Khu lạnh / Khu khô), không tính sức chứa theo khay. */
  const raw = warehouse?.kind === 'raw';
  const capacity = raw ? location?.capacity ?? 1 : Number(form.capacity);
  const othersAllocated = (warehouse?.locations ?? [])
    .filter((l) => l.id !== location?.id)
    .reduce((sum, l) => sum + l.capacity, 0);
  const remaining = warehouse ? Math.max(0, warehouse.capacity - othersAllocated) : 0;
  const errors = {
    code: !form.code.trim() ? (raw ? 'Vui lòng nhập mã khu' : 'Vui lòng nhập mã vị trí') : undefined,
    zone: raw && !form.zone.trim() ? 'Vui lòng nhập tên khu' : undefined,
    capacity: !raw && (!form.capacity.trim() || !(Number.isInteger(capacity) && capacity > 0)) ? 'Sức chứa phải là số nguyên dương' : undefined,
  };
  const zones = [...new Set(warehouse?.locations.map((l) => l.zone).filter(Boolean))] as string[];

  const save = useAction(
    () => {
      const body = { code: form.code.trim(), zone: form.zone.trim() || null, capacity, note: form.note.trim() || null };
      return location ? warehousesApi.updateLocation(location.id, body) : warehousesApi.addLocation(warehouse!.id, body);
    },
    { success: raw ? 'Đã lưu khu' : 'Đã lưu vị trí', onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={location ? `Sửa ${raw ? 'khu' : 'vị trí'} ${location.code}` : `Thêm ${raw ? 'khu' : 'vị trí'} · ${warehouse?.code ?? ''}`}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!errors.code && !errors.zone && !errors.capacity) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      {raw ? (
        <div className="grid gap-3 sm:grid-cols-2">
          <Field
            label="Mã khu"
            required
            error={touched ? errors.code : undefined}
            hint="Khu lạnh: đặt mã bắt đầu bằng LANH (LANH, LANH-2…). Đồ khu lạnh dư sau sản xuất tách lô -TL; khu khác trả về lô gốc."
          >
            <Input placeholder="LANH" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
          </Field>
          <Field label="Tên khu" required error={touched ? errors.zone : undefined}><Input placeholder="Khu lạnh" value={form.zone} onChange={(e) => setForm({ ...form, zone: e.target.value })} /></Field>
          <Field label="Ghi chú" className="sm:col-span-2" hint="Ví dụ: đồ tươi sống, tủ đông 2…"><Input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>
        </div>
      ) : (
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Mã vị trí" required error={touched ? errors.code : undefined}><Input placeholder="A-01" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
        <Field label="Khu" hint="Chọn khu có sẵn hoặc nhập khu mới"><Input list={zonesId} value={form.zone} onChange={(e) => setForm({ ...form, zone: e.target.value })} /></Field>
        <datalist id={zonesId}>{zones.map((z) => <option key={z} value={z} />)}</datalist>
        <Field
          label="Sức chứa (khay)"
          required
          error={touched ? errors.capacity : undefined}
          hint={
            location
              ? `Đang chứa ${num(location.usedQty)} khay · còn phân bổ tối đa ${num(remaining)} (kho ${warehouse?.code ?? ''} = ${num(warehouse?.capacity ?? 0)})`
              : `Còn phân bổ tối đa ${num(remaining)} khay trong kho ${warehouse?.code ?? ''} (sức chứa ${num(warehouse?.capacity ?? 0)})`
          }
        >
          <Input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </Field>
        <Field label="Ghi chú"><Input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>
      </div>
      )}
    </Modal>
  );
}
