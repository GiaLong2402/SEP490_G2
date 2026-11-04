import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { Button, Checkbox, Field, Input, Modal } from '@/shared/ui';
import { warehousesApi } from '../api';
import type { WarehouseDetail } from '../types';

const empty = { code: '', name: '', address: '', capacity: '', isActive: true };

export function WarehouseFormModal({ open, warehouse, onClose }: { open: boolean; warehouse?: WarehouseDetail; onClose: () => void }) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(
      warehouse
        ? { code: warehouse.code, name: warehouse.name, address: warehouse.address ?? '', capacity: String(warehouse.capacity), isActive: warehouse.isActive }
        : empty,
    );
  }, [open, warehouse]);

  const capacity = Number(form.capacity);
  const minCapacity = warehouse ? Math.max(warehouse.totalQty, warehouse.allocatedCapacity) : 0;
  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã kho' : undefined,
    name: !form.name.trim() ? 'Vui lòng nhập tên kho' : undefined,
    capacity: !form.capacity.trim() || !(Number.isInteger(capacity) && capacity > 0) ? 'Sức chứa phải là số nguyên dương' : undefined,
  };
  const save = useAction(
    () => {
      const body = {
        code: form.code.trim(),
        name: form.name.trim(),
        address: form.address.trim() || null,
        capacity,
        isActive: form.isActive,
      };
      return warehouse ? warehousesApi.update(warehouse.id, body) : warehousesApi.create(body);
    },
    { success: (w) => `Đã lưu kho ${w.name}`, onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={warehouse ? `Sửa kho ${warehouse.code}` : 'Thêm kho'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!errors.code && !errors.name && !errors.capacity) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-[120px_1fr]">
        <Field label="Mã kho" required error={touched ? errors.code : undefined}><Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
        <Field label="Tên kho" required error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="Địa chỉ" className="sm:col-span-2"><Input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></Field>
        <Field
          label="Sức chứa (khay)"
          required
          className="sm:col-span-2"
          error={touched ? errors.capacity : undefined}
          hint={warehouse ? `Tối thiểu ${num(minCapacity)} (đang chứa ${num(warehouse.totalQty)}, đã phân bổ vị trí ${num(warehouse.allocatedCapacity)})` : 'Tổng sức chứa các vị trí sau này không được vượt số này'}
        >
          <Input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </Field>
        <div className="sm:col-span-2">
          <Checkbox label="Đang sử dụng" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
          {!form.isActive && <p className="mt-1 text-small text-ink-faint">Kho ngừng dùng sẽ không xuất hiện trong các ô chọn vị trí. Chỉ ngừng dùng được khi kho đã hết hàng.</p>}
        </div>
      </div>
    </Modal>
  );
}
