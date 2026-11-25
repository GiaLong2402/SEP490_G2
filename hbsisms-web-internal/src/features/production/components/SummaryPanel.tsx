import type { ReactNode } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { receiptsApi } from '@/features/receipts';
import { date, money, num } from '@/shared/lib/format';
import { InfoTip, LinkButton } from '@/shared/ui';
import { useMaterialIssue } from '../hooks';
import type { ProductionOrder } from '../types';
import { ActionBar, HistoryCard, Section, StepLayout, orderHistory } from './OrderShell';
import { actualTotalCost, estimatedUnitCost, leftoverTotal, reportedEvent } from './ReceiptPanel';

const signed = (v: number, fmt: (n: number) => string) => (v === 0 ? '0' : `${v > 0 ? '+' : '−'}${fmt(Math.abs(v))}`);

function Kpi({ label, value, unit, sub, subTone, bar, accent }: { label: ReactNode; value: string; unit?: string; sub: string; subTone?: 'warn' | 'muted'; bar?: number; accent?: boolean }) {
  return (
    <div className="rounded-lg border border-line bg-card p-4 shadow-card">
      <div className="flex items-center gap-1 text-small text-ink-muted">{label}</div>
      <div className={`mt-1 text-display tabular-nums ${accent ? 'text-primary' : 'text-ink'}`}>
        {value}
        {unit && <span className="text-body font-medium text-ink-faint">{unit}</span>}
      </div>
      {bar !== undefined && <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-rowalt"><div className="h-full rounded-full bg-green-fg" style={{ width: `${Math.min(100, bar)}%` }} /></div>}
      <div className={`mt-1 text-small ${subTone === 'warn' ? 'text-amber-fg' : 'text-ink-muted'}`}>{sub}</div>
    </div>
  );
}

function Row({ k, v }: { k: string; v: ReactNode }) {
  return (
    <div className="flex justify-between gap-4">
      <dt className="text-ink-muted">{k}</dt>
      <dd className="text-right font-semibold text-ink">{v}</dd>
    </div>
  );
}

/** Bước 05: lệnh tự Hoàn thành khi nhập kho xong — tổng kết kế hoạch / thực tế và giá vốn. */
export function SummaryPanel({ order }: { order: ProductionOrder }) {
  const issue = useMaterialIssue(order.materialIssueId);
  const receipt = useQuery({ queryKey: ['receipt', order.finishedReceiptId], queryFn: ({ signal }) => receiptsApi.get(order.finishedReceiptId!, signal), enabled: !!order.finishedReceiptId });
  const received = order.receivedQty ?? order.actualQty ?? 0;
  const est = estimatedUnitCost(order);
  const total = actualTotalCost(order);
  const unit = order.unitCost ?? (total !== null && received > 0 ? Math.round(total / received) : null);
  /** API trả sẵn; nếu chưa (API cũ / chưa restart) tự tính theo markup mặc định 30%. */
  const markupPercent = order.markupPercent ?? 30;
  const suggestedSellPrice =
    order.suggestedSellPrice
    ?? (unit != null ? Math.round((unit * (1 + markupPercent / 100)) / 1000) * 1000 : null);
  const halfKgPrice =
    suggestedSellPrice != null ? Math.round(suggestedSellPrice / 2 / 1000) * 1000 : null;
  const unitDiff = unit !== null && est !== null ? unit - est : null;
  const leftover = leftoverTotal(order);
  const pct = order.plannedQty > 0 ? Math.round((received / order.plannedQty) * 100) : 0;
  const tone = (v: number, upIsBad = true) => (v === 0 ? 'text-ink-muted' : (v > 0) === upIsBad ? 'text-amber-fg' : 'text-green-fg');
  const putAway = order.finishedPutAway ?? null;
  /** Ưu tiên API; dự phòng: dòng hàng của phiếu thành phẩm có dạng "SKU · mã lô". */
  const lotCode = putAway?.lotCode ?? (receipt.data?.items[0]?.name.split(' · ').slice(1).join(' · ') || null);
  const locationText = putAway?.locationCode
    ? [putAway.locationCode, putAway.locationZone, putAway.warehouseName].filter(Boolean).join(' · ')
    : '—';
  const leftoverNames = (order.leftovers ?? []).map((l) => l.rawMaterialName.toLowerCase()).join(', ');

  const rows = [
    { k: 'Số khay', plan: num(order.plannedQty), real: num(received), diff: signed(received - order.plannedQty, num), cls: tone(received - order.plannedQty, false) },
    {
      k: 'Tiền nguyên liệu',
      plan: money(order.estimatedMaterialCost),
      real: money(order.actualMaterialCost),
      diff: signed((order.actualMaterialCost ?? 0) - (order.estimatedMaterialCost ?? 0), money) + (leftover ? ` (${leftoverNames || 'nguyên liệu'} dư)` : ''),
      cls: tone((order.actualMaterialCost ?? 0) - (order.estimatedMaterialCost ?? 0)),
    },
    { k: 'Chi phí sản xuất', plan: money(order.estimatedProductionCost), real: money(order.actualProductionCost), diff: signed((order.actualProductionCost ?? 0) - (order.estimatedProductionCost ?? 0), money), cls: tone((order.actualProductionCost ?? 0) - (order.estimatedProductionCost ?? 0)) },
    { k: 'Giá vốn / khay', plan: money(est), real: money(unit), diff: unitDiff === null ? '—' : signed(unitDiff, money), cls: tone(unitDiff ?? 0) },
  ];
  const history = orderHistory(order, issue.data, [
    ...(order.completedAt ? [{ what: 'Tự chuyển Hoàn thành', who: 'Hệ thống', at: order.completedAt }, { what: `Nhập kho ${order.finishedReceiptCode ?? ''} · ${num(received)} khay`, at: order.completedAt }] : []),
    reportedEvent(order),
  ]);

  return (
    <StepLayout aside={<HistoryCard events={history} />}>
      <div className="grid gap-3 sm:grid-cols-3">
        <Kpi label="Sản lượng đạt" value={num(received)} unit={` / ${num(order.plannedQty)} khay`} bar={pct} sub={`${pct}% kế hoạch · hỏng ${num(order.defectQty ?? 0)} khay${order.defectReason ? ` (${order.defectReason.toLowerCase()})` : ''}`} />
        <Kpi
          accent
          label={<>Giá vốn<InfoTip label="Ghi chú giá vốn">Giá vốn chia cho số khay thật nhập kho, nên khay hỏng làm giá vốn tăng; phần nguyên liệu dư trả về kho được trừ ra. Nếu lý do hỏng lặp lại nhiều lệnh, xem lại khâu đó.</InfoTip></>}
          value={money(unit)}
          unit=" / khay"
          sub={unitDiff === null || !est ? '—' : `${unitDiff >= 0 ? 'Cao' : 'Thấp'} hơn dự tính ${money(Math.abs(unitDiff))} (${unitDiff >= 0 ? '+' : '−'}${Math.abs((unitDiff / est) * 100).toFixed(1).replace('.', ',')}%)`}
          subTone={unitDiff !== null && unitDiff > 0 ? 'warn' : 'muted'}
        />
        <Kpi label="Tổng chi phí" value={money(total)} sub={`Nguyên liệu ${num(order.actualMaterialCost)}${leftover ? ` (đã trừ ${num(leftover)} ${leftoverNames} dư)` : ''} + sản xuất ${num(order.actualProductionCost)}`} />
      </div>

      <Section flush title="Kế hoạch so với thực tế">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[560px] border-collapse text-body">
            <thead>
              <tr className="bg-thead text-left text-micro uppercase tracking-wide text-ink-muted">
                <th className="px-4 py-2.5 font-semibold">Mục</th>
                <th className="px-3 py-2.5 text-right font-semibold">Kế hoạch</th>
                <th className="px-3 py-2.5 text-right font-semibold">Thực tế</th>
                <th className="px-4 py-2.5 text-right font-semibold">Chênh lệch</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.k} className="border-t border-line">
                  <td className="px-4 py-3">{r.k}</td>
                  <td className="px-3 py-3 text-right tabular-nums">{r.plan}</td>
                  <td className="px-3 py-3 text-right font-semibold tabular-nums">{r.real}</td>
                  <td className={`px-4 py-3 text-right tabular-nums ${r.cls}`}>{r.diff}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <div className="grid gap-4 md:grid-cols-2">
        <section className="rounded-lg border border-line bg-card p-4 shadow-card">
          <h2 className="mb-3 text-h2 text-ink">Hàng đã nhập kho</h2>
          <dl className="space-y-2.5">
            <Row k="Mã lô" v={<span className="font-mono">{lotCode ?? '—'}</span>} />
            <Row k="Số khay" v={`Báo ${num(order.actualQty)} · Nhập kho ${num(received)}`} />
            <Row k="Nguyên liệu trả lại" v={order.leftovers?.length ? <span className="font-mono">{order.leftovers.map((l) => `${l.lotCode} · ${num(l.qty)} ${l.unit}`).join(', ')}</span> : 'Không'} />
            <Row k="Vị trí" v={locationText} />
            <Row k="Hạn sử dụng" v={putAway?.expDate ? date(putAway.expDate) : '—'} />
            <Row k="Phiếu nhập" v={order.finishedReceiptCode ? <Link to="/receipts" className="font-mono font-normal text-primary hover:underline">{order.finishedReceiptCode}</Link> : '—'} />
          </dl>
        </section>
        <section className="flex flex-col rounded-lg border border-line bg-card p-4 shadow-card">
          <div className="flex items-start justify-between gap-2">
            <h2 className="text-h2 text-ink">Giá bán (đã cập nhật)</h2>
            <InfoTip label="Cách tính giá bán">
              Giá vốn / khay × (1 + {markupPercent}% lời), làm tròn 1.000 ₫ → khay 1 kg. Khay 0,5 kg = một nửa (cũng
              tròn 1.000 ₫). Hệ thống ghi vào niêm yết quy cách — không nhập tay.
            </InfoTip>
          </div>
          <p className="mt-1 text-small text-ink-muted">
            {money(unit)} vốn → +{markupPercent}% → tròn 1.000 ₫
          </p>
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="rounded-md border border-line bg-rowalt/60 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 1 kg</div>
              <div className="mt-1 text-h2 tabular-nums text-primary">{money(suggestedSellPrice)}</div>
            </div>
            <div className="rounded-md border border-line bg-rowalt/60 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 0,5 kg</div>
              <div className="mt-1 text-h2 tabular-nums text-ink">{money(halfKgPrice)}</div>
              <div className="mt-0.5 text-small text-ink-faint">= ½ giá 1 kg</div>
            </div>
          </div>
          {order.productId != null && (
            <Link
              to={`/products/${order.productId}`}
              className="mt-3 text-small font-medium text-primary hover:underline"
            >
              Xem trên sản phẩm →
            </Link>
          )}
        </section>
      </div>

      <ActionBar actions={<LinkButton to="/production-orders" variant="primary" className="!border-green-fg !bg-green-fg" icon={<CheckCircle2 size={15} />}>Về danh sách lệnh</LinkButton>}>
        <span className="text-body">Không cần bấm đóng lệnh: lệnh <b className="text-ink">tự chuyển "Hoàn thành"</b> ngay khi nhập kho {order.finishedReceiptCode ?? ''} xong</span>
        <span className="text-body">Lệnh đã hoàn thành chỉ xem, không sửa được nữa</span>
      </ActionBar>
    </StepLayout>
  );
}
