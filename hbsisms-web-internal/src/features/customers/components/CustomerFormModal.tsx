import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Select, Textarea } from '@/shared/ui';
import { customersApi } from '../api';
import { normalizeVnPhone, validateOptionalPhone, validateOptionalTaxCode } from '../customerValidation';
import type { CustomerDetail, CustomerInput } from '../types';

const empty: CustomerInput = {
  customerType: 'b2c',
  name: '',
  email: '',
  phone: '',
  taxCode: '',
  contactName: '',
  contactPhone: '',
  creditLimit: 0,
  netDays: 0,
  creditEnabled: false,
  isActive: true,
  note: '',
};

/** Phone normalize như API. B2C: hạn mức chỉ khi creditEnabled. */
function toRequestBody(form: CustomerInput): CustomerInput {
  if (form.customerType === 'b2b') {
    return {
      ...form,
      email: form.email?.trim() || null,
      phone: normalizeVnPhone(form.phone),
      taxCode: form.taxCode?.trim() || null,
      contactName: form.contactName?.trim() || null,
      contactPhone: normalizeVnPhone(form.contactPhone),
      creditEnabled: true,
      note: form.note?.trim() || null,
    };
  }
  const creditEnabled = Boolean(form.creditEnabled);
  return {
    ...form,
    email: form.email?.trim() || null,
    phone: normalizeVnPhone(form.phone),
    taxCode: null,
    contactName: null,
    contactPhone: null,
    creditEnabled,
    creditLimit: creditEnabled ? form.creditLimit : 0,
    netDays: creditEnabled ? form.netDays : 0,
    note: form.note?.trim() || null,
  };
}

export function CustomerFormModal({ open, customer, onClose, onSaved }: { open: boolean; customer?: CustomerDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const [form, setForm] = useState<CustomerInput>(empty);
  const [touched, setTouched] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(
      customer
        ? {
            customerType: customer.customerType,
            name: customer.name,
            email: customer.email,
            phone: customer.phone,
            taxCode: customer.taxCode,
            contactName: customer.contactName,
            contactPhone: customer.contactPhone,
            creditLimit: customer.creditLimit,
            netDays: customer.netDays,
            creditEnabled: customer.creditEnabled,
            isActive: customer.isActive,
            note: customer.note,
          }
        : empty,
    );
  }, [open, customer]);
  const set = <K extends keyof CustomerInput>(key: K, value: CustomerInput[K]) => setForm((f) => ({ ...f, [key]: value }));
  const save = useAction(() => {
    const body = toRequestBody(form);
    return customer ? customersApi.update(customer.id, body) : customersApi.create(body);
  }, {
    success: (saved) =>
      saved.creditChangePending ? 'Đã gửi đề xuất hạn mức — chờ Chủ duyệt' : 'Đã lưu khách hàng',
    onDone: (saved) => {
      onClose();
      onSaved?.(saved.id);
    },
  });
  const isB2b = form.customerType === 'b2b';
  const showCredit = isB2b || Boolean(form.creditEnabled);
  const errors = {
    name: !form.name.trim() ? 'Vui lòng nhập tên khách' : undefined,
    phone: validateOptionalPhone(form.phone),
    tax: isB2b ? validateOptionalTaxCode(form.taxCode) : undefined,
    contactPhone: isB2b ? validateOptionalPhone(form.contactPhone, 'SĐT liên hệ') : undefined,
    limit: showCredit && form.creditLimit < 0 ? 'Hạn mức không được âm' : undefined,
    net: showCredit && (form.netDays < 0 || !Number.isInteger(form.netDays)) ? 'Số ngày không hợp lệ' : undefined,
  };
  const hasError = !!(errors.name || errors.phone || errors.tax || errors.contactPhone || errors.limit || errors.net);
  return (
    <Modal
      open={open}
      title={customer ? `Sửa ${customer.code}` : 'Thêm khách hàng'}
      onClose={onClose}
      width="lg"
      onSubmit={() => {
        setTouched(true);
        if (!hasError) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Loại khách" required>
          <Select
            value={form.customerType}
            onChange={(e) => {
              const customerType = e.target.value as CustomerInput['customerType'];
              setForm((f) =>
                customerType === 'b2b'
                  ? { ...f, customerType, creditEnabled: true, netDays: f.netDays || 15 }
                  : {
                      ...f,
                      customerType,
                      taxCode: '',
                      contactName: '',
                      contactPhone: '',
                      creditEnabled: false,
                      creditLimit: 0,
                      netDays: 0,
                    },
              );
            }}
          >
            <option value="b2c">B2C (khách lẻ)</option>
            <option value="b2b">B2B (doanh nghiệp)</option>
          </Select>
        </Field>
        <Field label="Tên khách hàng" required error={touched ? errors.name : undefined}><Input value={form.name} onChange={(e) => set('name', e.target.value)} /></Field>
        <Field label="Điện thoại" error={touched ? errors.phone : undefined}><Input value={form.phone ?? ''} onChange={(e) => set('phone', e.target.value)} placeholder="0xxxxxxxxx" /></Field>
        <Field label="Email"><Input type="email" value={form.email ?? ''} onChange={(e) => set('email', e.target.value)} /></Field>
        {isB2b && (
          <>
            <Field label="Mã số thuế" error={touched ? errors.tax : undefined}><Input value={form.taxCode ?? ''} onChange={(e) => set('taxCode', e.target.value)} placeholder="10–13 chữ số" /></Field>
            <Field label="Người liên hệ"><Input value={form.contactName ?? ''} onChange={(e) => set('contactName', e.target.value)} /></Field>
            <Field label="SĐT liên hệ" error={touched ? errors.contactPhone : undefined}><Input value={form.contactPhone ?? ''} onChange={(e) => set('contactPhone', e.target.value)} placeholder="0xxxxxxxxx" /></Field>
          </>
        )}
        {!isB2b && (
          <div className="sm:col-span-2">
            <Checkbox
              label="Cho phép mua nợ"
              checked={Boolean(form.creditEnabled)}
              onChange={(e) =>
                setForm((f) => ({
                  ...f,
                  creditEnabled: e.target.checked,
                  creditLimit: e.target.checked ? f.creditLimit : 0,
                  netDays: e.target.checked ? f.netDays || 15 : 0,
                }))
              }
            />
          </div>
        )}
        {showCredit && (
          <>
            <Field label="Hạn mức công nợ (₫)" error={touched ? errors.limit : undefined}>
              <Input
                type="number"
                min={0}
                inputMode="numeric"
                value={form.creditLimit === 0 ? '' : form.creditLimit}
                placeholder="0"
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === '') {
                    set('creditLimit', 0);
                    return;
                  }
                  const n = Number(raw);
                  if (!Number.isNaN(n) && n >= 0) set('creditLimit', n);
                }}
              />
            </Field>
            <Field label="Kỳ hạn NET (ngày)" error={touched ? errors.net : undefined}>
              <Input
                type="number"
                min={0}
                inputMode="numeric"
                value={form.netDays === 0 ? '' : form.netDays}
                placeholder="0"
                onChange={(e) => {
                  const raw = e.target.value;
                  if (raw === '') {
                    set('netDays', 0);
                    return;
                  }
                  const n = Number(raw);
                  if (!Number.isNaN(n) && n >= 0 && Number.isInteger(n)) set('netDays', n);
                }}
              />
            </Field>
            <p className="sm:col-span-2 text-small text-ink-muted">Bật mua nợ hoặc tăng hạn mức sẽ gửi Chủ duyệt.</p>
          </>
        )}
        <Field label="Ghi chú" className="sm:col-span-2"><Textarea value={form.note ?? ''} onChange={(e) => set('note', e.target.value)} /></Field>
        <div className="sm:col-span-2"><Checkbox label="Đang hoạt động" checked={form.isActive} onChange={(e) => set('isActive', e.target.checked)} /></div>
      </div>
    </Modal>
  );
}
