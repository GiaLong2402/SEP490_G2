import { Banknote, CircleCheck, Landmark, TriangleAlert } from 'lucide-react';
import { useEffect, useState, type ReactNode } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import type { PaymentMethod } from '@/shared/domain/enums';
import { dateTime, formatThousands, money, parseThousands } from '@/shared/lib/format';
import { Button, Field, Input, Modal } from '@/shared/ui';
import { paymentsApi } from '../api';
import type { PaymentListItem } from '../types';

interface Props {
  open: boolean;
  onClose: () => void;
  orderId: ID;
  orderCode: string | null;
  customerName: string | null;
  /** Max refundable (net paid). */
  maxAmount: number;
  /** Đã hoàn trước đó cho đơn này. */
  refundedAmount?: number;
  sourcePaymentId?: ID | null;
  /** Lần khách chuyển vào — để Chủ biết đang hoàn cho giao dịch nào, chuyển trả về đâu. */
  source?: PaymentListItem | null;
  defaultMethod?: PaymentMethod;
}

const METHODS: { value: PaymentMethod; label: string; icon: ReactNode }[] = [
  { value: 'bank_transfer', label: 'Chuyển khoản', icon: <Landmark size={16} /> },
  { value: 'cash', label: 'Tiền mặt', icon: <Banknote size={16} /> },
];

export function RefundFormModal({
  open,
  onClose,
  orderId,
  orderCode,
  customerName,
  maxAmount,
  refundedAmount = 0,
  sourcePaymentId = null,
  source = null,
  defaultMethod = 'bank_transfer',
}: Props) {
  const excess = source?.excessAmount && source.excessAmount > 0 ? source.excessAmount : 0;
  // Phần thừa còn phải hoàn (khoản hoàn trước đó trừ vào phần thừa trước)
  const excessLeft = Math.max(0, Math.min(excess - refundedAmount, maxAmount));
  const needsRefund = Boolean(source?.needsRefund);
  const paidIn = source?.amount ?? maxAmount + refundedAmount;

  const [digits, setDigits] = useState('');
  const [method, setMethod] = useState<PaymentMethod>(defaultMethod);
  const [reference, setReference] = useState('');
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    // Điền sẵn: còn phần thừa → phần thừa; đơn hủy cần hoàn → toàn bộ; còn lại để Chủ tự nhập
    const start = excessLeft > 0 ? excessLeft : needsRefund ? maxAmount : 0;
    setDigits(start > 0 ? String(start) : '');
    setMethod(defaultMethod === 'cash' ? 'cash' : 'bank_transfer');
    setReference('');
    setNote(excessLeft > 0 ? 'Hoàn phần chuyển thừa' : needsRefund ? 'Đơn đã hủy, hoàn tiền cho khách' : '');
    setTouched(false);
  }, [open, excessLeft, needsRefund, maxAmount, defaultMethod]);

  const value = Number(digits || 0);
  const amountOk = value > 0 && value <= maxAmount;
  // Bằng chứng (Excel #40): chuyển khoản → mã giao dịch của lần chuyển trả; tiền mặt → đã trả cho ai, ở đâu
  const isTransfer = method === 'bank_transfer';
  const referenceOk = !isTransfer || reference.trim() !== '';
  const noteOk = isTransfer || note.trim() !== '';
  const valid = amountOk && referenceOk && noteOk;
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

  const quick = [
    excessLeft > 0 ? { label: 'Phần thừa', amount: excessLeft } : null,
    maxAmount > 0 && maxAmount !== excessLeft ? { label: 'Toàn bộ còn lại', amount: maxAmount } : null,
  ].filter((x): x is { label: string; amount: number } => x !== null);
  const payer = [source?.payerName, source?.payerBank, source?.payerAccount].filter(Boolean).join(' · ');

  return (
    <Modal
      open={open}
      title="Hoàn tiền cho khách"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (valid) create.mutate();
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={create.isPending}>
            Ghi nhận hoàn {amountOk ? money(value) : ''}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <p className="-mt-1 text-ink-muted">
          {orderCode ? <span className="num">{orderCode}</span> : 'Giao dịch'}
          {' · '}
          <span className="font-medium text-ink">{customerName ?? 'Khách'}</span>
        </p>

        {/* Bức tranh tiền của đơn */}
        <div className="overflow-hidden rounded-md border border-line">
          <div className="grid grid-cols-3 divide-x divide-line bg-thead">
            <Stat label="Khách đã chuyển" value={money(paidIn)} />
            <Stat label="Đã hoàn" value={money(refundedAmount)} />
            <Stat label="Còn có thể hoàn" value={money(maxAmount)} strong />
          </div>
          {source && (source.reference || source.payosOrderCode || source.paidAt || payer) ? (
            <div className="space-y-0.5 border-t border-line px-3 py-2 text-small text-ink-muted">
              {source.reference || source.payosOrderCode ? (
                <div>
                  {source.reference ? 'Mã giao dịch khách chuyển: ' : 'Mã PayOS: '}
                  <span className="num text-ink">{source.reference ?? source.payosOrderCode}</span>
                  {source.paidAt ? <> · {dateTime(source.paidAt)}</> : null}
                </div>
              ) : null}
              {payer ? (
                <div>
                  Chuyển trả về: <span className="text-ink">{payer}</span>
                </div>
              ) : null}
            </div>
          ) : null}
        </div>

        {excess > 0 && excessLeft > 0 ? (
          <Notice tone="amber" icon={<TriangleAlert size={16} />}>
            Khách chuyển thừa <b className="num">{money(excess)}</b> so với tiền đơn (
            <span className="num">{money(paidIn - excess)}</span>). Cần hoàn lại <b className="num">{money(excessLeft)}</b>.
          </Notice>
        ) : excess > 0 ? (
          <Notice tone="green" icon={<CircleCheck size={16} />}>
            Đã hoàn đủ phần chuyển thừa <b className="num">{money(excess)}</b>. Chỉ hoàn thêm khi đơn bị hủy hoặc khách trả hàng.
          </Notice>
        ) : needsRefund ? (
          <Notice tone="amber" icon={<TriangleAlert size={16} />}>
            Đơn đã hủy sau khi khách chuyển tiền — cần hoàn lại toàn bộ.
          </Notice>
        ) : null}

        <Field
          label="Số tiền hoàn"
          required
          error={touched && !amountOk ? `Nhập số tiền từ 1 đến ${money(maxAmount)}` : undefined}
        >
          <div className="relative">
            <Input
              inputMode="numeric"
              autoComplete="off"
              placeholder="0"
              className="h-11 pr-9 text-right text-h1 num"
              value={formatThousands(digits)}
              onChange={(e) => setDigits(parseThousands(e.target.value).replace(/^0+/, ''))}
            />
            <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-ink-muted">₫</span>
          </div>
        </Field>
        {quick.length ? (
          <div className="-mt-2 flex flex-wrap gap-2">
            {quick.map((q) => (
              <button
                key={q.label}
                type="button"
                onClick={() => setDigits(String(q.amount))}
                className={`rounded-full border px-3 py-1 text-small transition-colors ${
                  value === q.amount
                    ? 'border-primary bg-primary-soft text-primary'
                    : 'border-line text-ink-muted hover:border-primary hover:text-primary'
                }`}
              >
                {q.label} · <span className="num">{money(q.amount)}</span>
              </button>
            ))}
          </div>
        ) : null}

        <div>
          <div className="mb-1 text-small font-medium text-ink-muted">
            Cách hoàn<span className="text-red-fg"> *</span>
          </div>
          <div role="radiogroup" aria-label="Cách hoàn" className="grid grid-cols-2 gap-2">
            {METHODS.map((m) => (
              <button
                key={m.value}
                type="button"
                role="radio"
                aria-checked={method === m.value}
                onClick={() => setMethod(m.value)}
                className={`flex h-10 items-center justify-center gap-2 rounded-md border text-body font-medium transition-colors ${
                  method === m.value
                    ? 'border-primary bg-primary-soft text-primary'
                    : 'border-line text-ink-muted hover:border-primary/50 hover:text-ink'
                }`}
              >
                {m.icon}
                {m.label}
              </button>
            ))}
          </div>
        </div>

        {isTransfer ? (
          <Field
            label="Mã giao dịch của lần chuyển trả"
            required
            hint={
              !(touched && !referenceOk)
                ? 'Mở app ngân hàng → Lịch sử giao dịch → chọn lần chuyển trả khách → xem "Mã giao dịch" hoặc "Số tham chiếu".'
                : undefined
            }
            error={touched && !referenceOk ? 'Nhập mã giao dịch của lần chuyển trả khách' : undefined}
          >
            <Input
              className="num"
              placeholder="VD: FT26320ABCDE"
              autoComplete="off"
              value={reference}
              onChange={(e) => setReference(e.target.value.toUpperCase())}
            />
          </Field>
        ) : null}
        <Field
          label={isTransfer ? 'Ghi chú' : 'Đã trả cho ai, ở đâu (bằng chứng)'}
          required={!isTransfer}
          error={touched && !noteOk ? 'Ghi rõ đã trả tiền mặt cho ai, ở đâu' : undefined}
        >
          <Input
            placeholder={isTransfer ? 'Lý do hoàn...' : 'VD: Đã trả chị Lan tại cửa hàng'}
            value={note}
            onChange={(e) => setNote(e.target.value)}
          />
        </Field>
      </div>
    </Modal>
  );
}

function Stat({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="min-w-0 px-3 py-2.5">
      <div className="text-micro text-ink-muted">{label}</div>
      <div className={`num mt-0.5 truncate ${strong ? 'text-h2 font-semibold text-primary' : 'text-body font-medium text-ink'}`}>{value}</div>
    </div>
  );
}

function Notice({ tone, icon, children }: { tone: 'amber' | 'green'; icon: ReactNode; children: ReactNode }) {
  const cls = tone === 'amber' ? 'border-amber-fg/25 bg-amber-bg text-amber-fg' : 'border-green-fg/25 bg-green-bg text-green-fg';
  return (
    <div role="status" className={`flex gap-2 rounded-md border px-3 py-2 text-body ${cls}`}>
      <span className="mt-px shrink-0">{icon}</span>
      <div>{children}</div>
    </div>
  );
}
