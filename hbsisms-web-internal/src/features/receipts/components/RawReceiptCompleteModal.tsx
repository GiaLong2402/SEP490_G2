import { useEffect, useState } from 'react';
import { CalendarClock, Truck, UserRound } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { date, dateTimeFull, formatThousands, money, num, parseThousands, todayISO } from '@/shared/lib/format';
import { isPieceUnit } from '@/shared/lib/units';
import { Button, Chip, DateInput, Field, InfoTip, Input, Modal } from '@/shared/ui';
import { receiptsApi } from '../api';
import { useUnitLabel } from '../hooks';
import type { ReceiptListItem } from '../types';

interface Row {
  lineId: number;
  qtyReceived: string;
  lotCode: string;
  mfgDate: string;
  expDate: string;
  unitPrice: string;
}
type RowErrors = Partial<Record<'qtyReceived' | 'unitPrice' | 'expDate', string>>;

const priceOf = (r: Row) => Number(parseThousands(r.unitPrice));

function errorsOf(r: Row, today: string, unit: string): RowErrors {
  const qty = Number(r.qtyReceived);
  if (r.qtyReceived.trim() === '' || !(qty >= 0)) return { qtyReceived: 'Nhập số ≥ 0' };
  if (qty === 0) return {};
  const errors: RowErrors = {};
  if (isPieceUnit(unit) && !Number.isInteger(qty)) {
    errors.qtyReceived = 'Đơn vị đếm cái — nhập số nguyên (không thập phân)';
  }
  if (!(priceOf(r) > 0)) errors.unitPrice = 'Nhập đơn giá > 0';
  if (r.mfgDate && r.expDate && r.expDate < r.mfgDate) errors.expDate = 'Hạn sử dụng trước ngày sản xuất';
  else if (r.expDate && r.expDate < today) errors.expDate = 'Đã quá hạn sử dụng';
  return errors;
}

/**
 * Xác nhận nhập kho phiếu NVL đang chờ (Inter 1 dòng 47): mỗi dòng nhập SL thực nhận (có thể khác dự kiến,
 * 0 = không về), mã lô (trống = tự sinh), NSX / HSD (nếu có), đơn giá nhập → cộng tồn theo lô.
 */
export function RawReceiptCompleteModal({ receipt, onClose }: { receipt: ReceiptListItem | null; onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);
  const unitLabel = useUnitLabel();

  useEffect(() => {
    if (!receipt) return;
    setRows(receipt.lines.map((l) => ({ lineId: l.id, qtyReceived: String(l.qtyExpected), lotCode: '', mfgDate: '', expDate: '', unitPrice: '' })));
    setNote('');
    setTouched(false);
  }, [receipt]);

  const today = todayISO();
  const receivedCount = rows.filter((r) => Number(r.qtyReceived) > 0).length;
  const valid =
    rows.length > 0 &&
    !!receipt &&
    rows.every((r, i) => Object.keys(errorsOf(r, today, receipt.lines[i]?.unit ?? '')).length === 0) &&
    receivedCount > 0;
  const total = rows.reduce((sum, r) => sum + (Number(r.qtyReceived) > 0 ? Number(r.qtyReceived) * priceOf(r) : 0), 0);

  const complete = useAction(
    () =>
      receiptsApi.complete(receipt!.id, {
        lines: rows.map((r) => ({
          lineId: r.lineId,
          qtyReceived: Number(r.qtyReceived),
          lotCode: r.lotCode.trim() || null,
          mfgDate: r.mfgDate || null,
          expDate: r.expDate || null,
          unitPrice: Number(r.qtyReceived) > 0 ? priceOf(r) : null,
        })),
        note: note.trim() || null,
      }),
    { success: (r) => `Đã nhập kho ${r.code}`, onDone: onClose },
  );

  return (
    <Modal
      open={!!receipt}
      title={receipt ? `Xác nhận nhập kho · ${receipt.code}` : ''}
      width="xl"
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (valid) complete.mutate();
      }}
      footer={
        <>
          <span className="mr-auto self-center text-small text-ink-muted">
            Tổng tiền nhập <span className="ml-1 text-h1 tabular-nums text-ink">{money(total)}</span>
            <span className="ml-2 text-ink-faint">· {receivedCount}/{rows.length} dòng có hàng</span>
          </span>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={complete.isPending}>Xác nhận nhập kho</Button>
        </>
      }
    >
      {receipt && (
        <>
          <dl className="grid gap-px overflow-hidden rounded-md border border-line bg-line sm:grid-cols-3">
            {[
              { icon: <Truck size={17} />, label: 'Nguồn hàng', value: receipt.source, sub: null },
              { icon: <CalendarClock size={17} />, label: 'Dự kiến về', value: receipt.expectedDate ? date(receipt.expectedDate) : 'Chưa hẹn', sub: null },
              { icon: <UserRound size={17} />, label: 'Người tạo phiếu', value: receipt.receiverName ?? '-', sub: dateTimeFull(receipt.receivedAt) },
            ].map((item) => (
              <div key={item.label} className="flex items-center gap-3 bg-card px-3 py-3">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-primary-soft text-primary" aria-hidden>{item.icon}</span>
                <div className="min-w-0">
                  <dt className="text-small text-ink-muted">{item.label}</dt>
                  <dd className="break-words text-h2 text-ink">{item.value}</dd>
                  {item.sub && <dd className="text-small text-ink-faint">Lúc {item.sub}</dd>}
                </div>
              </div>
            ))}
          </dl>

          <div className="mb-2 mt-4 flex items-center gap-1.5">
            <h3 className="text-h2 text-ink">Hàng thực nhận</h3>
            <span className="text-small text-ink-faint">({rows.length} dòng)</span>
            <InfoTip label="Hướng dẫn nhập kho">
              <span className="block font-semibold">Cách điền:</span>
              <span className="block">• Ghi số hàng <b>thực tế nhận được</b>. Khác số dự kiến cũng được.</span>
              <span className="block">• Món nào không về thì ghi số 0.</span>
              <span className="block">• Có hàng thì phải ghi giá mua của 1 đơn vị.</span>
              <span className="block">• Hạn sử dụng phải sau ngày sản xuất và chưa hết hạn.</span>
            </InfoTip>
          </div>

          <ul className="space-y-2.5">
            {receipt.lines.map((line, i) => {
              const r = rows[i];
              if (!r) return null;
              const patch = (p: Partial<Row>) => setRows(rows.map((x) => (x.lineId === r.lineId ? { ...x, ...p } : x)));
              const errors = touched ? errorsOf(r, today, line.unit) : {};
              const hasError = Object.keys(errors).length > 0;
              const qty = Number(r.qtyReceived);
              const skipped = r.qtyReceived.trim() !== '' && qty === 0;
              const piece = isPieceUnit(line.unit);
              const diff = qty - line.qtyExpected;
              const lineTotal = qty > 0 ? qty * priceOf(r) : 0;
              return (
                <li key={line.id} className={`rounded-md border ${hasError ? 'border-red-fg/60' : 'border-line'} ${skipped ? 'bg-rowalt/60' : 'bg-card'}`}>
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 rounded-t-md border-b border-line bg-rowalt/60 px-3 py-2">
                    <span className="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary text-micro font-semibold text-card">{i + 1}</span>
                    <span className="font-semibold text-ink">{line.rawMaterialName}</span>
                    <span className="text-small text-ink-faint">{line.rawMaterialCode}</span>
                    {r.qtyReceived.trim() !== '' && Number.isFinite(diff) && (
                      skipped ? <Chip>Không về</Chip>
                        : diff === 0 ? <Chip tone="green">Đủ số lượng</Chip>
                          : <Chip tone={diff < 0 ? 'amber' : 'blue'}>{diff < 0 ? 'Thiếu' : 'Dư'} {num(Math.abs(diff))} {unitLabel(line.unit)}</Chip>
                    )}
                    <span className="ml-auto text-small text-ink-muted">
                      Thành tiền <span className="ml-1 text-body font-semibold tabular-nums text-ink">{lineTotal > 0 ? money(lineTotal) : '-'}</span>
                    </span>
                  </div>
                  <div className="grid gap-3 p-3 sm:grid-cols-2 lg:grid-cols-5">
                    <Field label={`Thực nhận (${unitLabel(line.unit)})`} required error={errors.qtyReceived} hint={`Dự kiến ${num(line.qtyExpected)} ${unitLabel(line.unit)}${piece ? ' · số nguyên' : ''}`}>
                      <Input type="number" min={0} step={piece ? 1 : '0.001'} className="text-right" value={r.qtyReceived} onChange={(e) => patch({ qtyReceived: e.target.value })} />
                    </Field>
                    <Field label="Đơn giá (₫)" required={!skipped} error={errors.unitPrice}>
                      <Input inputMode="numeric" className="text-right" placeholder="0" disabled={skipped} value={r.unitPrice} onChange={(e) => patch({ unitPrice: formatThousands(e.target.value.replace(/\D/g, '')) })} />
                    </Field>
                    <Field
                      label="Mã lô"
                      tip={<><span className="block">Không có mã lô thì để trống, máy tự đặt.</span><span className="block">Ví dụ: <b>NVL-261123-01</b> = lô thứ 1 của ngày 23/11/2026.</span></>}
                    >
                      <Input placeholder="Tự sinh" disabled={skipped} value={r.lotCode} onChange={(e) => patch({ lotCode: e.target.value })} />
                    </Field>
                    <Field label="Ngày sản xuất">
                      <DateInput max={today} disabled={skipped} value={r.mfgDate} onChange={(v) => patch({ mfgDate: v })} />
                    </Field>
                    <Field label="Hạn sử dụng" error={errors.expDate}>
                      <DateInput min={today} disabled={skipped} value={r.expDate} onChange={(v) => patch({ expDate: v })} />
                    </Field>
                  </div>
                </li>
              );
            })}
          </ul>

          {touched && receivedCount === 0 && (
            <p className="mt-2 text-small text-red-fg">Phải có ít nhất một dòng thực nhận lớn hơn 0 - nếu hàng không về, hãy hủy phiếu.</p>
          )}
          <Field label="Ghi chú nhập kho" className="mt-4">
            <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: thiếu 0,5 kg so với dự kiến" />
          </Field>
        </>
      )}
    </Modal>
  );
}
