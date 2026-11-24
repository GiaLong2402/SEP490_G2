import { useEffect, useState } from 'react';
import { Check, CircleAlert, PackagePlus } from 'lucide-react';
import { useQueries } from '@tanstack/react-query';
import { Link, useNavigate } from 'react-router-dom';
import { useProductBom } from '@/features/products/hooks';
import { isLotExpired, receiptsApi, useUnitLabel } from '@/features/receipts';
import { ApiError } from '@/shared/api/client';
import { useAction } from '@/shared/api/useAction';
import { date, dateTimeFull, formatThousands, money, num, parseThousands, todayISO } from '@/shared/lib/format';
import { Button, Chip, ConfirmDialog, DateInput, Field, InfoTip, Textarea } from '@/shared/ui';
import { productionApi } from '../api';
import { useBomProducts } from '../hooks';
import type { ProductionOrder } from '../types';
import { ProductPicker } from './ProductPicker';
import { ActionBar, HistoryCard, OverviewCard, Section, StepLayout, SuffixInput, orderHistory } from './OrderShell';

interface PlanRow { key: string; rawId: number; name: string; code: string; unit: string; per: number; waste: number; need: number; stock: number; expired: number; cost: number | null; sufficient: boolean }

const round3 = (n: number) => Math.round(n * 1000) / 1000;

/**
 * Bước 01 (Duy, dòng 48): thông tin lệnh + nguyên liệu cần dùng theo định mức (đã cộng hao hụt), đối chiếu tồn.
 * Lệnh mới / đang sửa: xem trước ngay theo định mức và các lô còn dùng được (không tính lô hết hạn).
 * Lệnh đã lưu và chưa sửa: hiện đúng số máy chủ đã chốt lúc lưu.
 */
export function PlanEditor({ order }: { order: ProductionOrder | null }) {
  const navigate = useNavigate();
  const unitLabel = useUnitLabel();
  const bomProducts = useBomProducts();
  const [productId, setProductId] = useState(order ? String(order.productId) : '');
  const [qty, setQty] = useState(order ? String(order.plannedQty) : '');
  const [due, setDue] = useState(order?.plannedDueDate ?? todayISO(3));
  const [cost, setCost] = useState(order?.estimatedProductionCost ? String(order.estimatedProductionCost) : '');
  const [note, setNote] = useState(order?.note ?? '');
  const [touched, setTouched] = useState(false);
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (!order) return;
    setProductId(String(order.productId));
    setQty(String(order.plannedQty));
    setDue(order.plannedDueDate ?? '');
    setCost(order.estimatedProductionCost ? String(order.estimatedProductionCost) : '');
    setNote(order.note ?? '');
  }, [order]);

  const n = Number(qty);
  const production = Number(parseThousands(cost)) || 0;
  const dirty = !order || n !== order.plannedQty || (due || null) !== order.plannedDueDate || (production || null) !== order.estimatedProductionCost || (note.trim() || null) !== (order.note ?? null);
  const product = bomProducts.items.find((p) => String(p.id) === productId);

  // Luôn lấy định mức + lô để biết phần đã hết hạn; khi chưa lưu thì tính luôn số cần / tồn / tiền phía giao diện.
  const bom = useProductBom(Number(productId) || 0);
  const rawLines = (bom.data?.lines ?? []).filter((l) => l.rawMaterialId !== null);
  const lotQueries = useQueries({
    queries: rawLines.map((l) => ({ queryKey: ['raw-material-lots', l.rawMaterialId], queryFn: ({ signal }: { signal: AbortSignal }) => receiptsApi.rawMaterialLots(l.rawMaterialId!, false, signal) })),
  });
  const lotsReady = lotQueries.every((q) => q.data);
  const expiredOf = new Map<number, number>();
  const previewRows: PlanRow[] = rawLines.map((l, i) => {
    const all = (lotQueries[i]?.data ?? []).filter((x) => x.qtyRemaining > 0);
    const lots = all.filter((x) => !isLotExpired(x));
    const expired = round3(all.filter(isLotExpired).reduce((s, x) => s + x.qtyRemaining, 0));
    expiredOf.set(l.rawMaterialId!, expired);
    const need = n > 0 ? round3(l.qty * n * (1 + (l.wastePercent ?? 0) / 100)) : 0;
    const stock = round3(lots.reduce((s, x) => s + x.qtyRemaining, 0));
    let left = need;
    let total = 0;
    for (const lot of lots) {
      if (left <= 0) break;
      const take = Math.min(left, lot.qtyRemaining);
      total += take * (lot.unitPrice ?? 0);
      left -= take;
    }
    return { key: String(l.id), rawId: Number(l.rawMaterialId), name: l.rawMaterialName ?? '', code: l.rawMaterialCode ?? '', unit: l.unitCode, per: l.qty, waste: Number(l.wastePercent ?? 0), need, stock, expired, cost: Math.round(total), sufficient: stock >= need };
  });
  // Lệnh nháp luôn đối chiếu tồn HIỆN TẠI (backend cũng tính lại lúc Duyệt). Chỉ dùng số chụp lúc lưu khi không xem được định mức.
  const live = bom.isSuccess && lotsReady && rawLines.length > 0;
  const rows: PlanRow[] = live || dirty
    ? previewRows
    : (order?.materials ?? []).map((m) => ({ key: String(m.id), rawId: Number(m.rawMaterialId), name: m.rawMaterialName, code: m.rawMaterialCode, unit: m.unit, per: m.bomQtyPerUnit, waste: Number(m.wastePercent ?? 0), need: m.requiredQty, stock: m.onHandQty, expired: expiredOf.get(m.rawMaterialId) ?? 0, cost: m.lineEstimatedCost, sufficient: m.sufficient }));
  const enough = rows.filter((r) => r.sufficient).length;
  const allEnough = rows.length > 0 && enough === rows.length;
  const materialTotal = rows.reduce((s, r) => s + (r.cost ?? 0), 0);
  const noBom = !!productId && bom.isSuccess && rawLines.length === 0;
  /** Không xem được định mức (thiếu quyền `products`) → không xem trước được, lưu nháp để máy chủ tính. */
  const bomForbidden = bom.error instanceof ApiError && bom.error.status === 403;
  const estUnit = n > 0 ? Math.round((materialTotal + production) / n) : null;

  const errors = { product: !(Number(productId) > 0) ? 'Chọn thành phẩm' : noBom ? 'Sản phẩm chưa có định mức nguyên liệu (BOM)' : undefined, qty: !(n > 0) ? 'Số lượng phải lớn hơn 0' : undefined };
  const valid = !errors.product && !errors.qty;
  const body = { plannedQty: n, plannedDueDate: due || null, note: note.trim() || null, estimatedProductionCost: production || null };
  const persist = () => (order ? productionApi.update(order.id, body) : productionApi.create({ productId: Number(productId), ...body }));

  const save = useAction(persist, {
    success: (r) => (order ? `Đã lưu lệnh ${r.code}` : `Đã tạo lệnh ${r.code} - đang ở trạng thái Nháp`),
    onDone: (r) => !order && navigate(`/production-orders/${r.id}`, { replace: true }),
  });
  const approve = useAction(
    async () => {
      const saved = dirty ? await persist() : order!;
      return productionApi.approve(saved.id);
    },
    { success: (r) => `Đã duyệt lệnh ${r.code} - tạo phiếu xuất ${r.materialIssueCode ?? ''}`, onDone: (r) => !order && navigate(`/production-orders/${r.id}`, { replace: true }) },
  );
  /** Thiếu nguyên liệu: tự lưu nháp (để không mất dữ liệu) rồi mở phiếu nhập NVL điền sẵn số còn thiếu; lưu phiếu xong quay lại lệnh. */
  const toReceipt = useAction(() => (dirty ? persist() : Promise.resolve(order!)), {
    success: dirty ? (r) => `Đã lưu nháp ${r.code} - mở phiếu nhập nguyên liệu` : undefined,
    onDone: (r) => {
      const lines = rows.filter((x) => !x.sufficient).map((x) => `${x.rawId}:${Math.ceil((x.need - x.stock) * 1000) / 1000}`).join(',');
      const q = new URLSearchParams({ new: 'raw', lines, source: `Lệnh ${r.code}`, returnTo: `/production-orders/${r.id}` });
      navigate(`/receipts?${q.toString()}`);
    },
  });
  const cancel = useAction(() => productionApi.cancel(order!.id), { success: (r) => `Đã hủy lệnh ${r.code}`, onDone: () => setCancelling(false) });
  const run = (fn: () => void) => {
    setTouched(true);
    if (valid) fn();
  };

  const aside = (
    <>
      <OverviewCard
        items={[
          ['Trạng thái', order ? 'Nháp' : 'Nháp (chưa lưu)'],
          ['Thành phẩm', product?.name ?? order?.productName ?? '—'],
          ['Số lượng', n > 0 ? `${num(n)} khay` : '—'],
          ['Cần xong', date(due || null)],
          ['Tiền nguyên liệu (tạm)', money(materialTotal)],
          ['Chi phí sản xuất', money(production)],
        ]}
        footer={['Giá vốn dự tính', <><b className="text-h1 tabular-nums text-ink">{money(estUnit)}</b><span className="text-ink-muted"> / khay</span></>]}
      />
      <HistoryCard events={orderHistory(order)} />
    </>
  );

  return (
    <StepLayout aside={aside}>
      <Section title="Thông tin lệnh" subtitle="Chọn thành phẩm cần làm, số lượng và ngày cần xong">
        <div className="grid gap-3 sm:grid-cols-3">
          <div className="sm:col-span-3">
            <div className="mb-1 text-small font-medium text-ink-muted">Thành phẩm <span className="text-red-fg">*</span></div>
            <ProductPicker
              value={productId}
              onChange={setProductId}
              items={bomProducts.items}
              loading={bomProducts.loading}
              forbidden={bomProducts.forbidden}
              disabled={!!order}
              invalid={touched && !!errors.product}
              selectedSub={productId ? (noBom ? 'chưa có định mức nguyên liệu' : 'đã có định mức nguyên liệu') : undefined}
              fallbackName={order?.productName}
              fallbackCode={order?.productCode}
            />
            {touched && errors.product && <p className="mt-1 text-small text-red-fg">{errors.product}</p>}
          </div>
          <Field label="Số lượng kế hoạch" required error={touched ? errors.qty : undefined}>
            <SuffixInput suffix="khay" type="number" min={1} className="text-right" value={qty} onChange={(e) => setQty(e.target.value)} />
          </Field>
          <Field label="Ngày cần hoàn thành">
            <DateInput min={todayISO()} value={due} onChange={setDue} />
          </Field>
          <Field label="Chi phí sản xuất dự tính" tip="Tiền công, điện, gas… cho cả lệnh. Cộng với tiền nguyên liệu để ra giá vốn.">
            <SuffixInput suffix="₫" inputMode="numeric" className="text-right" placeholder="0" value={formatThousands(cost)} onChange={(e) => setCost(parseThousands(e.target.value))} />
          </Field>
          <div className="sm:col-span-3">
            <Field label="Ghi chú sản xuất (không bắt buộc)">
              <Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: làm cho đơn đặt trước của khách sỉ, giao 29/11" />
            </Field>
          </div>
        </div>
      </Section>

      <Section
        flush
        title="Nguyên liệu cần dùng"
        subtitle={n > 0 ? `Theo định mức cho ${num(n)} khay, đã cộng hao hụt` : 'Nhập số lượng để tính nguyên liệu'}
        right={productId ? <Link to={`/products/${productId}`} className="text-small font-medium text-primary hover:underline">Xem định mức →</Link> : undefined}
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[760px] border-collapse text-body">
            <thead>
              <tr className="bg-thead text-left text-micro uppercase tracking-wide text-ink-muted">
                <th className="px-4 py-2.5 font-semibold">Nguyên liệu</th>
                <th className="px-3 py-2.5 text-right font-semibold">Định mức / khay</th>
                <th className="px-3 py-2.5 text-right font-semibold">Hao hụt</th>
                <th className="px-3 py-2.5 text-right font-semibold">Cần dùng</th>
                <th className="w-[230px] px-3 py-2.5 font-semibold">Tồn kho</th>
                <th className="px-3 py-2.5 text-right font-semibold">Tạm tính</th>
                <th className="px-4 py-2.5 font-semibold">Trạng thái</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr><td colSpan={7} className="px-4 py-8 text-center text-ink-muted">{!productId ? 'Chọn thành phẩm để xem nguyên liệu cần dùng' : bomForbidden ? 'Bạn không có quyền xem định mức - bấm Lưu nháp để máy tính nguyên liệu cần dùng' : bom.isLoading || !lotsReady ? 'Đang tính...' : n > 0 ? 'Sản phẩm chưa có định mức nguyên liệu' : 'Nhập số lượng kế hoạch'}</td></tr>
              )}
              {rows.map((r) => {
                const after = round3(r.stock - r.need);
                const pct = r.stock > 0 ? Math.min(100, (r.need / r.stock) * 100) : 100;
                return (
                  <tr key={r.key} className="border-t border-line align-middle">
                    <td className="px-4 py-3"><div className="font-medium">{r.name}</div><div className="num text-small text-ink-faint">{r.code}</div></td>
                    <td className="px-3 py-3 text-right tabular-nums">{num(r.per)} {unitLabel(r.unit)}</td>
                    <td className="px-3 py-3 text-right tabular-nums">{num(r.waste ?? 0)}%</td>
                    <td className="px-3 py-3 text-right font-semibold tabular-nums">{num(r.need)} {unitLabel(r.unit)}</td>
                    <td className="px-3 py-3">
                      <div className="tabular-nums">{num(r.stock)} {unitLabel(r.unit)}</div>
                      <div className="mt-1 h-1.5 overflow-hidden rounded-full bg-rowalt"><div className={`h-full rounded-full ${r.sufficient ? 'bg-green-fg' : 'bg-red-fg'}`} style={{ width: `${pct}%` }} /></div>
                      <div className={`mt-1 text-small ${after < 0 ? 'text-red-fg' : 'text-ink-faint'}`}>
                        {after < 0 ? `Thiếu ${num(-after)} ${unitLabel(r.unit)}` : `Dùng xong còn ${num(after)} ${unitLabel(r.unit)}`}
                        {r.expired > 0 && ` · không tính ${num(r.expired)} ${unitLabel(r.unit)} đã hết hạn`}
                      </div>
                    </td>
                    <td className="px-3 py-3 text-right tabular-nums">{money(r.cost)}</td>
                    <td className="px-4 py-3">{r.sufficient ? <Chip tone="green">Đủ</Chip> : <Chip tone="red">Thiếu</Chip>}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="flex flex-wrap items-center justify-between gap-2 border-t border-line px-4 py-2.5 text-small text-ink-muted">
          <span>{live ? 'Đối chiếu theo tồn kho hiện tại' : order ? `Đã đối chiếu tồn kho lúc ${dateTimeFull(order.createdAt)}` : 'Đang đối chiếu tồn kho...'}</span>
          <span className="inline-flex items-center gap-1">
            {rows.length} nguyên liệu · Tạm tính <b className="tabular-nums text-ink">{money(materialTotal)}</b>
            <InfoTip label="Ghi chú tạm tính">Tiền nguyên liệu ở bước này là tạm tính theo giá nhập các lô. Sau khi xuất kho (bước 2), hệ thống tính lại đúng theo lô được lấy.</InfoTip>
          </span>
        </div>
      </Section>

      <ActionBar
        stacked
        actions={
          <>
            {order && <Button variant="ghost" className="!text-red-fg" onClick={() => setCancelling(true)}>Hủy lệnh</Button>}
            <Button loading={save.isPending} disabled={!!order && !dirty} onClick={() => run(() => save.mutate())}>Lưu nháp</Button>
            <Button variant="primary" icon={<Check size={15} strokeWidth={2.6} />} disabled={!allEnough} loading={approve.isPending} onClick={() => run(() => approve.mutate())}>
              Duyệt lệnh sản xuất
            </Button>
          </>
        }
      >
        {rows.length === 0 ? (
          <span>Chọn thành phẩm và số lượng để đối chiếu nguyên liệu.</span>
        ) : allEnough ? (
          <span className="inline-flex items-center gap-1.5 text-body font-semibold text-green-fg"><Check size={15} strokeWidth={2.6} aria-hidden />{enough}/{rows.length} nguyên liệu đủ để làm</span>
        ) : (
          <span className="inline-flex flex-wrap items-center gap-2 text-body font-semibold text-red-fg">
            <CircleAlert size={15} aria-hidden />Thiếu {rows.length - enough}/{rows.length} nguyên liệu - cần nhập thêm trước khi duyệt
            <Button className="!h-7 !px-2.5 text-small" icon={<PackagePlus size={14} />} loading={toReceipt.isPending} onClick={() => run(() => toReceipt.mutate())}>Tạo phiếu nhập nguyên liệu</Button>
          </span>
        )}
        <span>Duyệt xong hệ thống tự tạo phiếu xuất nguyên liệu (chờ xuất).</span>
      </ActionBar>

      {order && (
        <ConfirmDialog open={cancelling} danger title={`Hủy lệnh ${order.code}?`} message="Lệnh nháp sẽ chuyển sang Đã hủy." confirmLabel="Hủy lệnh" loading={cancel.isPending} onConfirm={() => cancel.mutate()} onClose={() => setCancelling(false)} />
      )}
    </StepLayout>
  );
}
