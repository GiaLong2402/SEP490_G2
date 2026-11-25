import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import type { WarehouseKind } from '@/shared/domain/entities';
import { Button, Checkbox, Field, Input, Modal, Select } from '@/shared/ui';
import { warehousesApi } from '../api';
import type { WarehouseDetail } from '../types';

const empty = { code: '', name: '', address: '', capacity: '', isActive: true, kind: 'finished' as WarehouseKind };

export function WarehouseFormModal({ open, warehouse, onClose }: { open: boolean; warehouse?: WarehouseDetail; onClose: () => void }) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(
      warehouse
        ? { code: warehouse.code, name: warehouse.name, address: warehouse.address ?? '', capacity: String(warehouse.capacity), isActive: warehouse.isActive, kind: warehouse.kind ?? 'finished' }
        : empty,
    );
  }, [open, warehouse]);

  const raw = form.kind === 'raw';
  /** Đổi loại chỉ khi kho chưa có vị trí (backend cũng chặn). */
  const kindLocked = !!warehouse && warehouse.locations.length > 0;
  // Kho nguyên liệu không tính sức chứa theo khay: giữ số cũ (hoặc 500) cho đúng ràng buộc cột.
  const capacity = raw ? warehouse?.capacity ?? 500 : Number(form.capacity);
  const minCapacity = warehouse ? Math.max(warehouse.totalQty, warehouse.allocatedCapacity) : 0;
  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã kho' : undefined,
    name: !form.name.trim() ? 'Vui lòng nhập tên kho' : undefined,
    capacity: !raw && (!form.capacity.trim() || !(Number.isInteger(capacity) && capacity > 0)) ? 'Sức chứa phải là số nguyên dương' : undefined,
  };
  const save = useAction(
    () => {
      const body = {
        code: form.code.trim(),
        name: form.name.trim(),
        address: form.address.trim() || null,
        capacity,
        isActive: form.isActive,
        kind: form.kind,
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
          label="Loại kho"
          className="sm:col-span-2"
          hint={kindLocked ? 'Kho đã có vị trí nên không đổi được loại.' : 'Kho thành phẩm chia theo vị trí và sức chứa (khay); kho nguyên liệu chia theo khu cất (Khu lạnh / Khu khô).'}
        >
          <Select aria-label="Loại kho" value={form.kind} disabled={kindLocked} onChange={(e) => setForm({ ...form, kind: e.target.value as WarehouseKind })}>
            <option value="finished">Kho thành phẩm</option>
            <option value="raw">Kho nguyên liệu</option>
          </Select>
        </Field>
        {!raw && (
        <Field
          label="Sức chứa (khay)"
          required
          className="sm:col-span-2"
          error={touched ? errors.capacity : undefined}
          hint={warehouse ? `Tối thiểu ${num(minCapacity)} (đang chứa ${num(warehouse.totalQty)}, đã phân bổ vị trí ${num(warehouse.allocatedCapacity)})` : 'Tổng sức chứa các vị trí sau này không được vượt số này'}
        >
          <Input type="number" min={1} value={form.capacity} onChange={(e) => setForm({ ...form, capacity: e.target.value })} />
        </Field>
        )}
        <div className="sm:col-span-2">
          <Checkbox label="Đang sử dụng" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
          {!form.isActive && <p className="mt-1 text-small text-ink-faint">Kho ngừng dùng sẽ không xuất hiện trong các ô chọn vị trí. Chỉ ngừng dùng được khi kho đã hết hàng.</p>}
        </div>
      </div>
    </Modal>
  );
}
