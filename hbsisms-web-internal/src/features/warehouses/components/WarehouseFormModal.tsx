import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal } from '@/shared/ui';
import { warehousesApi } from '../api';
import type { WarehouseDetail } from '../types';

const empty = { code: '', name: '', address: '', isActive: true };

export function WarehouseFormModal({ open, warehouse, onClose }: { open: boolean; warehouse?: WarehouseDetail; onClose: () => void }) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(warehouse ? { code: warehouse.code, name: warehouse.name, address: warehouse.address ?? '', isActive: warehouse.isActive } : empty);
  }, [open, warehouse]);

  const errors = { code: !form.code.trim() ? 'Vui lòng nhập mã kho' : undefined, name: !form.name.trim() ? 'Vui lòng nhập tên kho' : undefined };
  const save = useAction(
    () => {
      const body = { code: form.code.trim(), name: form.name.trim(), address: form.address.trim() || null, isActive: form.isActive };
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
        if (!errors.code && !errors.name) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-[120px_1fr]">
        <Field label="Mã kho" required error={touched ? errors.code : undefined}><Input value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
        <Field label="Tên kho" required error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="Địa chỉ" className="sm:col-span-2"><Input value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} /></Field>
        <div className="sm:col-span-2">
          <Checkbox label="Đang sử dụng" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
          {!form.isActive && <p className="mt-1 text-small text-ink-faint">Kho ngừng dùng sẽ không xuất hiện trong các ô chọn vị trí. Chỉ ngừng dùng được khi kho đã hết hàng.</p>}
        </div>
      </div>
    </Modal>
  );
}
