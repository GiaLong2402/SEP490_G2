import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Textarea } from '@/shared/ui';
import { suppliersApi } from '../api';
import type { SupplierListItem } from '../types';

const empty = { name: '', taxCode: '', contactName: '', phone: '', email: '', address: '', note: '', isActive: true };

export function SupplierFormModal({ open, supplier, onClose, onSaved }: { open: boolean; supplier?: SupplierListItem; onClose: () => void; onSaved?: (id: number) => void }) {
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(supplier ? { name: supplier.name, taxCode: supplier.taxCode ?? '', contactName: supplier.contactName ?? '', phone: supplier.phone ?? '', email: supplier.email ?? '', address: supplier.address ?? '', note: supplier.note ?? '', isActive: supplier.isActive } : empty);
  }, [open, supplier]);
  const set = <K extends keyof typeof empty>(key: K, value: (typeof empty)[K]) => setForm((f) => ({ ...f, [key]: value }));

  const errors = {
    name: !form.name.trim() ? 'Vui lòng nhập tên nhà cung cấp' : undefined,
    email: form.email.trim() && !/^\S+@\S+\.\S+$/.test(form.email.trim()) ? 'Email không hợp lệ' : undefined,
  };
  const save = useAction(
    () => {
      const clean = (value: string) => value.trim() || null;
      const body = { name: form.name.trim(), taxCode: clean(form.taxCode), contactName: clean(form.contactName), phone: clean(form.phone), email: clean(form.email), address: clean(form.address), note: clean(form.note), isActive: form.isActive };
      return supplier ? suppliersApi.update(supplier.id, body) : suppliersApi.create(body);
    },
    {
      success: (s) => `Đã lưu nhà cung cấp ${s.name}`,
      onDone: (s) => {
        onClose();
        onSaved?.(s.id);
      },
    },
  );

  return (
    <Modal
      open={open}
      title={supplier ? `Sửa ${supplier.code}` : 'Thêm nhà cung cấp'}
      width="lg"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!errors.name && !errors.email) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Tên nhà cung cấp" required className="sm:col-span-2" error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="Người liên hệ"><Input value={form.contactName} onChange={(e) => set('contactName', e.target.value)} /></Field>
        <Field label="Điện thoại"><Input value={form.phone} onChange={(e) => set('phone', e.target.value)} /></Field>
        <Field label="Email" error={touched ? errors.email : undefined}><Input type="email" value={form.email} onChange={(e) => set('email', e.target.value)} /></Field>
        <Field label="Mã số thuế"><Input value={form.taxCode} onChange={(e) => set('taxCode', e.target.value)} /></Field>
        <Field label="Địa chỉ" className="sm:col-span-2"><Input value={form.address} onChange={(e) => set('address', e.target.value)} /></Field>
        <Field label="Ghi chú" className="sm:col-span-2"><Textarea value={form.note} onChange={(e) => set('note', e.target.value)} /></Field>
        <div className="sm:col-span-2">
          <Checkbox label="Đang giao dịch" checked={form.isActive} onChange={(e) => set('isActive', e.target.checked)} />
          {!form.isActive && <p className="mt-1 text-small text-ink-faint">Phiếu nhập cũ vẫn giữ nguyên, nhưng không chọn được nhà cung cấp này cho phiếu mới.</p>}
        </div>
      </div>
    </Modal>
  );
}
