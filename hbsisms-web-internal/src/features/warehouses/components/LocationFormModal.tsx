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
    setForm(location ? { code: location.code, zone: location.zone ?? '', capacity: location.capacity?.toString() ?? '', note: location.note ?? '' } : empty);
  }, [open, location]);

  const capacity = Number(form.capacity);
  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã vị trí' : undefined,
    capacity: form.capacity !== '' && !(Number.isInteger(capacity) && capacity > 0) ? 'Phải là số nguyên dương' : undefined,
  };
  const zones = [...new Set(warehouse?.locations.map((l) => l.zone).filter(Boolean))] as string[];

  const save = useAction(
    () => {
      const body = { code: form.code.trim(), zone: form.zone.trim() || null, capacity: form.capacity === '' ? null : capacity, note: form.note.trim() || null };
      return location ? warehousesApi.updateLocation(location.id, body) : warehousesApi.addLocation(warehouse!.id, body);
    },
    { success: 'Đã lưu vị trí', onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={location ? `Sửa vị trí ${location.code}` : `Thêm vị trí · ${warehouse?.code ?? ''}`}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!errors.code && !errors.capacity) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Mã vị trí" required error={touched ? errors.code : undefined}><Input placeholder="A-01" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
        <Field label="Khu" hint="Chọn khu có sẵn hoặc nhập khu mới"><Input list={zonesId} value={form.zone} onChange={(e) => setForm({ ...form, zone: e.target.value })} /></Field>
        <datalist id={zonesId}>{zones.map((z) => <option key={z} value={z} />)}</datalist>
        <Field label="Sức chứa (khay)" error={touched ? errors.capacity : undefined} hint={location ? `Đang chứa ${num(location.usedQty)} khay` : 'Bỏ trống nếu không giới hạn'}><Input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} /></Field>
        <Field label="Ghi chú"><Input value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></Field>
      </div>
    </Modal>
  );
}
