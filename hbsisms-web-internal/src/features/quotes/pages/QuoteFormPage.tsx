import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useCustomerOptions } from '@/features/customers';
import { LineItemsEditor, isCompleteLine, newLine, toPricingLines, usePricing, type EditableLine } from '@/features/pricing';
import { useVariantOptions } from '@/features/products';
import { useAction } from '@/shared/api/useAction';
import { money, todayISO } from '@/shared/lib/format';
import { Button, Card, Field, Input, PageHeader, Select, Textarea } from '@/shared/ui';
import { quotesApi } from '../api';

export default function QuoteFormPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const customers = useCustomerOptions();
  const variants = useVariantOptions();
  const requestId = params.get('requestId') ? Number(params.get('requestId')) : null;
  const [customerId, setCustomerId] = useState<number | null>(params.get('customerId') ? Number(params.get('customerId')) : null);
  const [validUntil, setValidUntil] = useState(todayISO(7));
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<EditableLine[]>([newLine()]);
  const [touched, setTouched] = useState(false);

  const pricingLines = useMemo(() => toPricingLines(lines), [lines]);
  const pricing = usePricing({ customerId, lines: toPricingLines(lines.filter(isCompleteLine)) });
  const linesValid = lines.every(isCompleteLine);

  const create = useAction(() => quotesApi.create({ customerId: customerId!, requestId, validUntil: validUntil || null, note: note.trim() || null, lines: pricingLines }), {
    success: (q) => `Đã lập báo giá ${q.code}`,
    onDone: (q) => navigate(`/quotes/${q.id}`),
  });

  return (
    <>
      <PageHeader breadcrumb={<><Link to="/quotes" className="hover:underline">Báo giá</Link> / Lập báo giá</>} title="Lập báo giá" />
      <div className="space-y-4">
        <Card title="Thông tin chung">
          <div className="grid gap-3 md:grid-cols-3">
            <Field label="Khách hàng" required error={touched && !customerId ? 'Vui lòng chọn khách' : undefined}>
              <Select value={customerId ?? ''} onChange={(e) => setCustomerId(e.target.value ? Number(e.target.value) : null)}>
                <option value="">Chọn khách hàng...</option>
                {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
              </Select>
            </Field>
            <Field label="Hiệu lực đến"><Input type="date" min={todayISO()} value={validUntil} onChange={(e) => setValidUntil(e.target.value)} /></Field>
            <Field label="Ghi chú"><Textarea rows={1} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
          </div>
        </Card>
        <Card title="Dòng hàng" flush>
          <LineItemsEditor lines={lines} onChange={setLines} variants={variants.data ?? []} priced={pricing.data?.lines} showStock={false} />
          {touched && !linesValid && <p className="px-3 pb-3 text-small text-red-fg">Mỗi dòng cần chọn sản phẩm và số lượng là số nguyên dương.</p>}
        </Card>
        <div className="flex items-center justify-end gap-4">
          <span className="text-h2">Tổng cộng: <span className="tabular-nums">{money(pricing.data?.total ?? 0)}</span></span>
          <Button
            variant="primary"
            loading={create.isPending}
            onClick={() => {
              setTouched(true);
              if (customerId && linesValid) create.mutate();
            }}
          >
            Lưu báo giá
          </Button>
        </div>
      </div>
    </>
  );
}
