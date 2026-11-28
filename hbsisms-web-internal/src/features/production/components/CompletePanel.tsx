import { useEffect, useState } from 'react';
<<<<<<< HEAD
import { Check, CircleAlert, PackagePlus } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
=======
import { Check } from 'lucide-react';
>>>>>>> origin/main
import { canWarehouseOps, useAuth } from '@/features/auth';
import { useUnitLabel } from '@/features/receipts';
import { useAction } from '@/shared/api/useAction';
import { date, dateTimeFull, formatThousands, money, num, parseThousands, todayISO } from '@/shared/lib/format';
import { Alert, Button, Chip, DateInput, ErrorState, Field, InfoTip, Input, PageLoading } from '@/shared/ui';
import { productionApi } from '../api';
import { useMaterialIssue, usePackagingPreview, useProductVariants } from '../hooks';
import type { ProductionOrder } from '../types';
import { ActionBar, HistoryCard, OverviewCard, Section, StepLayout, SuffixInput, orderHistory } from './OrderShell';

const DEFECT_REASONS = ['Rách màng', 'Sai định lượng', 'Hỏng khi đóng gói'] as const;

type PackMode = 'ONE_KG' | 'HALF_KG' | 'SPLIT';

function isOneKgPack(label: string | null | undefined, weightG: number | null | undefined) {
  if (weightG === 1000) return true;
  if (weightG === 500) return false;
  const t = (label ?? '').toLowerCase();
  if (/0[,.]5|500g|1\/2/.test(t)) return false;
  return /1\s*kg|1000g/.test(t);
}
function isHalfKgPack(label: string | null | undefined, weightG: number | null | undefined) {
  if (weightG === 500) return true;
  if (weightG === 1000) return false;
  return /0[,.]5|500g|1\/2/.test((label ?? '').toLowerCase());
}

/** Bước 03: báo làm xong — khay đạt/hỏng, đóng gói, chi phí SX. Hao hụt NVL đã nằm trong BOM lúc lập KH/xuất. */
export function CompletePanel({ order }: { order: ProductionOrder }) {
  const navigate = useNavigate();
  const { user } = useAuth();
  const canComplete = canWarehouseOps(user?.roleCode);
  const unitLabel = useUnitLabel();
  const issue = useMaterialIssue(order.materialIssueId);
  const variantsQ = useProductVariants(order.productId);
  const oneKgVariant = (variantsQ.data ?? []).find((v) => isOneKgPack(v.packLabel, v.unitWeightG)) ?? null;
  const halfKgVariant = (variantsQ.data ?? []).find((v) => isHalfKgPack(v.packLabel, v.unitWeightG)) ?? null;
  const canSplit = !!oneKgVariant && !!halfKgVariant;

  const [okQty, setOkQty] = useState('');
  const [defectQty, setDefectQty] = useState('');
  const [doneDate, setDoneDate] = useState(todayISO());
  const [reason, setReason] = useState('');
  const [otherReason, setOtherReason] = useState('');
  const [touched, setTouched] = useState(false);
  const [packMode, setPackMode] = useState<PackMode>('ONE_KG');
  const [qtyOneKg, setQtyOneKg] = useState('');
  const [qtyHalfKg, setQtyHalfKg] = useState('');
  const [prodCost, setProdCost] = useState(
    order.estimatedProductionCost != null && order.estimatedProductionCost > 0
      ? String(Math.round(order.estimatedProductionCost))
      : '',
  );

  useEffect(() => {
    if (oneKgVariant) setPackMode('ONE_KG');
    else if (halfKgVariant) setPackMode('HALF_KG');
  }, [oneKgVariant?.id, halfKgVariant?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  /** Đổi số đạt → kéo đóng gói theo kg quy đổi (1×1kg + 2×0,5kg = 2 đạt). */
  useEffect(() => {
    if (packMode !== 'SPLIT') return;
    const target = Number(okQty);
    if (!Number.isInteger(target) || target <= 0) return;
    let one = qtyOneKg.trim() === '' ? 0 : Number(qtyOneKg);
    let half = qtyHalfKg.trim() === '' ? 0 : Number(qtyHalfKg);
    if (!Number.isFinite(one) || !Number.isFinite(half)) return;
    one = Math.max(0, Math.floor(one));
    half = Math.max(0, Math.floor(half));
    let weight = one + half * 0.5;
    if (weight <= target) return;
    if (one > target) {
      one = target;
      half = 0;
    } else {
      const maxHalf = Math.floor((target - one) * 2);
      half = Math.min(half, maxHalf);
    }
    if (String(one) !== qtyOneKg) setQtyOneKg(String(one));
    if (String(half) !== qtyHalfKg) setQtyHalfKg(String(half));
  }, [okQty, packMode]); // eslint-disable-line react-hooks/exhaustive-deps

  const plan = order.plannedQty;
  const ok = Number(okQty);
  const bad = defectQty.trim() === '' ? 0 : Number(defectQty);
  const okValid = Number.isInteger(ok) && ok > 0 && ok <= plan;
  const badValid = Number.isInteger(bad) && bad >= 0;
  const overPlan =
    (Number.isInteger(ok) && ok > plan)
    || (okValid && badValid && bad > 0 && ok + bad > plan);
  const okError = !okQty.trim()
    ? undefined
    : !Number.isInteger(ok) || ok <= 0
      ? 'Nhập số khay nguyên > 0'
      : ok > plan
        ? `Không vượt kế hoạch ${num(plan)} khay`
        : undefined;
  const badError = !badValid
    ? 'Số nguyên ≥ 0'
    : okValid && bad > 0 && ok + bad > plan
      ? `Đạt ${num(ok)} + hỏng ${num(bad)} vượt kế hoạch ${num(plan)}`
      : undefined;
  const nOne = qtyOneKg.trim() === '' ? 0 : Number(qtyOneKg);
  const nHalf = qtyHalfKg.trim() === '' ? 0 : Number(qtyHalfKg);
  /** Quy đổi khay 1 kg: 1×1kg + 2×0,5kg = 2 (so sánh bằng số nguyên ×2 tránh lỗi float). */
  const splitWeight2 = Number.isInteger(nOne) && Number.isInteger(nHalf) ? nOne * 2 + nHalf : NaN;
  const splitWeight = Number.isFinite(splitWeight2) ? splitWeight2 / 2 : NaN;
  const splitWeightOk = okValid && splitWeight2 === ok * 2;
  const splitTyped = qtyOneKg.trim() !== '' || qtyHalfKg.trim() !== '';
  const splitValid =
    packMode !== 'SPLIT'
    || (okValid
      && Number.isInteger(nOne)
      && nOne >= 0
      && Number.isInteger(nHalf)
      && nHalf >= 0
      && splitWeightOk
      && (nOne > 0 || nHalf > 0));
  const packError =
    packMode === 'SPLIT' && !okValid
      ? 'Nhập số khay đạt trước, rồi mới chia khay 1 kg / 0,5 kg'
      : !okValid
        ? undefined
        : packMode === 'ONE_KG' && !oneKgVariant && !halfKgVariant
          ? 'Sản phẩm chưa có quy cách đang bán'
          : packMode === 'ONE_KG' && !oneKgVariant
            ? 'Chưa có quy cách khay 1 kg'
            : packMode === 'HALF_KG' && !halfKgVariant
              ? 'Chưa có quy cách khay 0,5 kg'
              : packMode === 'SPLIT' && !canSplit
                ? 'Cần cả quy cách 1 kg và 0,5 kg để chia'
                : packMode === 'SPLIT' && Number.isFinite(splitWeight) && splitWeight > ok
                  ? `Đang chia ${num(splitWeight)} (quy đổi 1 kg), vượt số đạt ${num(ok)}`
                  : packMode === 'SPLIT' && !splitValid
                    ? Number.isFinite(splitWeight)
                      ? `Chia khay chưa khớp: cần đủ ${num(ok)} (quy đổi 1 kg), hiện mới ${num(splitWeight)}`
                      : `Chia khay chưa khớp số đạt ${num(ok)}`
                    : undefined;

  const setSplitOne = (raw: string) => {
    if (!okValid) {
      setQtyOneKg(raw);
      return;
    }
    const v = Number(raw);
    if (raw.trim() === '' || !Number.isFinite(v)) {
      setQtyOneKg(raw);
      return;
    }
    const capped = Math.max(0, Math.min(Math.floor(v), ok));
    setQtyOneKg(String(capped));
    const halfMax = Math.floor((ok - capped) * 2);
    if (nHalf > halfMax) setQtyHalfKg(halfMax > 0 ? String(halfMax) : '0');
  };
  const setSplitHalf = (raw: string) => {
    if (!okValid) {
      setQtyHalfKg(raw);
      return;
    }
    const v = Number(raw);
    if (raw.trim() === '' || !Number.isFinite(v)) {
      setQtyHalfKg(raw);
      return;
    }
    const capped = Math.max(0, Math.min(Math.floor(v), ok * 2));
    setQtyHalfKg(String(capped));
    const oneMax = Math.floor(ok - capped * 0.5);
    if (nOne > oneMax) setQtyOneKg(oneMax > 0 ? String(oneMax) : '0');
  };
  const issuedCost = order.actualMaterialCost ?? order.estimatedMaterialCost ?? 0;
  const hasDefect = badValid && bad > 0;
  const defectReason = hasDefect ? (reason === 'Khác' ? otherReason.trim() || null : reason || null) : null;
  const reasonError = hasDefect && !defectReason ? (reason === 'Khác' ? 'Ghi lý do khác' : 'Chọn lý do hỏng') : undefined;

  /** Số khay vật lý → trừ VT đóng gói (xốp, túi…). */
  const physicalTrays =
    packMode === 'SPLIT'
      ? splitValid
        ? nOne + nHalf
        : 0
      : packMode === 'HALF_KG'
        ? okValid
          ? ok * 2
          : 0
        : okValid
          ? ok
          : 0;
  const packPreview = usePackagingPreview(order.productId, physicalTrays);
  const shortPack = (packPreview.data?.materials ?? []).filter((m) => !m.sufficient);
  const packStockError =
    physicalTrays > 0 && packPreview.data && !packPreview.data.allMaterialsSufficient
      ? 'Tồn vật tư đóng gói không đủ — kiểm tra bảng bên dưới'
      : undefined;
  const openPackReceipt = () => {
    const lines = shortPack
      .map((m) => {
        const need = Math.max(0, Math.ceil(m.requiredQty - m.onHandQty));
        return need > 0 ? `${m.rawMaterialId}:${need}` : null;
      })
      .filter(Boolean)
      .join(',');
    const q = new URLSearchParams({
      new: 'raw',
      source: `Lệnh ${order.code} · VT đóng gói`,
      returnTo: `/production-orders/${order.id}`,
    });
    if (lines) q.set('lines', lines);
    navigate(`/receipts?${q.toString()}`);
  };
  const actualProdCost = Number(parseThousands(prodCost)) || 0;
  const prodCostError =
    prodCost.trim() !== '' && !(actualProdCost >= 0) ? 'Số tiền ≥ 0' : undefined;
  const valid =
    okValid
    && badValid
    && !overPlan
    && !reasonError
    && !packError
    && !packStockError
    && !prodCostError
    && !!doneDate;

  const setOkCapped = (raw: string) => {
    if (raw.trim() === '') {
      setOkQty('');
      return;
    }
    const v = Number(raw);
    if (!Number.isFinite(v)) {
      setOkQty(raw);
      return;
    }
    const capped = Math.max(0, Math.min(Math.floor(v), plan));
    setOkQty(String(capped));
    const halfMax = Math.max(0, plan - capped);
    if (defectQty.trim() !== '' && Number(defectQty) > halfMax) {
      setDefectQty(halfMax > 0 ? String(halfMax) : '0');
    }
  };
  const setBadCapped = (raw: string) => {
    if (raw.trim() === '') {
      setDefectQty('');
      return;
    }
    const v = Number(raw);
    if (!Number.isFinite(v)) {
      setDefectQty(raw);
      return;
    }
    const maxBad = okValid ? Math.max(0, plan - ok) : plan;
    setDefectQty(String(Math.max(0, Math.min(Math.floor(v), maxBad))));
  };
  const made = (okValid ? ok : 0) + (badValid ? bad : 0);
  const okPct = made > 0 ? Math.round(((okValid ? ok : 0) / Math.max(made, order.plannedQty)) * 100) : 0;
  const badPct = made > 0 ? Math.round(((badValid ? bad : 0) / Math.max(made, order.plannedQty)) * 100) : 0;

  const buildPacks = () => {
    if (packMode === 'SPLIT' && oneKgVariant && halfKgVariant) {
      const packs = [];
      if (nOne > 0) packs.push({ variantId: oneKgVariant.id, qty: nOne });
      if (nHalf > 0) packs.push({ variantId: halfKgVariant.id, qty: nHalf });
      return packs;
    }
    // Đạt theo quy đổi 1 kg → tất cả 0,5 kg = gấp đôi số khay vật lý.
    if (packMode === 'HALF_KG' && halfKgVariant) {
      return [{ variantId: halfKgVariant.id, qty: ok * 2 }];
    }
    const v = oneKgVariant ?? halfKgVariant ?? (variantsQ.data ?? [])[0];
    if (!v) return null;
    if (!oneKgVariant && halfKgVariant) {
      return [{ variantId: halfKgVariant.id, qty: ok * 2 }];
    }
    return [{ variantId: v.id, qty: ok }];
  };

  const complete = useAction(
    () =>
      productionApi.complete(order.id, {
        actualQty: ok,
        actualProductionCost: actualProdCost,
        variantId: null,
        finishedDate: doneDate,
        defectQty: bad || null,
        defectReason,
        leftovers: [],
        packs: buildPacks(),
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
          ['Chi phí SX dự tính', money(order.estimatedProductionCost)],
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
        <div className="grid items-end gap-3 p-4 sm:grid-cols-2 lg:grid-cols-4">
          <Field
            label="Số khay đạt"
            required
            error={touched && okError ? okError : undefined}
            tip={`Theo quy đổi khay 1 kg (BOM). Tối đa = kế hoạch ${num(plan)}. Ví dụ đạt 2 → có thể đóng 1×1kg + 2×0,5kg.`}
          >
            <SuffixInput
              suffix="khay"
              type="number"
              min={1}
              max={plan}
              step={1}
              steppable
              className="text-right"
              value={okQty}
              onChange={(e) => setOkCapped(e.target.value)}
            />
          </Field>
          <Field
            label="Số khay hỏng (không bắt buộc)"
            error={touched && badError ? badError : undefined}
            tip={`Khay hỏng lúc đang làm, chưa vào kho. Đạt + hỏng ≤ kế hoạch (${num(plan)}).`}
          >
            <SuffixInput
              suffix="khay"
              type="number"
              min={0}
              max={okValid ? Math.max(0, plan - ok) : plan}
              step={1}
              steppable
              className="text-right"
              placeholder="0"
              value={defectQty}
              onChange={(e) => setBadCapped(e.target.value)}
            />
          </Field>
          <Field label="Ngày làm xong" required>
            <DateInput max={todayISO()} value={doneDate} onChange={setDoneDate} />
          </Field>
          <Field
            label="Chi phí SX thực tế"
            tip={
              order.estimatedProductionCost
                ? `Không bắt buộc. Tiền công, điện, gas… cả lệnh. Dự tính lúc lập lệnh: ${money(order.estimatedProductionCost)}.`
                : 'Không bắt buộc. Tiền công, điện, gas… cả lệnh. Để trống = 0.'
            }
            error={touched && prodCostError ? prodCostError : undefined}
          >
            <SuffixInput
              suffix="₫"
              inputMode="numeric"
              steppable
              step={1000}
              min={0}
              className="text-right"
              placeholder="0"
              value={formatThousands(prodCost)}
              onChange={(e) => setProdCost(parseThousands(e.target.value))}
            />
          </Field>
          {hasDefect && (
          <div className="sm:col-span-3">
            <div className="mb-1.5 text-small font-medium text-ink-muted">Lý do hỏng <span className="text-red-fg">*</span> <span className="font-normal text-ink-faint">(chọn nhanh hoặc tự ghi)</span></div>
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
            {touched && reasonError && <p className="mt-1 text-small text-red-fg">{reasonError}</p>}
          </div>
          )}
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
      </Section>

      <Section
        flush
        title="Đóng gói thành phẩm"
        subtitle="Số đạt = quy đổi khay 1 kg · VT đóng gói (xốp, màng) trừ theo số khay vật lý vừa chọn"
      >
        <div className="space-y-3 p-4">
          <div role="radiogroup" aria-label="Quy cách đóng gói" className="flex flex-wrap gap-2">
            {(
              [
                ['ONE_KG', 'Tất cả khay 1 kg', !!oneKgVariant],
                ['HALF_KG', 'Tất cả khay 0,5 kg', !!halfKgVariant],
                ['SPLIT', 'Chia 1 kg và 0,5 kg', canSplit],
              ] as const
            ).map(([mode, label, enabled]) => (
              <button
                key={mode}
                type="button"
                role="radio"
                aria-checked={packMode === mode}
                disabled={!enabled}
                onClick={() => setPackMode(mode)}
                className={`inline-flex h-8 items-center gap-1 rounded-full border px-3 text-small disabled:cursor-not-allowed disabled:opacity-40 ${
                  packMode === mode ? 'border-primary bg-primary/10 font-semibold text-primary' : 'border-line bg-card text-ink-muted hover:bg-rowalt'
                }`}
              >
                {packMode === mode && <Check size={13} aria-hidden />}
                {label}
              </button>
            ))}
          </div>
          {packMode === 'SPLIT' && (
            <div className="grid gap-3 sm:grid-cols-2">
              <Field
                label="Khay 1 kg (vật lý)"
                tip={oneKgVariant ? oneKgVariant.sku : undefined}
                error={touched && packMode === 'SPLIT' && packError && !splitWeightOk ? packError : undefined}
              >
                <SuffixInput
                  suffix="khay"
                  type="number"
                  min={0}
                  max={okValid ? ok : undefined}
                  step={1}
                  steppable
                  disabled={!okValid}
                  className="text-right"
                  placeholder={okValid ? `0–${ok}` : '…'}
                  value={qtyOneKg}
                  onChange={(e) => setSplitOne(e.target.value)}
                />
              </Field>
              <Field
                label="Khay 0,5 kg (vật lý)"
                tip={halfKgVariant ? `${halfKgVariant.sku} · tối đa ${okValid ? ok * 2 : '…'}` : undefined}
              >
                <SuffixInput
                  suffix="khay"
                  type="number"
                  min={0}
                  max={okValid ? ok * 2 : undefined}
                  step={1}
                  steppable
                  disabled={!okValid}
                  className="text-right"
                  placeholder={okValid ? `0–${ok * 2}` : '…'}
                  value={qtyHalfKg}
                  onChange={(e) => setSplitHalf(e.target.value)}
                />
              </Field>
            </div>
          )}
          {packMode === 'ONE_KG' && oneKgVariant && (
            <p className="text-small text-ink-muted">
              {okValid ? num(ok) : '…'} khay 1 kg → <span className="font-mono text-ink">{oneKgVariant.sku}</span>
              {oneKgVariant.packLabel ? ` · ${oneKgVariant.packLabel}` : ''}
            </p>
          )}
          {packMode === 'HALF_KG' && halfKgVariant && (
            <p className="text-small text-ink-muted">
              Đạt {okValid ? num(ok) : '…'} → nhập <b className="text-ink">{okValid ? num(ok * 2) : '…'} khay 0,5 kg</b>{' '}
              (<span className="font-mono">{halfKgVariant.sku}</span>)
            </p>
          )}
          {(touched || (packMode === 'SPLIT' && splitTyped)) && packError && (
            <p className="text-small text-red-fg">{packError}</p>
          )}
          {touched && packStockError && <p className="text-small text-red-fg">{packStockError}</p>}
          {!variantsQ.isLoading && !oneKgVariant && !halfKgVariant && (
            <Alert tone="warning">Sản phẩm chưa có quy cách 1 kg / 0,5 kg đang bán — kiểm tra danh mục sản phẩm.</Alert>
          )}
          {physicalTrays > 0 && (
            <div className="space-y-2 border-t border-line pt-3">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <p className="text-small font-semibold text-ink">
                  VT đóng gói sẽ trừ · {num(physicalTrays)} khay vật lý
                </p>
                {packPreview.data && (
                  <p className="text-small tabular-nums text-ink-muted">
                    Ước ~{money(packPreview.data.estimatedMaterialCost)}
                  </p>
                )}
              </div>
              {packPreview.isLoading && <p className="text-small text-ink-faint">Đang tính định mức đóng gói…</p>}
              {packPreview.error && (
                <Alert tone="warning">Không lấy được xem trước VT đóng gói — vẫn có thể báo xong nếu kho đủ.</Alert>
              )}
              {packPreview.data && packPreview.data.materials.length === 0 && (
                <p className="text-small text-ink-faint">BOM không có dòng đếm (xốp, túi…) — không trừ thêm.</p>
              )}
              {packPreview.data && packPreview.data.materials.length > 0 && (
                <div className="overflow-x-auto rounded-md border border-line">
                  <table className="w-full min-w-[520px] border-collapse text-body">
                    <thead>
                      <tr className="bg-thead text-left text-micro uppercase tracking-wide text-ink-muted">
                        <th className="px-3 py-2 font-semibold">Vật tư</th>
                        <th className="px-3 py-2 text-right font-semibold">Cần</th>
                        <th className="px-3 py-2 text-right font-semibold">Tồn dùng được</th>
                        <th className="px-3 py-2 text-right font-semibold">Trạng thái</th>
                      </tr>
                    </thead>
                    <tbody>
                      {packPreview.data.materials.map((m) => (
                        <tr key={m.rawMaterialId} className="border-t border-line">
                          <td className="px-3 py-2">
                            <div className="font-medium text-ink">{m.rawMaterialName}</div>
                            <div className="font-mono text-small text-ink-faint">{m.rawMaterialCode}</div>
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums">
                            {num(m.requiredQty)} {unitLabel(m.unit)}
                          </td>
                          <td className="px-3 py-2 text-right tabular-nums text-ink-muted">
                            {num(m.onHandQty)} {unitLabel(m.unit)}
                          </td>
                          <td className="px-3 py-2 text-right">
                            <Chip tone={m.sufficient ? 'green' : 'red'}>{m.stockStatus}</Chip>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
              {shortPack.length > 0 && (
                <div className="flex flex-wrap items-center gap-2 rounded-md border border-red-fg/25 bg-red-bg/40 px-3 py-2.5">
                  <CircleAlert size={16} className="shrink-0 text-red-fg" aria-hidden />
                  <span className="min-w-0 flex-1 text-small font-medium text-red-fg">
                    Thiếu {shortPack.length} vật tư đóng gói — nhập thêm rồi quay lại lệnh
                  </span>
                  <Button className="!h-8 !px-3 text-small" icon={<PackagePlus size={14} />} onClick={openPackReceipt}>
                    Tạo phiếu nhập vật tư
                  </Button>
                </div>
              )}
            </div>
          )}
        </div>
      </Section>

      <ActionBar
        actions={
          canComplete ? (
            <Button
              variant="primary"
              icon={<Check size={15} strokeWidth={2.6} />}
              disabled={!!packStockError}
              loading={complete.isPending}
              onClick={() => {
                setTouched(true);
                if (valid) complete.mutate();
              }}
            >
              Báo làm xong
            </Button>
          ) : undefined
        }
      >
        <span>
<<<<<<< HEAD
          {packStockError
            ? 'Nhập đủ vật tư đóng gói rồi F5 / quay lại lệnh mới báo làm xong.'
            : canComplete
              ? `Báo xong: tạo phiếu nhập TP theo đóng gói · đạt ${okValid ? num(ok) : '…'} (quy đổi 1 kg)`
              : 'Chỉ nhân viên kho / Chủ / Admin được báo làm xong.'}
=======
          {canComplete
            ? `Báo xong: tạo phiếu nhập TP theo đóng gói · đạt ${okValid ? num(ok) : '…'} (quy đổi 1 kg)`
            : 'Chỉ nhân viên kho / Chủ / Admin được báo làm xong.'}
>>>>>>> origin/main
        </span>
        <span>Hao hụt NVL đã tính trong BOM lúc lập KH/xuất · lệnh chuyển "Chờ nhập kho"</span>
      </ActionBar>
    </StepLayout>
  );
}
