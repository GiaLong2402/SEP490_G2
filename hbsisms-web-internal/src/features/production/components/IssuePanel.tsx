import { useState } from 'react';
import { PackageMinus, Printer, TriangleAlert } from 'lucide-react';
import { useQueries } from '@tanstack/react-query';
import { isOwnerOrAdmin, useAuth } from '@/features/auth';
import { DiscardLotDialog, isLotExpired, RawMaterialLotsModal, receiptsApi, useUnitLabel, type LotsModalMaterial, type RawMaterialLot } from '@/features/receipts';
import { useAction } from '@/shared/api/useAction';
import { date, dateTimeFull, daysUntil, money, num } from '@/shared/lib/format';
import { Button, Chip, ErrorState, Field, InfoTip, Input, PageLoading } from '@/shared/ui';
import { productionApi } from '../api';
import { useMaterialIssue } from '../hooks';
import type { MaterialIssueLine, ProductionOrder } from '../types';
import { ActionBar, HistoryCard, OverviewCard, Section, StepLayout, orderHistory } from './OrderShell';

interface Slice { lot: RawMaterialLot; take: number; amount: number; unitCost: number; costSource: 'WAC' | 'LOT' }
interface Plan {
  line: MaterialIssueLine;
  slices: Slice[];
  expired: RawMaterialLot[];
  short: number;
  total: number;
  wacUnitCost: number | null;
}

const round3 = (n: number) => Math.round(n * 1000) / 1000;

/**
 * Chọn lô FEFO; định giá theo WAC liên hoàn NVL (fallback giá lô nếu chưa có WAC).
 * Danh sách lô từ API đã theo thứ tự FEFO.
 */
function allocate(line: MaterialIssueLine, lots: RawMaterialLot[], wacUnitCost: number | null): Plan {
  const usable = lots.filter((l) => l.qtyRemaining > 0 && !isLotExpired(l));
  const expired = lots.filter((l) => l.qtyRemaining > 0 && isLotExpired(l));
  const slices: Slice[] = [];
  let left = line.qtyRequired;
  for (const lot of usable) {
    if (left <= 0) break;
    const take = round3(Math.min(left, lot.qtyRemaining));
    const useWac = wacUnitCost != null && Number.isFinite(wacUnitCost);
    const unitCost = useWac ? wacUnitCost : (lot.unitPrice ?? 0);
    slices.push({
      lot,
      take,
      unitCost,
      costSource: useWac ? 'WAC' : 'LOT',
      amount: Math.round(take * unitCost),
    });
    left -= take;
  }
  return {
    line,
    slices,
    expired,
    short: Math.max(0, round3(left)),
    total: slices.reduce((s, x) => s + x.amount, 0),
    wacUnitCost,
  };
}

function MaterialBlock({ plan, no }: { plan: Plan; no: number }) {
  const unit = useUnitLabel()(plan.line.unit);
  const pricedByWac = plan.wacUnitCost != null;
  return (
    <article className="rounded-md border border-line">
      <div className="flex flex-wrap items-center gap-2.5 rounded-t-md border-b border-line bg-rowalt/60 px-3 py-2">
        <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-micro font-semibold text-card">{no}</span>
        <span className="font-semibold">{plan.line.rawMaterialName}</span>
        <span className="font-mono text-small text-ink-faint">{plan.line.rawMaterialCode}</span>
        {plan.short > 0 ? <Chip tone="red">Thiếu {num(plan.short)} {unit}</Chip> : <Chip tone="green">Đã chọn đủ {num(plan.line.qtyRequired)} {unit}</Chip>}
        {pricedByWac ? <Chip tone="neutral">WAC liên hoàn {money(plan.wacUnitCost)}</Chip> : <Chip tone="amber">Chưa có WAC · dùng giá lô</Chip>}
        <span className="ml-auto text-small text-ink-muted">Thành tiền <b className="tabular-nums text-ink">{money(plan.total)}</b></span>
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[720px] border-collapse text-small">
          <thead>
            <tr className="text-left text-micro uppercase tracking-wide text-ink-muted">
              <th className="px-3 py-2 font-semibold">Mã lô (FEFO)</th>
              <th className="px-3 py-2 font-semibold">Hạn sử dụng</th>
              <th className="px-3 py-2 text-right font-semibold">Giá nhập lô</th>
              <th className="px-3 py-2 text-right font-semibold">ĐG xuất</th>
              <th className="px-3 py-2 text-right font-semibold">Còn trong lô</th>
              <th className="px-3 py-2 text-right font-semibold">Lấy</th>
              <th className="px-3 py-2 text-right font-semibold">Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {plan.slices.map((s) => (
              <tr key={s.lot.id} className="border-t border-line">
                <td className="px-3 py-2 font-mono">{s.lot.lotCode}</td>
                <td className="px-3 py-2">
                  {s.lot.expDate ? <><div>{date(s.lot.expDate)}</div><div className="text-ink-faint">Còn {daysUntil(s.lot.expDate)} ngày</div></> : <><div>Không ghi</div><div className="text-ink-faint">Vật tư đóng gói</div></>}
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-ink-muted">{money(s.lot.unitPrice)}</td>
                <td className="px-3 py-2 text-right tabular-nums">
                  <div>{money(s.unitCost)}</div>
                  <div className="text-micro text-ink-faint">{s.costSource === 'WAC' ? 'WAC liên hoàn' : 'giá lô'}</div>
                </td>
                <td className="px-3 py-2 text-right tabular-nums text-ink-muted">{num(s.lot.qtyRemaining)} {unit}</td>
                <td className="px-3 py-2 text-right font-semibold tabular-nums">{num(s.take)} {unit}</td>
                <td className="px-3 py-2 text-right tabular-nums">{money(s.amount)}</td>
              </tr>
            ))}
            {plan.slices.length === 0 && <tr><td colSpan={7} className="px-3 py-4 text-center text-ink-muted">Không còn lô dùng được</td></tr>}
          </tbody>
        </table>
      </div>
    </article>
  );
}

/** Bước 02: xuất NVL — FEFO chọn lô + WAC liên hoàn định giá (fallback giá lô). */
export function IssuePanel({ order }: { order: ProductionOrder }) {
  const { user } = useAuth();
  const canConfirmExport = isOwnerOrAdmin(user?.roleCode);
  const unitOf = useUnitLabel();
  const issue = useMaterialIssue(order.materialIssueId);
  const [note, setNote] = useState('');
  const [discarding, setDiscarding] = useState<{ lot: RawMaterialLot; plan: Plan } | null>(null);
  const [lotsOf, setLotsOf] = useState<LotsModalMaterial | null>(null);
  const lines = issue.data?.lines ?? [];
  const pending = issue.data?.status === 'pending';
  const lotQueries = useQueries({
    queries: pending
      ? lines.map((l) => ({
          queryKey: ['raw-material-lots', l.rawMaterialId],
          queryFn: ({ signal }: { signal: AbortSignal }) => receiptsApi.rawMaterialLots(l.rawMaterialId, false, signal),
        }))
      : [],
  });
  const wacQueries = useQueries({
    queries: pending
      ? lines.map((l) => ({
          queryKey: ['raw-material-avg-unit-cost', l.rawMaterialId],
          queryFn: ({ signal }: { signal: AbortSignal }) => receiptsApi.rawMaterialAvgUnitCost(l.rawMaterialId, signal),
        }))
      : [],
  });
  const confirm = useAction(() => productionApi.confirmIssue(order.materialIssueId!, note.trim() || null), { success: (r) => `Đã xuất kho ${r.code} - lệnh chuyển Đang sản xuất` });

  const aside = (
    <>
      <OverviewCard
        items={[
          ['Trạng thái', 'Đã duyệt'],
          ['Thành phẩm', order.productName],
          ['Số lượng', `${num(order.plannedQty)} khay`],
          ['Cần xong', date(order.plannedDueDate)],
          ['Người duyệt', order.approverName ?? '—'],
          ['Phiếu xuất', <span className="font-mono">{order.materialIssueCode ?? '—'}</span>],
        ]}
      />
      <HistoryCard events={orderHistory(order, issue.data)} />
    </>
  );
  if (!order.materialIssueId) return <StepLayout aside={aside}><Section title="Phiếu xuất nguyên liệu"><p className="text-ink-muted">Lệnh chưa có phiếu xuất nguyên liệu.</p></Section></StepLayout>;
  if (issue.isLoading || lotQueries.some((q) => q.isLoading) || wacQueries.some((q) => q.isLoading)) return <PageLoading />;
  if (issue.error || !issue.data) return <ErrorState error={issue.error} onRetry={issue.refetch} />;

  const plans = pending
    ? lines.map((l, i) => {
        const wac = wacQueries[i]?.data;
        const avg = wac?.source === 'WAC' && wac.avgUnitCost != null ? Number(wac.avgUnitCost) : null;
        return allocate(l, lotQueries[i]?.data ?? [], avg);
      })
    : [];
  const expired = plans.flatMap((p) => p.expired.map((lot) => ({ lot, plan: p })));
  const total = plans.reduce((s, p) => s + p.total, 0);
  const anyShort = plans.some((p) => p.short > 0);
  const estimate = order.estimatedMaterialCost ?? 0;

  return (
    <StepLayout aside={aside}>
      <Section
        flush={!pending}
        title={
          <span className="inline-flex flex-wrap items-center gap-2">
            Phiếu xuất nguyên liệu <span className="num text-primary">{issue.data.code}</span>
            <Chip tone={pending ? 'amber' : issue.data.status === 'completed' ? 'green' : 'neutral'}>{pending ? 'Chờ xuất' : issue.data.status === 'completed' ? 'Đã xuất' : 'Đã hủy'}</Chip>
          </span>
        }
        subtitle={
          pending ? (
            <span className="inline-flex items-center gap-1">
              Tự tạo lúc duyệt · <b>FEFO</b> + <b>WAC liên hoàn</b>
              <InfoTip label="FEFO và WAC liên hoàn">
                FEFO: lô hạn gần lấy trước (lô trả lại -TL ưu tiên). WAC liên hoàn: ĐG =
                (GT tồn trước + GT nhập) / (SL tồn trước + SL nhập), cập nhật mỗi nhập/xuất (không cần kỳ giá
                vốn đang mở). Thành tiền xuất theo ĐG này, không theo giá từng lô. Chưa từng nhập thì tạm dùng
                giá lô.
              </InfoTip>
            </span>
          ) : (
            `Xuất bởi ${issue.data.completerName ?? '—'} · ${dateTimeFull(issue.data.completedAt)}`
          )
        }
      >
        {pending ? (
          <div className="space-y-3.5">
            {expired.map(({ lot, plan }) => (
              <div key={lot.id} role="alert" className="flex flex-wrap items-center gap-3 rounded-md border border-amber-fg/30 bg-amber-bg px-3 py-2.5">
                <TriangleAlert size={18} className="shrink-0 text-amber-fg" aria-hidden />
                <div className="min-w-[260px] flex-1 text-small text-ink">
                  <b>Đã bỏ qua 1 lô hết hạn:</b> <span className="font-mono">{lot.lotCode}</span> · {plan.line.rawMaterialName} · còn {num(lot.qtyRemaining)} {unitOf(plan.line.unit)} · hết hạn {date(lot.expDate)}
                  <div className="text-ink-muted">Máy không xuất lô này. Nếu nguyên liệu đã vứt thì bấm Bỏ lô để trừ khỏi tồn.</div>
                </div>
                <Button variant="danger" className="!h-8 !px-3 text-small" onClick={() => setDiscarding({ lot, plan })}>Bỏ lô</Button>
                <Button
                  className="!h-8 !px-3 text-small"
                  onClick={() => {
                    const lots = lotQueries[plans.indexOf(plan)]?.data ?? [];
                    setLotsOf({ id: plan.line.rawMaterialId, name: plan.line.rawMaterialName, unit: plan.line.unit, qtyOnHand: round3(lots.reduce((s, x) => s + x.qtyRemaining, 0)) });
                  }}
                >
                  Xem các lô
                </Button>
              </div>
            ))}
            {plans.map((p, i) => <MaterialBlock key={p.line.id} plan={p} no={i + 1} />)}
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Người xuất kho"><Input value={user ? `${user.fullName}${user.position ? ` · ${user.position}` : ''}` : ''} disabled /></Field>
              <Field label="Ghi chú xuất kho"><Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: mực lô cũ lấy trước" /></Field>
            </div>
          </div>
        ) : (
          (() => {
            const weightIds = new Set((order.materials ?? []).map((m) => Number(m.rawMaterialId)));
            const weightLines = lines.filter((l) => weightIds.has(Number(l.rawMaterialId)));
            const packLines = lines.filter((l) => !weightIds.has(Number(l.rawMaterialId)));
            return (
              <div className="space-y-1">
                <IssuedTable lines={weightLines.length ? weightLines : lines} />
                {packLines.length > 0 && (
                  <>
                    <div className="border-t border-line px-4 py-2.5 text-small font-semibold text-ink">
                      VT đóng gói (trừ lúc báo làm xong)
                    </div>
                    <IssuedTable lines={packLines} />
                  </>
                )}
              </div>
            );
          })()
        )}
      </Section>

      {pending && (
        <ActionBar
          stacked
          actions={
            <>
              <Button icon={<Printer size={15} />} onClick={() => window.print()}>In phiếu xuất</Button>
              {canConfirmExport && (
                <Button variant="primary" icon={<PackageMinus size={15} />} disabled={anyShort} loading={confirm.isPending} onClick={() => confirm.mutate()}>Xác nhận xuất kho</Button>
              )}
            </>
          }
        >
          <span>
            Tiền nguyên liệu (WAC): <b className="text-body tabular-nums text-ink">{money(total)}</b>{' '}
            {estimate > 0 && (total === estimate ? <span className="text-green-fg">(đúng như tạm tính)</span> : <span className="text-amber-fg">({total > estimate ? '+' : '−'}{money(Math.abs(total - estimate))} so với tạm tính)</span>)}
          </span>
          <span>
            {canConfirmExport
              ? 'Xuất xong: trừ tồn từng lô (FEFO), ghi sổ theo WAC, lệnh chuyển "Đang sản xuất".'
              : 'Chỉ Chủ / Admin được xác nhận xuất kho — bạn có thể xem và in phiếu.'}
          </span>
        </ActionBar>
      )}

      <DiscardLotDialog lot={discarding?.lot ?? null} materialName={discarding?.plan.line.rawMaterialName ?? ''} unit={discarding ? unitOf(discarding.plan.line.unit) : ''} qtyOnHand={discarding ? round3((lotQueries[plans.indexOf(discarding.plan)]?.data ?? []).reduce((s, x) => s + x.qtyRemaining, 0)) : undefined} onClose={() => setDiscarding(null)} />
      <RawMaterialLotsModal material={lotsOf} onClose={() => setLotsOf(null)} />
    </StepLayout>
  );
}

/** Phiếu xuất đã xong: mỗi dòng là một lô thật đã lấy. */
export function IssuedTable({ lines }: { lines: MaterialIssueLine[] }) {
  const unitLabel = useUnitLabel();
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[560px] border-collapse text-body">
        <thead>
          <tr className="bg-thead text-left text-micro uppercase tracking-wide text-ink-muted">
            <th className="px-4 py-2.5 font-semibold">Nguyên liệu</th>
            <th className="px-3 py-2.5 font-semibold">Lô</th>
            <th className="px-3 py-2.5 text-right font-semibold">Cần</th>
            <th className="px-4 py-2.5 text-right font-semibold">Đã xuất</th>
          </tr>
        </thead>
        <tbody>
          {lines.map((l) => (
            <tr key={l.id} className="border-t border-line">
              <td className="px-4 py-2.5"><div className="font-medium">{l.rawMaterialName}</div><div className="num text-small text-ink-faint">{l.rawMaterialCode}</div></td>
              <td className="num px-3 py-2.5">{l.lotCode ?? '—'}</td>
              <td className="px-3 py-2.5 text-right tabular-nums text-ink-muted">{num(l.qtyRequired)} {unitLabel(l.unit)}</td>
              <td className="px-4 py-2.5 text-right font-medium tabular-nums">{num(l.qtyIssued)} {unitLabel(l.unit)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
