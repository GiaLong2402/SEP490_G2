import type { ReactNode } from 'react';
import { CheckCircle2 } from 'lucide-react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { isOwnerOrAdmin, useAuth } from '@/features/auth';
import { productsApi } from '@/features/products/api';
import { receiptsApi, useUnitLabel } from '@/features/receipts';
import { useAction } from '@/shared/api/useAction';
import { date, money, num } from '@/shared/lib/format';
import { Button, InfoTip, LinkButton } from '@/shared/ui';
import { useMaterialIssue } from '../hooks';
import type { ProductionOrder } from '../types';
import { ActionBar, HistoryCard, Section, StepLayout, orderHistory } from './OrderShell';
import { actualTotalCost, estimatedUnitCost, leftoverTotal, reportedEvent } from './ReceiptPanel';

const signed = (v: number, fmt: (n: number) => string) => (v === 0 ? '0' : `${v > 0 ? '+' : '−'}${fmt(Math.abs(v))}`);

function Kpi({ label, value, unit, sub, subTone, bar, accent }: { label: ReactNode; value: string; unit?: string; sub: ReactNode; subTone?: 'warn' | 'muted'; bar?: number; accent?: boolean }) {
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

/** Dòng nhãn – giá trị; `sub` là dòng phụ nhỏ dưới giá trị (không nhồi chung một dòng). */
function Row({ k, v, sub }: { k: string; v: ReactNode; sub?: ReactNode }) {
  return (
    <div className="flex items-start justify-between gap-4">
      <dt className="shrink-0 text-ink-muted">{k}</dt>
      <dd className="min-w-0 text-right">
        <div className="font-semibold text-ink">{v}</div>
        {sub && <div className="text-small text-ink-muted">{sub}</div>}
      </dd>
    </div>
  );
}

/** Một dòng cộng tiền trong ô Tổng chi phí. */
function CostLine({ k, v, note }: { k: string; v: string; note?: string }) {
  return (
    <div>
      <div className="flex justify-between gap-3">
        <span>{k}</span>
        <span className="tabular-nums text-ink">{v}</span>
      </div>
      {note && <div className="text-green-fg">{note}</div>}
    </div>
  );
}

/** Bước 05: lệnh tự Hoàn thành khi nhập kho xong — tổng kết kế hoạch / thực tế và giá vốn. */
export function SummaryPanel({ order }: { order: ProductionOrder }) {
  const { user } = useAuth();
  const canApplyPrice = isOwnerOrAdmin(user?.roleCode);
  const queryClient = useQueryClient();
  const issue = useMaterialIssue(order.materialIssueId);
  const receipt = useQuery({ queryKey: ['receipt', order.finishedReceiptId], queryFn: ({ signal }) => receiptsApi.get(order.finishedReceiptId!, signal), enabled: !!order.finishedReceiptId });
  /** Task 59 / đề bài thầy: gợi ý bán = WAC sau nhập TP (không lấy giá thành mẻ). */
  const wacSuggest = useQuery({
    queryKey: ['sell-price-suggestion', order.finishedVariantId, 'WAC'],
    queryFn: () => productsApi.sellPriceSuggestion(order.finishedVariantId!, 'WAC'),
    enabled: order.finishedVariantId != null,
    retry: false,
  });
  const applyWacPrice = useAction(
    () => productsApi.applySellPrice(order.finishedVariantId!, { costSource: 'WAC' }),
    {
      success: (r) => {
        const half = Math.round(r.suggestedListPrice / 2 / 1000) * 1000;
        return `Đã áp dụng giá 1 kg ${money(r.suggestedListPrice)} · 0,5 kg ${money(half)}`;
      },
      onDone: () => {
        void queryClient.invalidateQueries({ queryKey: ['sell-price-history'] });
        void queryClient.invalidateQueries({ queryKey: ['sell-price-suggestion'] });
        void queryClient.invalidateQueries({ queryKey: ['product', order.productId] });
      },
    },
  );
  const received = order.receivedQty ?? order.actualQty ?? 0;
  const est = estimatedUnitCost(order);
  const total = actualTotalCost(order);
  const unit = order.unitCost ?? (total !== null && received > 0 ? Math.round(total / received) : null);
  const markupPercent = wacSuggest.data?.markupPercent ?? order.markupPercent ?? 30;
  const wacUnit = wacSuggest.data?.unitCost ?? null;
  const suggestedSellPrice = wacSuggest.data?.suggestedListPrice
    ?? order.suggestedSellPrice
    ?? null;
  const halfKgPrice =
    suggestedSellPrice != null ? Math.round(suggestedSellPrice / 2 / 1000) * 1000 : null;
  const unitDiff = unit !== null && est !== null ? unit - est : null;
  const leftover = leftoverTotal(order);
  const pct = order.plannedQty > 0 ? Math.round((received / order.plannedQty) * 100) : 0;
  const tone = (v: number, upIsBad = true) => (v === 0 ? 'text-ink-muted' : (v > 0) === upIsBad ? 'text-amber-fg' : 'text-green-fg');
  const putAway = order.finishedPutAway ?? null;
  /** Ưu tiên API; dự phòng: dòng hàng của phiếu thành phẩm có dạng "SKU · mã lô". */
  const lotCode = putAway?.lotCode ?? (receipt.data?.items[0]?.name.split(' · ').slice(1).join(' · ') || null);
  const unitLabel = useUnitLabel();
  const locationMain = putAway?.locationCode ? [putAway.locationCode, putAway.locationZone].filter(Boolean).join(' · ') : '—';
  const leftoverNames = (order.leftovers ?? []).map((l) => l.rawMaterialName.toLowerCase()).join(', ');
  const materialDiff = (order.actualMaterialCost ?? 0) - (order.estimatedMaterialCost ?? 0);
  const productionDiff = (order.actualProductionCost ?? 0) - (order.estimatedProductionCost ?? 0);

  /** `note` = lý do chênh lệch, hiện dòng nhỏ dưới con số (không nối chung một dòng). */
  const rows: { k: string; plan: string; real: string; diff: string; cls: string; note?: string }[] = [
    { k: 'Số khay', plan: num(order.plannedQty), real: num(received), diff: signed(received - order.plannedQty, num), cls: tone(received - order.plannedQty, false), note: order.defectQty ? `Hỏng ${num(order.defectQty)} khay` : undefined },
    {
      k: 'Tiền nguyên liệu',
      plan: money(order.estimatedMaterialCost),
      real: money(order.actualMaterialCost),
      diff: signed(materialDiff, money),
      cls: tone(materialDiff),
      note: leftover ? `Trả lại kho: ${leftoverNames || 'nguyên liệu dư'}` : undefined,
    },
    { k: 'Chi phí sản xuất', plan: money(order.estimatedProductionCost), real: money(order.actualProductionCost), diff: signed(productionDiff, money), cls: tone(productionDiff) },
    {
      k: 'Giá vốn / khay',
      plan: money(est),
      real: money(unit),
      diff: unitDiff === null ? '—' : signed(unitDiff, money),
      cls: tone(unitDiff ?? 0),
      note: unitDiff && unitDiff < 0 && leftover ? 'Nhờ nguyên liệu dư trả lại' : unitDiff && unitDiff > 0 && order.defectQty ? 'Do có khay hỏng' : undefined,
    },
  ];
  const history = orderHistory(order, issue.data, [
    ...(order.completedAt
      ? [{
          what: 'Nhập kho · lệnh hoàn thành',
          details: [
            `Phiếu ${order.finishedReceiptCode ?? ''} · ${num(received)} khay${putAway?.locationCode ? ` vào ${putAway.locationCode}` : ''}`,
            'Lệnh tự chuyển "Hoàn thành"',
          ],
          who: 'Hệ thống',
          at: order.completedAt,
        }]
      : []),
    reportedEvent(order, unitLabel),
  ]);

  return (
    <StepLayout aside={<HistoryCard events={history} />}>
      <div className="grid gap-3 sm:grid-cols-3">
        <Kpi
          label="Sản lượng đạt"
          value={num(received)}
          unit={` / ${num(order.plannedQty)} khay`}
          bar={pct}
          sub={<><div>{pct}% kế hoạch</div><div>Hỏng {num(order.defectQty ?? 0)} khay{order.defectReason ? ` · ${order.defectReason.toLowerCase()}` : ''}</div></>}
        />
        <Kpi
          accent
          label={<>Giá vốn<InfoTip label="Ghi chú giá vốn">Giá vốn chia cho số khay thật nhập kho, nên khay hỏng làm giá vốn tăng; phần nguyên liệu dư trả về kho được trừ ra. Nếu lý do hỏng lặp lại nhiều lệnh, xem lại khâu đó.</InfoTip></>}
          value={money(unit)}
          unit=" / khay"
          sub={unitDiff === null || !est ? '—' : `${unitDiff >= 0 ? 'Cao' : 'Thấp'} hơn dự tính ${money(Math.abs(unitDiff))} (${unitDiff >= 0 ? '+' : '−'}${Math.abs((unitDiff / est) * 100).toFixed(1).replace('.', ',')}%)`}
          subTone={unitDiff !== null && unitDiff > 0 ? 'warn' : 'muted'}
        />
        <Kpi
          label="Tổng chi phí"
          value={money(total)}
          sub={
            <div className="mt-1 space-y-1">
              <CostLine k="Nguyên liệu" v={money(order.actualMaterialCost)} note={leftover ? `đã trừ ${money(leftover)} dư trả lại` : undefined} />
              <CostLine k="Sản xuất" v={money(order.actualProductionCost)} />
            </div>
          }
        />
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
                  <td className="px-4 py-3 text-right">
                    <div className={`tabular-nums ${r.cls}`}>{r.diff}</div>
                    {r.note && <div className="text-small text-ink-muted">{r.note}</div>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Section>

      <div className="grid gap-4 md:grid-cols-2">
        <section className="rounded-lg border border-line bg-card p-4 shadow-card">
          <h2 className="mb-3 text-h2 text-ink">Hàng đã nhập kho</h2>
          <dl className="space-y-3">
            <Row k="Mã lô" v={<span className="font-mono">{lotCode ?? '—'}</span>} />
            <Row
              k="Số khay"
              v={`${num(received)} khay`}
              sub={order.actualQty != null && order.actualQty !== received ? `Báo đạt ${num(order.actualQty)}, đếm lại khi nhập kho` : 'Đúng số báo đạt'}
            />
            <Row k="Vị trí" v={locationMain} sub={putAway?.warehouseName ?? undefined} />
            <Row k="Hạn sử dụng" v={putAway?.expDate ? date(putAway.expDate) : '—'} />
            <Row k="Phiếu nhập" v={order.finishedReceiptCode ? <Link to="/receipts" className="font-mono font-normal text-primary hover:underline">{order.finishedReceiptCode}</Link> : '—'} />
          </dl>
          <div className="mt-4 border-t border-line pt-3">
            <div className="mb-2 text-small font-medium text-ink-muted">Nguyên liệu dư trả lại kho</div>
            {order.leftovers?.length ? (
              <ul className="space-y-2">
                {order.leftovers.map((l) => (
                  <li key={`${l.rawMaterialId}-${l.lotCode}`} className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <div className="font-medium text-ink">{l.rawMaterialName}</div>
                      <div className="font-mono text-small text-ink-faint">Lô {l.lotCode}</div>
                    </div>
                    <div className="shrink-0 text-right">
                      <div className="font-semibold tabular-nums text-ink">{num(l.qty)} {unitLabel(l.unit)}</div>
                      <div className="text-small tabular-nums text-green-fg">−{money(l.amount)}</div>
                    </div>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="text-small text-ink-faint">Không có</p>
            )}
          </div>
        </section>
        <section className="flex flex-col rounded-lg border border-line bg-card p-4 shadow-card">
          <div className="flex items-start justify-between gap-2">
            <h2 className="text-h2 text-ink">Gợi ý giá bán theo WAC</h2>
            <InfoTip label="Cách tính giá bán">
              Không lấy giá vốn mẻ này. Sau nhập TP, sổ kỳ gộp tồn cũ + hàng mới → ĐG bình quân WAC. Giá gợi ý =
              WAC × (1 + {markupPercent}% lời), làm tròn 1.000 ₫. Chỉ khi Áp dụng mới đổi niêm yết.
            </InfoTip>
          </div>
          <p className="mt-1 text-small font-medium text-ink">
            Nguồn giá: <span className="text-primary">ĐG bình quân kỳ (WAC)</span> — không dùng giá vốn mẻ.
          </p>
          <div className="mt-3 grid grid-cols-2 gap-2 rounded-md border border-line bg-rowalt/50 px-3 py-2.5 text-small">
            <div>
              <div className="text-micro uppercase tracking-wide text-ink-muted">Giá vốn mẻ này</div>
              <div className="mt-0.5 tabular-nums text-ink">{money(unit)}</div>
              <div className="text-ink-faint">chỉ tham chiếu</div>
            </div>
            <div>
              <div className="text-micro uppercase tracking-wide text-ink-muted">WAC dùng gợi ý</div>
              <div className="mt-0.5 font-semibold tabular-nums text-primary">
                {wacSuggest.isLoading ? '…' : wacUnit != null ? money(wacUnit) : '—'}
              </div>
              <div className="text-ink-faint">+{markupPercent}% → tròn 1.000 ₫</div>
            </div>
          </div>
          {wacSuggest.error && (
            <p className="mt-2 text-small text-amber-fg">Chưa lấy được WAC — mở màn sản phẩm để thử lại</p>
          )}
          {wacSuggest.data?.currentListPrice != null && (
            <p className="mt-2 text-small text-ink-faint">
              Niêm yết hiện tại: {money(wacSuggest.data.currentListPrice)}
            </p>
          )}
          <div className="mt-4 grid grid-cols-2 gap-3">
            <div className="rounded-md border border-line bg-rowalt/60 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 1 kg (từ WAC)</div>
              <div className="mt-1 text-h2 tabular-nums text-primary">{money(suggestedSellPrice)}</div>
            </div>
            <div className="rounded-md border border-line bg-rowalt/60 px-3 py-3">
              <div className="text-micro font-semibold uppercase tracking-wide text-ink-muted">Khay 0,5 kg</div>
              <div className="mt-1 text-h2 tabular-nums text-ink">{money(halfKgPrice)}</div>
              <div className="mt-0.5 text-small text-ink-faint">= ½ giá gợi ý</div>
            </div>
          </div>
          <div className="mt-3 flex flex-wrap items-center gap-3">
            {canApplyPrice && wacSuggest.data && order.finishedVariantId != null ? (
              <Button
                variant="primary"
                loading={applyWacPrice.isPending}
                onClick={() => applyWacPrice.mutate()}
              >
                Áp dụng giá (1 kg + 0,5 kg)
              </Button>
            ) : null}
            {order.productId != null && (
              <Link to={`/products/${order.productId}`} className="text-small font-medium text-primary hover:underline">
                Xem trên sản phẩm →
              </Link>
            )}
          </div>
        </section>
      </div>

      <ActionBar actions={<LinkButton to="/production-orders" variant="primary" className="!border-green-fg !bg-green-fg" icon={<CheckCircle2 size={15} />}>Về danh sách lệnh</LinkButton>}>
        <span className="text-body">Không cần bấm đóng lệnh: lệnh <b className="text-ink">tự chuyển "Hoàn thành"</b> ngay khi nhập kho {order.finishedReceiptCode ?? ''} xong</span>
        <span className="text-body">Lệnh đã hoàn thành chỉ xem, không sửa được nữa</span>
      </ActionBar>
    </StepLayout>
  );
}
