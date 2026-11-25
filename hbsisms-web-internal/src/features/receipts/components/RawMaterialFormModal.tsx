import { useEffect, useState } from 'react';
import { Boxes } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { useUnits } from '@/features/products';
import { useRawZones } from '@/features/warehouses';
import { Button, Checkbox, Field, InfoTip, Input, Modal, Select } from '@/shared/ui';
import { receiptsApi } from '../api';
import { useUnitLabel } from '../hooks';
import type { RawMaterial } from '../types';

const empty = { code: '', name: '', unitId: '', storageLocationId: '', reorderLevel: '', isActive: true };

export function RawMaterialFormModal({ open, material, onClose, onSaved }: { open: boolean; material?: RawMaterial; onClose: () => void; onSaved?: (id: number) => void }) {
  const units = useUnits();
  const zones = useRawZones().data ?? [];
  const unitLabel = useUnitLabel();
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(material ? { code: material.code, name: material.name, unitId: material.unitId ? String(material.unitId) : '', storageLocationId: material.storageLocationId ? String(material.storageLocationId) : '', reorderLevel: material.reorderLevel?.toString() ?? '', isActive: material.isActive } : empty);
  }, [open, material]);

  const errors = {
    code: !form.code.trim() ? 'Vui lòng nhập mã' : undefined,
    name: !form.name.trim() ? 'Vui lòng nhập tên' : undefined,
    unitId: !form.unitId ? 'Vui lòng chọn đơn vị tính' : undefined,
    storageLocationId: !form.storageLocationId ? 'Vui lòng chọn khu cất' : undefined,
    reorderLevel: form.reorderLevel !== '' && !(Number(form.reorderLevel) >= 0) ? 'Không được âm' : undefined,
  };
  const save = useAction(
    () => {
      const body = { code: form.code.trim(), name: form.name.trim(), unitId: Number(form.unitId), storageLocationId: Number(form.storageLocationId), reorderLevel: form.reorderLevel === '' ? null : Number(form.reorderLevel), isActive: form.isActive };
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
        <Field label="Đơn vị tính" required error={touched ? errors.unitId : undefined} hint={material && !material.unitId ? `Đơn vị cũ: ${material.unit} (chưa gắn danh mục)` : undefined}>
          <Select aria-label="Đơn vị tính" value={form.unitId} onChange={(e) => setForm({ ...form, unitId: e.target.value })}>
            <option value="">Chọn...</option>
            {(units.data ?? []).filter((u) => u.isActive || String(u.id) === form.unitId).map((u) => <option key={u.id} value={u.id}>{u.name} ({u.code})</option>)}
          </Select>
        </Field>
        <Field
          label="Khu cất"
          required
          error={touched ? errors.storageLocationId : undefined}
          tip="Chỗ cất nguyên liệu trong kho nguyên liệu. Mọi lô của nguyên liệu này nằm ở khu đó; đồ tươi sống để Khu lạnh, khay, màng, gia vị để Khu khô."
        >
          <Select aria-label="Khu cất" value={form.storageLocationId} onChange={(e) => setForm({ ...form, storageLocationId: e.target.value })}>
            <option value="">Chọn...</option>
            {zones.map((z) => <option key={z.id} value={z.id}>{z.zone ?? z.code} ({z.code})</option>)}
          </Select>
        </Field>
        <Field label="Mức đặt lại" error={touched ? errors.reorderLevel : undefined} tip="Khi tồn kho xuống dưới số này, hệ thống sẽ báo “Cần đặt thêm”."><Input type="number" min={0} step="0.1" value={form.reorderLevel} onChange={(e) => setForm({ ...form, reorderLevel: e.target.value })} /></Field>
        <div className="sm:col-span-2">
          <Checkbox label="Đang sử dụng" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} />
          <div className="mt-3 flex items-center gap-3 rounded-md border border-line bg-rowalt/60 px-3 py-2.5">
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden><Boxes size={17} /></span>
            <div>
              <div className="flex items-center gap-1 text-small text-ink-muted">
                {material ? 'Tồn hiện tại' : 'Tồn ban đầu'}
                <InfoTip label="Ghi chú về tồn">Không sửa tồn ở đây. Tồn chỉ tăng khi bấm nút “Nhập kho” ở phiếu nhập nguyên liệu.</InfoTip>
              </div>
              <div className="text-h1 tabular-nums text-ink">
                {num(material?.qtyOnHand ?? 0)}
                {material && <span className="ml-1 text-body font-normal text-ink-muted">{unitLabel(material.unit)}</span>}
              </div>
            </div>
          </div>
        </div>
      </div>
    </Modal>
  );
}
