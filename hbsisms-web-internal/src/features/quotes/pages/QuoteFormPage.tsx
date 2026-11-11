import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useCustomerOptions } from '@/features/customers';
import { LineItemsEditor, isCompleteLine, newLine, toPricingLines, usePricing, type EditableLine } from '@/features/pricing';
import { useVariantOptions } from '@/features/products';
import { useRequest } from '@/features/requests';
import { useAction } from '@/shared/api/useAction';
import { requestStatusMeta, saleChannelLabels } from '@/shared/domain/enums';
import { date, dateTime, money, num, todayISO } from '@/shared/lib/format';
import { Alert, Button, Card, Chip, Field, Input, PageHeader, Select, StatusChip, Textarea } from '@/shared/ui';
import { quotesApi } from '../api';

export default function QuoteFormPage() {
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const customers = useCustomerOptions();
  const variants = useVariantOptions();
  const requestId = params.get('requestId') ? Number(params.get('requestId')) : null;
  const [customerId, setCustomerId] = useState<number | null>(params.get('customerId') ? Number(params.get('customerId')) : null);
  const [validUntil, setValidUntil] = useState(() => todayISO(7));
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<EditableLine[]>([newLine()]);
  const [touched, setTouched] = useState(false);

  const requestQuery = useRequest(requestId);
  const request = requestQuery.data;

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
        {requestId != null && (
          <Card title="Nội dung yêu cầu khách hàng">
            {requestQuery.isLoading && <p className="text-ink-muted">Đang tải yêu cầu…</p>}
            {requestQuery.error && (
              <Alert tone="danger" title="Không tải được yêu cầu">
                {requestQuery.error.message}
              </Alert>
            )}
            {request && (
              <div className="space-y-3">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-mono font-semibold">{request.code}</span>
                  <StatusChip meta={requestStatusMeta} value={request.status} />
                  <Chip tone="neutral">{saleChannelLabels[request.channel]}</Chip>
                </div>
                <p className="whitespace-pre-wrap text-body text-ink">{request.summary?.trim() || '—'}</p>
                <dl className="grid gap-2 text-small text-ink-muted sm:grid-cols-2">
                  <div>
                    <dt className="text-ink-faint">Khách</dt>
                    <dd className="font-medium text-ink">{request.customerName ?? '—'}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-faint">Tiếp nhận</dt>
                    <dd>{dateTime(request.receivedAt)}</dd>
                  </div>
                  <div>
                    <dt className="text-ink-faint">Khả thi</dt>
                    <dd>
                      {request.feasible == null
                        ? 'Chưa đánh giá'
                        : request.feasible
                          ? `Có${request.fulfillableQty != null ? ` · ${num(request.fulfillableQty)} khay` : ''}`
                          : 'Không'}
                    </dd>
                  </div>
                  <div>
                    <dt className="text-ink-faint">Ngày dự kiến</dt>
                    <dd>{request.expectedDate ? date(request.expectedDate) : '—'}</dd>
                  </div>
                </dl>
                {request.note && <p className="border-t border-line pt-3 text-small text-ink-muted">{request.note}</p>}
                <p className="text-small text-ink-faint">
                  Báo giá sẽ gắn với yêu cầu này. Quay lại{' '}
                  <Link to="/requests" className="text-primary hover:underline">
                    danh sách yêu cầu
                  </Link>
                  .
                </p>
              </div>
            )}
          </Card>
        )}

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
