import type { ReactNode } from 'react';
import { PackageCheck } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { FinishedReceiptFields, receiptsApi, useFinishedReceiptForm } from '@/features/receipts';
import { money, num } from '@/shared/lib/format';
import { Button, Chip, ErrorState, InfoTip, PageLoading } from '@/shared/ui';
import { useMaterialIssue } from '../hooks';
import type { ProductionOrder } from '../types';
import { ActionBar, HistoryCard, OverviewCard, Section, StepLayout, orderHistory, type HistoryEvent } from './OrderShell';

/** Mốc "Báo làm xong" trong lịch sử. */
export function reportedEvent(o: ProductionOrder): HistoryEvent {
  const parts = [`Báo làm xong ${num(o.actualQty)}/${num(o.plannedQty)}`];
  if (o.defectQty) parts.push(`hỏng ${num(o.defectQty)}`);
  for (const l of o.leftovers ?? []) parts.push(`trả ${num(l.qty)} ${l.unit} ${l.rawMaterialName.toLowerCase()} (${l.lotCode})`);
  if (o.finishedReceiptCode) parts.push(`tạo ${o.finishedReceiptCode}`);
  return { what: parts.join(' · '), at: o.finishedDate ?? null };
}

/** Tổng chi phí thật của lệnh: NVL đã xuất (backend trừ phần dư trả lại) + chi phí sản xuất. */
export const actualTotalCost = (o: ProductionOrder) =>
  o.actualMaterialCost === null && o.actualProductionCost === null ? null : (o.actualMaterialCost ?? 0) + (o.actualProductionCost ?? 0);
export const leftoverTotal = (o: ProductionOrder) => (o.leftovers ?? []).reduce((s, l) => s + l.amount, 0);
export const estimatedUnitCost = (o: ProductionOrder) =>
  o.plannedQty > 0 ? Math.round(((o.estimatedMaterialCost ?? 0) + (o.estimatedProductionCost ?? 0)) / o.plannedQty) : null;

function FormulaBox({ label, value, sub, highlight }: { label: string; value: ReactNode; sub?: ReactNode; highlight?: boolean }) {
  return (
    <div className={`min-w-[150px] flex-1 rounded-md p-3 ${highlight ? 'bg-primary/10' : 'bg-rowalt'}`}>
      <div className={`text-small ${highlight ? 'font-semibold text-primary' : 'text-ink-muted'}`}>{label}</div>
      <div className={`tabular-nums ${highlight ? 'text-display text-primary' : 'text-h1 text-ink'}`}>{value}</div>
      {sub && <div className="text-small text-ink-muted">{sub}</div>}
    </div>
  );
}
const Op = ({ children }: { children: string }) => <span className="text-h1 text-ink-faint" aria-hidden>{children}</span>;

/** Bước 04 (Long, dòng 52): phiếu nhập thành phẩm sinh từ lệnh đang chờ — chọn vị trí, lô, hạn rồi Nhập kho. */
export function ReceiptPanel({ order }: { order: ProductionOrder }) {
  const receipt = useQuery({
    queryKey: ['receipt', order.finishedReceiptId],
    queryFn: ({ signal }) => receiptsApi.get(order.finishedReceiptId!, signal),
    enabled: !!order.finishedReceiptId,
  });
  const productLabel = `${order.productName}${order.finishedVariantSku ? ` · ${order.finishedVariantSku}` : ''}`;
  const total = actualTotalCost(order);
  const form = useFinishedReceiptForm(receipt.data, { productLabel, reportedQty: order.actualQty ?? 0, totalCost: total, mfgDate: order.finishedDate ?? null });
  const issue = useMaterialIssue(order.materialIssueId);
  const leftover = leftoverTotal(order);
  const aside = (
    <>
      <OverviewCard
        items={[
          ['Trạng thái', 'Chờ nhập kho'],
          ['Thành phẩm', order.productName],
          ['Kế hoạch / đạt', `${num(order.plannedQty)} / ${num(order.actualQty)} khay`],
          ['Hỏng', order.defectQty ? `${num(order.defectQty)} khay${order.defectReason ? ` · ${order.defectReason.toLowerCase()}` : ''}` : '0'],
          ['Phiếu nhập', <><span className="font-mono">{order.finishedReceiptCode ?? '—'}</span> · {receipt.data?.status === 'completed' ? 'đã nhập' : 'chờ nhập'}</>],
          ...(order.leftovers?.length ? [['Nguyên liệu dư trả lại', `${order.leftovers.map((l) => `${num(l.qty)} ${l.unit}`).join(', ')} · −${money(leftover)}`] as [string, ReactNode]] : []),
          ['Tổng chi phí', money(total)],
        ]}
      />
      <HistoryCard events={orderHistory(order, issue.data, [reportedEvent(order)])} />
    </>
  );

  if (!order.finishedReceiptId) return <StepLayout aside={aside}><Section title="Phiếu nhập thành phẩm"><p className="text-ink-muted">Lệnh chưa có phiếu nhập thành phẩm.</p></Section></StepLayout>;
  if (receipt.isLoading) return <PageLoading />;
  if (receipt.error || !receipt.data) return <ErrorState error={receipt.error} onRetry={receipt.refetch} />;

  const pending = receipt.data.status === 'pending';
  const est = estimatedUnitCost(order);
  const unit = form.unitCost;
  const diffPct = unit !== null && est ? ((unit - est) / est) * 100 : null;

  return (
    <StepLayout aside={aside}>
      <Section
        title={
          <span className="inline-flex flex-wrap items-center gap-2">
            Phiếu nhập thành phẩm <span className="font-mono text-primary">{receipt.data.code}</span>
            <Chip tone={pending ? 'amber' : 'green'}>{pending ? 'Chờ nhập' : 'Đã nhập'}</Chip>
          </span>
        }
        subtitle={<>Tự tạo khi báo làm xong · nguồn: lệnh {order.code} · hiện ở màn Nhập kho với nút <b>Nhập kho</b></>}
      >
        <article className="rounded-md border border-line">
          <div className="flex items-center gap-2 rounded-t-md border-b border-line bg-rowalt/60 px-3 py-1.5">
            <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-micro font-semibold text-card">1</span>
            <span className="text-small font-medium text-ink-muted">Mặt hàng 1</span>
          </div>
          <div className="p-3">
            <FinishedReceiptFields form={form} productLabel={productLabel} />
          </div>
        </article>
      </Section>

      <Section
        title="Giá vốn của lô này"
        subtitle={
          <span className="inline-flex items-center gap-1">
            Tính tự động khi bấm Nhập kho
            <InfoTip label="Ghi chú giá vốn">Lưu vào đơn giá của dòng phiếu nhập. Mỗi phiếu sinh từ lệnh là một lô, nên mỗi lô có giá vốn riêng.</InfoTip>
          </span>
        }
      >
        <div className="flex flex-wrap items-center gap-3">
          <FormulaBox label="Tiền nguyên liệu thật" value={money(order.actualMaterialCost)} sub={leftover > 0 ? <span className="text-green-fg">{money((order.actualMaterialCost ?? 0) + leftover)} xuất − {money(leftover)} dư trả lại</span> : undefined} />
          <Op>+</Op>
          <FormulaBox label="Chi phí sản xuất" value={money(order.actualProductionCost)} />
          <Op>÷</Op>
          <FormulaBox label="Số khay nhập kho" value={form.qtyValid ? num(form.n) : '—'} />
          <Op>=</Op>
          <FormulaBox
            highlight
            label="Giá vốn / khay"
            value={money(unit)}
            sub={est ? `Dự tính ${money(est)}${diffPct !== null ? ` (${diffPct >= 0 ? '+' : ''}${diffPct.toFixed(1).replace('.', ',')}%${order.defectQty ? `: hỏng ${num(order.defectQty)} khay` : ''}${leftover ? ', đã trừ nguyên liệu dư' : ''})` : ''}` : undefined}
          />
        </div>
      </Section>

      {pending && (
        <ActionBar
          actions={<Button variant="primary" icon={<PackageCheck size={15} />} loading={form.mutation.isPending} onClick={form.submit}>Nhập kho</Button>}
        >
          <span className="text-body">
            Nhập xong: cộng {form.qtyValid ? num(form.n) : '…'} khay vào <b className="text-ink">{form.locationText ?? 'vị trí đã chọn'}</b>
            {unit !== null && <>, giá vốn {money(unit)}/khay</>} · lệnh tự chuyển "Hoàn thành"
          </span>
        </ActionBar>
      )}
    </StepLayout>
  );
}
