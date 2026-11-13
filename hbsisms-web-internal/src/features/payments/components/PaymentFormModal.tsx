import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import { paymentMethodLabels, type PaymentMethod } from '@/shared/domain/enums';
import { money } from '@/shared/lib/format';
import { Button, Field, Input, Modal, Select } from '@/shared/ui';
import { paymentsApi } from '../api';

interface Props {
  open: boolean;
  onClose: () => void;
  customerId: ID | null;
  customerName: string | null;
  /** Thu theo đơn; bỏ trống là thu vào công nợ chung của khách. */
  order?: { id: ID; code: string; outstanding: number };
  maxAmount?: number;
  defaultMethod?: PaymentMethod;
}

/** PayOS (cổng QR web) bản chất là CK → gộp vào bank_transfer, không hiện riêng. */
const METHODS: PaymentMethod[] = ['cash', 'bank_transfer', 'cod'];

function toStaffPaymentMethod(method: PaymentMethod): PaymentMethod {
  return method === 'payos' ? 'bank_transfer' : METHODS.includes(method) ? method : 'bank_transfer';
}

export function PaymentFormModal({ open, onClose, customerId, customerName, order, maxAmount, defaultMethod = 'bank_transfer' }: Props) {
  const limit = order?.outstanding ?? maxAmount ?? 0;
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>(toStaffPaymentMethod(defaultMethod));
  const [reference, setReference] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setAmount(String(limit));
    setMethod(toStaffPaymentMethod(defaultMethod));
    setReference('');
    setTouched(false);
  }, [open, limit, defaultMethod]);

  const value = Number(amount);
  const valid = value > 0 && value <= limit;
  const create = useAction(() => paymentsApi.create({ customerId, orderId: order?.id ?? null, method, amount: value, reference: reference.trim() || null, note: null }), {
    success: (p) => `Đã ghi nhận ${money(p.amount)} (${p.code})`,
    onDone: onClose,
  });

  return (
    <Modal
      open={open}
      title="Ghi nhận thanh toán"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (valid) create.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Ghi nhận</Button></>}
    >
      <div className="space-y-3">
        <p className="text-ink-muted">
          {customerName}
          {order ? ` · đơn ${order.code}` : ' · thu công nợ'}. Còn phải thu <span className="num font-semibold text-ink">{money(limit)}</span>.
        </p>
        <Field label="Số tiền (₫)" required error={touched && !valid ? `Nhập số tiền từ 1 đến ${money(limit)}` : undefined}><Input type="number" min={1} max={limit} value={amount} onChange={(e) => setAmount(e.target.value)} /></Field>
        <Field label="Phương thức" required>
          <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {METHODS.map((m) => <option key={m} value={m}>{paymentMethodLabels[m]}</option>)}
          </Select>
        </Field>
        <Field label="Mã tham chiếu"><Input placeholder="Mã giao dịch ngân hàng..." value={reference} onChange={(e) => setReference(e.target.value)} /></Field>
      </div>
    </Modal>
  );
}
