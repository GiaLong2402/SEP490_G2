import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { Button, Checkbox, Field, Input, Modal } from '@/shared/ui';
import { receiptsApi } from '../api';
import type { RawMaterial } from '../types';

const empty = { code: '', name: '', unit: 'kg', reorderLevel: '', isActive: true };

export function RawMaterialFormModal({ open, material, onClose, onSaved }: { open: boolean; material?: RawMaterial; onClose: () => void; onSaved?: (id: number) => void }) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(material ? { code: material.code, name: material.name, unit: material.unit, reorderLevel: material.reorderLevel?.toString() ?? '', isActive: material.isActive } : empty);
  }, [open, material]);

  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã' : undefined,
    name: !form.name.trim() ? 'Vui lòng nhập tên' : undefined,
    unit: !form.unit.trim() ? 'Vui lòng nhập đơn vị' : undefined,
    reorderLevel: form.reorderLevel !== '' && !(Number(form.reorderLevel) >= 0) ? 'Không được âm' : undefined,
  };
  const save = useAction(
    () => {
      const body = { code: form.code.trim(), name: form.name.trim(), unit: form.unit.trim(), reorderLevel: form.reorderLevel === '' ? null : Number(form.reorderLevel), isActive: form.isActive };
      return material ? receiptsApi.updateRawMaterial(material.id, body) : receiptsApi.createRawMaterial(body);
    },
    {
      success: (m) => `Đã lưu nguyên liệu ${m.name}`,
      onDone: (m) => {
        onClose();
        onSaved?.(m.id);
      },
    },
  );

  return (
    <Modal
      open={open}
      title={material ? `Sửa nguyên liệu ${material.code}` : 'Thêm nguyên liệu'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!Object.values(errors).some(Boolean)) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Mã nguyên liệu" required error={touched ? errors.code : undefined}><Input placeholder="NL-MUC" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} /></Field>
        <Field label="Tên nguyên liệu" required error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} /></Field>
        <Field label="Đơn vị" required error={touched ? errors.unit : undefined}><Input placeholder="kg, cái, cuộn..." value={form.unit} onChange={(e) => setForm({ ...form, unit: e.target.value })} /></Field>
        <Field label="Mức đặt lại" error={touched ? errors.reorderLevel : undefined} hint="Tồn dưới mức này sẽ hiện cảnh báo"><Input type="number" min={0} step="0.1" value={form.reorderLevel} onChange={(e) => setForm({ ...form, reorderLevel: e.target.value })} /></Field>
        <div className="sm:col-span-2">
          <Checkbox label="Đang sử dụng" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
          <p className="mt-2 text-small text-ink-faint">{material ? `Tồn hiện tại ${num(material.qtyOnHand)} ${material.unit}. Tồn chỉ tăng qua phiếu nhập, không sửa trực tiếp.` : 'Nguyên liệu mới có tồn bằng 0; tạo phiếu nhập để tăng tồn.'}</p>
        </div>
      </div>
    </Modal>
  );
}
