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
  orderId: ID;
  orderCode: string | null;
  customerName: string | null;
  /** Max refundable (net paid). */
  maxAmount: number;
  sourcePaymentId?: ID | null;
  defaultMethod?: PaymentMethod;
}

const METHODS: PaymentMethod[] = ['cash', 'bank_transfer'];

export function RefundFormModal({
  open,
  onClose,
  orderId,
  orderCode,
  customerName,
  maxAmount,
  sourcePaymentId = null,
  defaultMethod = 'bank_transfer',
}: Props) {
  const [amount, setAmount] = useState('');
  const [method, setMethod] = useState<PaymentMethod>(defaultMethod);
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setAmount(String(maxAmount > 0 ? maxAmount : ''));
    setMethod(METHODS.includes(defaultMethod) ? defaultMethod : 'bank_transfer');
    setReference('');
    setNote('');
    setTouched(false);
  }, [open, maxAmount, defaultMethod]);

  const value = Number(amount);
  const valid = value > 0 && value <= maxAmount;
  const create = useAction(
    () =>
      paymentsApi.refund({
        orderId,
        sourcePaymentId: sourcePaymentId ?? null,
        method,
        amount: value,
        reference: reference.trim() || null,
        note: note.trim() || null,
      }),
    {
      success: (p) => `Đã ghi nhận hoàn ${money(p.amount)} (${p.code})`,
      onDone: onClose,
    },
  );

  return (
    <Modal
      open={open}
      title="Ghi nhận hoàn tiền"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (valid) create.mutate();
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={create.isPending}>
            Ghi nhận hoàn
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-ink-muted">
          {customerName ?? 'Khách'}
          {orderCode ? ` · đơn ${orderCode}` : ''}. Có thể hoàn tối đa{' '}
          <span className="num font-semibold text-ink">{money(maxAmount)}</span>.
        </p>
        <Field
          label="Số tiền hoàn (₫)"
          required
          error={touched && !valid ? `Nhập số tiền từ 1 đến ${money(maxAmount)}` : undefined}
        >
          <Input type="number" min={1} max={maxAmount} value={amount} onChange={(e) => setAmount(e.target.value)} />
        </Field>
        <Field label="Phương thức hoàn" required>
          <Select value={method} onChange={(e) => setMethod(e.target.value as PaymentMethod)}>
            {METHODS.map((m) => (
              <option key={m} value={m}>
                {paymentMethodLabels[m]}
              </option>
            ))}
          </Select>
        </Field>
        <Field label="Mã tham chiếu">
          <Input
            placeholder="Mã CK hoàn / biên lai..."
            value={reference}
            onChange={(e) => setReference(e.target.value)}
          />
        </Field>
        <Field label="Ghi chú">
          <Input placeholder="Lý do hoàn..." value={note} onChange={(e) => setNote(e.target.value)} />
        </Field>
      </div>
    </Modal>
  );
}
