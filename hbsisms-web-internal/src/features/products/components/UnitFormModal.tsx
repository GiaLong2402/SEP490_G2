import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Field, Input, Modal, Select } from '@/shared/ui';
import { productsApi } from '../api';
import type { UnitOfMeasure } from '../types';

const empty = { code: '', name: '', isActive: true };

export function UnitFormModal({
  open,
  unit,
  onClose,
  onSaved,
}: {
  open: boolean;
  unit?: UnitOfMeasure;
  onClose: () => void;
  onSaved?: (saved: UnitOfMeasure) => void;
}) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(unit ? { code: unit.code, name: unit.name, isActive: unit.isActive } : empty);
  }, [open, unit]);

  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã đơn vị' : form.code.trim().length > 20 ? 'Tối đa 20 ký tự' : undefined,
    name: !form.name.trim() ? 'Vui lòng nhập tên đơn vị' : form.name.trim().length > 80 ? 'Tối đa 80 ký tự' : undefined,
  };

  const save = useAction(
    () => {
      const body = { code: form.code.trim(), name: form.name.trim(), isActive: form.isActive };
      return unit ? productsApi.updateUnit(unit.id, body) : productsApi.createUnit(body);
    },
    {
      success: (saved) => `Đã lưu đơn vị ${saved.name}`,
      onDone: (saved) => {
        onClose();
        onSaved?.(saved);
      },
    },
  );

  return (
    <Modal
      open={open}
      title={unit ? `Sửa đơn vị ${unit.code}` : 'Thêm đơn vị tính'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!errors.code && !errors.name) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Mã đơn vị" required error={touched ? errors.code : undefined}>
          <Input placeholder="kg, cái, cuộn..." value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
        </Field>
        <Field label="Tên đơn vị" required error={touched ? errors.name : undefined}>
          <Input placeholder="Ki-lô-gam" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
        </Field>
        <Field label="Trạng thái" hint="Không thể ngừng dùng đơn vị đang gắn với sản phẩm hoặc nguyên liệu." className="sm:col-span-2">
          <Select value={form.isActive ? 'true' : 'false'} onChange={(e) => setForm({ ...form, isActive: e.target.value === 'true' })}>
            <option value="true">Đang dùng</option>
            <option value="false">Ngừng dùng</option>
          </Select>
        </Field>
      </div>
    </Modal>
  );
}
