import { useMemo, useState } from 'react';
import { Check } from 'lucide-react';
import { useQueries } from '@tanstack/react-query';
import { receiptsApi, useUnitLabel, type RawMaterialLot } from '@/features/receipts';
import type { ID } from '@/shared/domain/entities';
import { useAction } from '@/shared/api/useAction';
import { date, dateTimeFull, money, num, todayISO } from '@/shared/lib/format';
import { Alert, Button, DateInput, ErrorState, Field, InfoTip, Input, PageLoading } from '@/shared/ui';
import { productionApi } from '../api';
import { useMaterialIssue } from '../hooks';
import type { ProductionOrder } from '../types';
import { ActionBar, HistoryCard, OverviewCard, Section, StepLayout, SuffixInput, orderHistory } from './OrderShell';

const DEFECT_REASONS = ['Rách màng', 'Sai định lượng', 'Hỏng khi đóng gói'] as const;

interface LeftoverRow {
  rawMaterialId: ID;
  name: string;
  unit: string;
  issued: number;
  /** Lô gốc (lô xuất sau cùng) — giá và mã lô trả lại lấy theo lô này. */
  sourceLot: RawMaterialLot | null;
  /** Hạn lô trả lại không được muộn hơn hạn sớm nhất trong các lô đã xuất. */
  maxExp: string | null;
}

/** Bước 03 (Cường, dòng 51): báo làm xong — khay đạt, khay hỏng + lý do, nguyên liệu dư trả lại kho (lô -TL). */
export function CompletePanel({ order }: { order: ProductionOrder }) {
  const unitLabel = useUnitLabel();
  const issue = useMaterialIssue(order.materialIssueId);
  const materialIds = useMemo(() => [...new Set((issue.data?.lines ?? []).map((l) => l.rawMaterialId))], [issue.data]);
  const lotQueries = useQueries({
    queries: materialIds.map((id) => ({ queryKey: ['raw-material-lots', id], queryFn: ({ signal }: { signal: AbortSignal }) => receiptsApi.rawMaterialLots(id, false, signal) })),
  });
  const lotsById = new Map<number, RawMaterialLot>();
  for (const q of lotQueries) for (const l of q.data ?? []) lotsById.set(Number(l.id), l);

  const rows: LeftoverRow[] = materialIds.map((id) => {
    const lines = (issue.data?.lines ?? []).filter((l) => l.rawMaterialId === id);
    const lots = lines.map((l) => (l.rawMaterialLotId ? lotsById.get(Number(l.rawMaterialLotId)) : undefined)).filter((l): l is RawMaterialLot => !!l);
    const exps = lots.map((l) => l.expDate).filter((d): d is string => !!d).sort();
    return { rawMaterialId: id, name: lines[0].rawMaterialName, unit: lines[0].unit, issued: lines.reduce((s, l) => s + (l.qtyIssued ?? 0), 0), sourceLot: lots[lots.length - 1] ?? null, maxExp: exps[0] ?? null };
  });

  const [okQty, setOkQty] = useState('');
  const [defectQty, setDefectQty] = useState('');
  const [doneDate, setDoneDate] = useState(todayISO());
  const [reason, setReason] = useState('');
  const [otherReason, setOtherReason] = useState('');
  const [left, setLeft] = useState<Record<number, { qty: string; exp: string }>>({});
  const [touched, setTouched] = useState(false);

  const defaultExp = (r: LeftoverRow) => (r.maxExp && r.maxExp < todayISO(1) ? r.maxExp : todayISO(1));
  const leftOf = (r: LeftoverRow) => left[r.rawMaterialId] ?? { qty: '', exp: defaultExp(r) };
  const patchLeft = (r: LeftoverRow, p: Partial<{ qty: string; exp: string }>) => setLeft({ ...left, [r.rawMaterialId]: { ...leftOf(r), ...p } });

  const ok = Number(okQty);
  const bad = defectQty.trim() === '' ? 0 : Number(defectQty);
  const okValid = Number.isInteger(ok) && ok > 0;
  const badValid = Number.isInteger(bad) && bad >= 0;
  const leftErrors = new Map<number, string>();
  for (const r of rows) {
    const v = leftOf(r);
    const q = v.qty.trim() === '' ? 0 : Number(v.qty);
    if (!(q >= 0) || q > r.issued) leftErrors.set(r.rawMaterialId, `Từ 0 đến ${num(r.issued)}`);
    else if (q > 0 && !v.exp) leftErrors.set(r.rawMaterialId, 'Chọn hạn');
    else if (q > 0 && r.maxExp && v.exp > r.maxExp) leftErrors.set(r.rawMaterialId, `Không quá ${date(r.maxExp)}`);
  }
  const returned = rows.filter((r) => Number(leftOf(r).qty) > 0);
  const issuedCost = order.actualMaterialCost ?? order.estimatedMaterialCost ?? 0;
  const valid = okValid && badValid && leftErrors.size === 0 && !!doneDate;
  const defectReason = bad > 0 ? (reason === 'Khác' ? otherReason.trim() || null : reason || null) : null;
  const made = (okValid ? ok : 0) + (badValid ? bad : 0);
  const okPct = made > 0 ? Math.round(((okValid ? ok : 0) / Math.max(made, order.plannedQty)) * 100) : 0;
  const badPct = made > 0 ? Math.round(((badValid ? bad : 0) / Math.max(made, order.plannedQty)) * 100) : 0;

  const complete = useAction(
    () =>
      productionApi.complete(order.id, {
        actualQty: ok,
        actualProductionCost: order.estimatedProductionCost,
        variantId: null,
        finishedDate: doneDate,
        defectQty: bad || null,
        defectReason,
        leftovers: returned.map((r) => ({ rawMaterialId: r.rawMaterialId, qty: Number(leftOf(r).qty), expDate: leftOf(r).exp })),
      }),
    { success: (r) => `Đã báo làm xong - tạo phiếu nhập ${r.finishedReceiptCode ?? ''} chờ nhập kho` },
  );

  const aside = (
    <>
      <OverviewCard
        items={[
          ['Trạng thái', 'Đang sản xuất'],
          ['Thành phẩm', order.productName],
          ['Số lượng', `${num(order.plannedQty)} khay`],
          ['Cần xong', date(order.plannedDueDate)],
          ['Phiếu xuất', <><span className="font-mono">{order.materialIssueCode ?? '—'}</span> · đã xuất</>],
          ['Tiền nguyên liệu', money(issuedCost)],
        ]}
      />
      <HistoryCard events={orderHistory(order, issue.data)} />
    </>
  );
  if (issue.isLoading) return <PageLoading />;
  if (issue.error) return <ErrorState error={issue.error} onRetry={issue.refetch} />;

  return (
    <StepLayout aside={aside}>
      <Section flush title="Báo sản lượng" subtitle="Làm xong thì ghi số khay đạt và số khay hỏng">
        <div className="grid grid-cols-1 gap-px border-b border-line bg-line sm:grid-cols-3">
          <div className="bg-card px-4 py-3"><div className="text-small text-ink-muted">Kế hoạch</div><div className="text-h1 tabular-nums">{num(order.plannedQty)} <span className="text-body font-normal text-ink-faint">khay</span></div></div>
          <div className="bg-card px-4 py-3"><div className="text-small text-ink-muted">Nguyên liệu đã xuất</div><div className="text-h1 tabular-nums">{money(issuedCost)}</div></div>
          <div className="bg-card px-4 py-3"><div className="text-small text-ink-muted">Bắt đầu làm</div><div className="text-h2 tabular-nums">{dateTimeFull(issue.data?.completedAt)}</div></div>
        </div>
        <div className="grid gap-3 p-4 sm:grid-cols-3">
          <Field label="Số khay đạt" required error={touched && !okValid ? 'Nhập số khay nguyên > 0' : undefined} tip="Điền sẵn vào phiếu nhập ở bước 4. Làm vượt kế hoạch vẫn lưu được, chỉ cảnh báo.">
            <SuffixInput suffix="khay" type="number" min={1} className="text-right" value={okQty} onChange={(e) => setOkQty(e.target.value)} />
          </Field>
          <Field label="Số khay hỏng (không bắt buộc)" error={touched && !badValid ? 'Số nguyên ≥ 0' : undefined} tip="Khay hỏng lúc đang làm, chưa vào kho nên ghi ở đây, không đi qua Báo hỏng. Giá vốn chia cho số khay thật nhập kho.">
            <SuffixInput suffix="khay" type="number" min={0} className="text-right" placeholder="0" value={defectQty} onChange={(e) => setDefectQty(e.target.value)} />
          </Field>
          <Field label="Ngày làm xong" required>
            <DateInput max={todayISO()} value={doneDate} onChange={setDoneDate} />
          </Field>
          <div className="sm:col-span-3">
            <div className="mb-1.5 text-small font-medium text-ink-muted">Lý do hỏng <span className="font-normal text-ink-faint">(chọn nhanh hoặc tự ghi)</span></div>
            <div role="radiogroup" aria-label="Lý do hỏng" className="flex flex-wrap items-center gap-2">
              {[...DEFECT_REASONS, 'Khác'].map((r) => (
                <button
                  key={r}
                  type="button"
                  role="radio"
                  aria-checked={reason === r}
                  onClick={() => setReason(reason === r ? '' : r)}
                  className={`inline-flex h-8 items-center gap-1 rounded-full border px-3 text-small ${reason === r ? 'border-primary bg-primary/10 font-semibold text-primary' : 'border-line bg-card text-ink-muted hover:bg-rowalt'}`}
                >
                  {reason === r && <Check size={13} aria-hidden />}{r === 'Khác' ? 'Khác…' : r}
                </button>
              ))}
              {reason === 'Khác' && <Input aria-label="Lý do khác" className="!w-64" placeholder="Ghi lý do" value={otherReason} onChange={(e) => setOtherReason(e.target.value)} />}
            </div>
          </div>
          <div className="sm:col-span-3">
            <div className="flex justify-between text-small text-ink-muted">
              <span>So với kế hoạch</span>
              <b className="text-ink">{okValid ? `${num(ok)}/${num(order.plannedQty)} khay đạt · ${Math.round((ok / order.plannedQty) * 100)}%` : '—'}</b>
            </div>
            <div className="mt-1.5 flex h-2.5 overflow-hidden rounded-full bg-rowalt" aria-hidden>
              <div className="bg-green-fg" style={{ width: `${okPct}%` }} />
              <div className="bg-red-fg" style={{ width: `${badPct}%` }} />
            </div>
            <div className="mt-1.5 flex gap-4 text-small text-ink-muted">
              <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-green-fg" />Đạt {okValid ? num(ok) : 0}</span>
              <span className="inline-flex items-center gap-1.5"><span className="h-2.5 w-2.5 rounded-sm bg-red-fg" />Hỏng {badValid ? num(bad) : 0}</span>
            </div>
          </div>
        </div>
        {okValid && ok > order.plannedQty && <div className="px-4 pb-4"><Alert tone="warning">Vượt kế hoạch {num(ok - order.plannedQty)} khay - vẫn lưu được.</Alert></div>}
      </Section>

      <Section
        flush
        title="Nguyên liệu dư trả lại kho (không bắt buộc)"
        subtitle={
          <span className="inline-flex items-center gap-1">
            Mỗi dòng có số dư sẽ tạo một <b>lô trả lại (-TL)</b> mới, giá theo lô gốc
            <InfoTip label="Ghi chú hạn lô trả lại">Hạn mặc định hôm nay + 1 ngày. Sửa được nhưng không muộn hơn hạn lô gốc; dư từ nhiều lô thì lấy hạn sớm nhất.</InfoTip>
          </span>
        }
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] border-collapse text-body">
            <thead>
              <tr className="bg-thead text-left text-micro uppercase tracking-wide text-ink-muted">
                <th className="px-4 py-2.5 font-semibold">Nguyên liệu</th>
                <th className="px-3 py-2.5 text-right font-semibold">Đã xuất</th>
                <th className="w-[140px] px-3 py-2.5 font-semibold">Số dư</th>
                <th className="w-[180px] px-3 py-2.5 font-semibold">Hạn lô trả lại</th>
                <th className="px-4 py-2.5 text-right font-semibold">Trừ khỏi giá vốn</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => {
                const v = leftOf(r);
                const q = Number(v.qty) || 0;
                const err = touched ? leftErrors.get(r.rawMaterialId) : undefined;
                return (
                  <tr key={r.rawMaterialId} className="border-t border-line align-top">
                    <td className="px-4 py-2.5">
                      <div className="font-semibold">{r.name}</div>
                      <div className="text-small text-ink-faint">{r.sourceLot ? `Lô gốc ${r.sourceLot.lotCode}${r.sourceLot.unitPrice ? ` · ${money(r.sourceLot.unitPrice)}/${unitLabel(r.unit)}` : ''}` : '—'}</div>
                    </td>
                    <td className="px-3 py-2.5 text-right tabular-nums text-ink-muted">{num(r.issued)} {unitLabel(r.unit)}</td>
                    <td className="px-3 py-1.5">
                      <SuffixInput suffix={unitLabel(r.unit)} aria-label={`Số dư ${r.name}`} aria-invalid={!!err || undefined} type="number" min={0} step="0.1" className={`text-right ${q > 0 ? '' : 'bg-rowalt'}`} placeholder="0" value={v.qty} onChange={(e) => patchLeft(r, { qty: e.target.value })} />
                      {err && <p className="mt-0.5 text-small text-red-fg">{err}</p>}
                    </td>
                    <td className="px-3 py-1.5">
                      {q > 0 ? (
                        <DateInput aria-label={`Hạn lô trả lại ${r.name}`} min={todayISO()} max={r.maxExp ?? undefined} value={v.exp} onChange={(d) => patchLeft(r, { exp: d })} />
                      ) : (
                        <div className="field-control flex items-center bg-rowalt text-ink-faint" aria-hidden>—</div>
                      )}
                    </td>
                    <td className={`px-4 py-2.5 text-right font-semibold tabular-nums ${q > 0 ? 'text-green-fg' : 'text-ink-faint'}`}>{q > 0 ? `−${money(Math.round(q * (r.sourceLot?.unitPrice ?? 0)))}` : '0 ₫'}</td>
                  </tr>
                );
              })}
              {rows.length === 0 && <tr><td colSpan={5} className="px-4 py-6 text-center text-ink-muted">Phiếu xuất chưa có dòng nguyên liệu nào</td></tr>}
            </tbody>
          </table>
        </div>
      </Section>

      <ActionBar
        actions={
          <Button
            variant="primary"
            icon={<Check size={15} strokeWidth={2.6} />}
            loading={complete.isPending}
            onClick={() => {
              setTouched(true);
              if (valid) complete.mutate();
            }}
          >
            Báo làm xong
          </Button>
        }
      >
        <span>Báo xong: tự tạo phiếu nhập thành phẩm (Chờ nhập), điền sẵn {okValid ? num(ok) : '…'} khay</span>
        <span>
          {returned.map((r) => <span key={r.rawMaterialId}>Tạo lô <span className="font-mono">{r.sourceLot ? `${r.sourceLot.lotCode}-TL` : 'trả lại'}</span> {num(Number(leftOf(r).qty))} {unitLabel(r.unit)} · </span>)}
          lệnh chuyển "Chờ nhập kho"
        </span>
      </ActionBar>
    </StepLayout>
  );
}
