import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Select, Textarea } from '@/shared/ui';
import { customersApi } from '../api';
import type { CustomerDetail, CustomerInput } from '../types';

const empty: CustomerInput = { customerType: 'b2c', name: '', email: '', phone: '', taxCode: '', contactName: '', contactPhone: '', creditLimit: 0, netDays: 15, isActive: true, note: '' };

export function CustomerFormModal({ open, customer, onClose, onSaved }: { open: boolean; customer?: CustomerDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const [form, setForm] = useState<CustomerInput>(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(customer ? { customerType: customer.customerType, name: customer.name, email: customer.email, phone: customer.phone, taxCode: customer.taxCode, contactName: customer.contactName, contactPhone: customer.contactPhone, creditLimit: customer.creditLimit, netDays: customer.netDays, isActive: customer.isActive, note: customer.note } : empty);
  }, [open, customer]);
  const set = <K extends keyof CustomerInput>(key: K, value: CustomerInput[K]) => setForm((f) => ({ ...f, [key]: value }));
  const save = useAction(() => (customer ? customersApi.update(customer.id, form) : customersApi.create(form)), {
    success: 'Đã lưu khách hàng',
    onDone: (saved) => {
      onClose();
      onSaved?.(saved.id);
    },
  });
  const isB2b = form.customerType === 'b2b';
  const errors = {
    name: !form.name.trim() ? 'Vui lòng nhập tên khách' : undefined,
    limit: isB2b && form.creditLimit < 0 ? 'Hạn mức không được âm' : undefined,
    net: isB2b && (form.netDays < 0 || !Number.isInteger(form.netDays)) ? 'Số ngày không hợp lệ' : undefined,
  };
  return (
    <Modal
      open={open}
      title={customer ? `Sửa ${customer.code}` : 'Thêm khách hàng'}
      onClose={onClose}
      width="lg"
      onSubmit={() => {
        setTouched(true);
        if (!errors.name && !errors.limit && !errors.net) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Loại khách" required>
          <Select value={form.customerType} onChange={(e) => set('customerType', e.target.value as CustomerInput['customerType'])}>
            <option value="b2c">B2C (khách lẻ)</option>
            <option value="b2b">B2B (doanh nghiệp)</option>
          </Select>
        </Field>
        <Field label="Tên khách hàng" required error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="Điện thoại"><Input value={form.phone ?? ''} onChange={(e) => set('phone', e.target.value)} /></Field>
        <Field label="Email"><Input type="email" value={form.email ?? ''} onChange={(e) => set('email', e.target.value)} /></Field>
        {isB2b && (
          <>
            <Field label="Mã số thuế"><Input value={form.taxCode ?? ''} onChange={(e) => set('taxCode', e.target.value)} /></Field>
            <Field label="Người liên hệ"><Input value={form.contactName ?? ''} onChange={(e) => set('contactName', e.target.value)} /></Field>
            <Field label="SĐT liên hệ"><Input value={form.contactPhone ?? ''} onChange={(e) => set('contactPhone', e.target.value)} /></Field>
            <Field label="Hạn mức công nợ (₫)" error={touched ? errors.limit : undefined}><Input type="number" min={0} value={form.creditLimit} onChange={(e) => set('creditLimit', Number(e.target.value))} /></Field>
            <Field label="Kỳ hạn NET (ngày)" error={touched ? errors.net : undefined}><Input type="number" min={0} value={form.netDays} onChange={(e) => set('netDays', Number(e.target.value))} /></Field>
          </>
        )}
        <Field label="Ghi chú" className="sm:col-span-2"><Textarea value={form.note ?? ''} onChange={(e) => set('note', e.target.value)} /></Field>
        <div className="sm:col-span-2"><Checkbox label="Đang hoạt động" checked={form.isActive} onChange={(e) => set('isActive', e.target.checked)} /></div>
      </div>
    </Modal>
  );
}
