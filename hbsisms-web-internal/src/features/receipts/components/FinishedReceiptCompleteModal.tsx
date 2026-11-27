import { useEffect, useMemo, useState } from 'react';
import { useFinishedWarehouses } from '@/features/warehouses';
import { useAction } from '@/shared/api/useAction';
import { money, num, todayISO } from '@/shared/lib/format';
import { Alert, Button, DateInput, Field, Input, Modal } from '@/shared/ui';
import { receiptsApi } from '../api';
import { locationMatchesPreferred, preferredFinishedZones } from '../preferredFinishedZones';
import type { ReceiptListItem } from '../types';
import { LocationPicker } from './LocationPicker';

export interface FinishedReceiptContext {
  /** Quy cách thành phẩm, ví dụ "Chả mực Hạ Long · khay 500g". */
  productLabel: string;
  /** Số khay xưởng báo đạt — điền sẵn. */
  reportedQty: number;
  /** Tổng chi phí của lệnh (NVL thật đã trừ phần dư + chi phí sản xuất) để xem trước giá vốn / khay. */
  totalCost: number | null;
  /** Ngày làm xong → điền sẵn ngày sản xuất. */
  mfgDate?: string | null;
  /** Mã / tên SP để gợi ý khu kho (CHA, CA…). */
  productCode?: string | null;
  productName?: string | null;
}

/**
 * Trạng thái + kiểm tra + gửi của form nhập kho phiếu thành phẩm (dòng 52, đã chốt 24/11/2026):
 * vị trí, mã lô, NSX / HSD; số khay điền sẵn = số báo đạt và vẫn sửa được (một quy cách).
 * Nhiều quy cách (chia 1 kg / 0,5 kg): giữ đúng số từng dòng phiếu, giá vốn chia theo khối lượng.
 */
export function useFinishedReceiptForm(receipt: ReceiptListItem | null | undefined, context: FinishedReceiptContext | null, onDone?: () => void) {
  const warehouses = useFinishedWarehouses().data ?? [];
  const [lotCode, setLotCode] = useState('');
  const [mfgDate, setMfgDate] = useState('');
  const [expDate, setExpDate] = useState('');
  const [qty, setQty] = useState('');
  const [locationId, setLocationId] = useState('');
  const [touched, setTouched] = useState(false);

  const packItems = useMemo(() => receipt?.items ?? [], [receipt?.items]);
  const multiPack = packItems.length > 1;
  const packTotal = packItems.reduce((s, i) => s + (i.qty ?? 0), 0);
  const reported = context?.reportedQty ?? (packTotal > 0 ? packTotal : receipt?.items[0]?.qty ?? 0);
  const preferredZones = useMemo(
    () => preferredFinishedZones(context?.productCode, context?.productName ?? context?.productLabel),
    [context?.productCode, context?.productName, context?.productLabel],
  );

  useEffect(() => {
    if (!receipt) return;
    setLotCode('');
    setMfgDate(context?.mfgDate ?? todayISO());
    setExpDate(todayISO(14));
    setQty(multiPack ? String(packTotal) : reported ? String(reported) : '');
    setLocationId('');
    setTouched(false);
  }, [receipt?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const roomOf = new Map<number, number>();
  for (const w of warehouses) {
    const whRoom = w.capacity - w.totalQty;
    for (const l of w.locations) roomOf.set(Number(l.id), Math.max(0, Math.min(l.capacity - l.usedQty, whRoom)));
  }
  const locationLabel = new Map(warehouses.flatMap((w) => w.locations.map((l) => [String(l.id), `${l.code} (${w.code})`] as const)));
  const n = Number(qty);
  const qtyValid = Number.isInteger(n) && n > 0 && (!multiPack || n === packTotal);
  const room = Number(locationId) > 0 ? roomOf.get(Number(locationId)) ?? 0 : null;
  const errors = {
    qty: !qtyValid
      ? multiPack
        ? `Giữ đúng tổng đóng gói ${num(packTotal)} khay`
        : 'Nhập số khay nguyên > 0'
      : undefined,
    expDate: !expDate ? 'Chọn hạn sử dụng' : mfgDate && expDate < mfgDate ? 'Hạn sử dụng trước ngày sản xuất' : undefined,
    location: !(Number(locationId) > 0) ? 'Chọn vị trí cất hàng' : room !== null && qtyValid && n > room ? `Vị trí chỉ còn ${num(room)} khay` : undefined,
  };
  const valid = !errors.qty && !errors.expDate && !errors.location;
  /** Quy đổi khay 1 kg (0,5 kg = nửa) — dùng chia giá vốn; khác số khay vật lý xếp kho. */
  const packWeightKg = multiPack
    ? packItems.reduce((s, i) => {
        const name = (i.name ?? '').toLowerCase().replace(/\s+/g, '');
        // SKU kiểu …-05 / …05, nhãn 0,5 kg / 500g…
        const half =
          /0[,.]5|500g|1\/2|half|\/2kg/.test(name)
          || /(?:^|[-_])05(?:$|[-_])/.test(name)
          || /(?:^|[-_])500(?:g)?(?:$|[-_])/.test(name);
        const w = half ? 0.5 : 1;
        return s + (i.qty ?? 0) * w;
      }, 0)
    : n;
  const unitCost =
    context?.totalCost != null && qtyValid && packWeightKg > 0
      ? Math.round(context.totalCost / packWeightKg)
      : null;

  const mutation = useAction(
    () => receiptsApi.completeFinished(receipt!.id, { lotCode: lotCode.trim() || null, mfgDate: mfgDate || null, expDate, locationId: Number(locationId), qty: n, note: null }),
    { success: (r) => `Đã nhập kho ${r.code} - lệnh sản xuất chuyển Hoàn thành`, onDone },
  );
  const submit = () => {
    setTouched(true);
    if (valid) mutation.mutate();
  };

  const selectedLoc = useMemo(() => {
    if (!(Number(locationId) > 0)) return null;
    for (const w of warehouses) {
      const l = w.locations.find((x) => String(x.id) === locationId);
      if (l) return l;
    }
    return null;
  }, [warehouses, locationId]);
  const locationOffPrefer =
    preferredZones.length > 0
    && selectedLoc != null
    && !locationMatchesPreferred(selectedLoc, preferredZones);

  return {
    warehouses, roomOf, reported, n, qtyValid, room, errors, touched, unitCost, mutation, submit,
    multiPack, packItems, packTotal, packWeightKg, preferredZones, locationOffPrefer,
    locationText: locationLabel.get(locationId) ?? null,
    values: { lotCode, mfgDate, expDate, qty, locationId },
    set: { lotCode: setLotCode, mfgDate: setMfgDate, expDate: setExpDate, qty: setQty, locationId: setLocationId },
  };
}
export type FinishedReceiptForm = ReturnType<typeof useFinishedReceiptForm>;

/** Các ô của form nhập kho thành phẩm (theo bản thiết kế bước 04). */
export function FinishedReceiptFields({ form, productLabel }: { form: FinishedReceiptForm; productLabel: string }) {
  const { values: v, set, errors, touched, reported, room, qtyValid, n, multiPack, packItems, preferredZones, locationOffPrefer } = form;
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
      <Field label="Quy cách" className="lg:col-span-7" tip="Lấy theo lệnh đóng gói, không đổi được.">
        {multiPack ? (
          <ul className="rounded-md border border-line bg-rowalt/40 px-3 py-2 space-y-1.5">
            {packItems.map((item, idx) => (
              <li key={`${item.name}-${idx}`} className="flex items-baseline justify-between gap-3 text-body">
                <span className="min-w-0 break-all font-mono text-ink">{item.name}</span>
                <span className="shrink-0 tabular-nums text-ink-muted">× {num(item.qty)}</span>
              </li>
            ))}
          </ul>
        ) : (
          <Input value={productLabel} disabled />
        )}
      </Field>
      <Field label="Mã lô" className="lg:col-span-5" tip={multiPack ? 'Để trống máy tự sinh; nếu nhập, mỗi quy cách thêm hậu tố -1, -2.' : 'Không có mã lô thì để trống, máy tự đặt.'}>
        <Input placeholder={`Tự sinh: LOT-${todayISO().slice(2).replace(/-/g, '')}-01`} value={v.lotCode} onChange={(e) => set.lotCode(e.target.value)} />
      </Field>
      <Field label="Ngày sản xuất" className="lg:col-span-3" tip="Lấy từ ngày làm xong.">
        <DateInput max={todayISO()} value={v.mfgDate} onChange={set.mfgDate} />
      </Field>
      <Field label="Hạn sử dụng" required className="lg:col-span-3" error={touched ? errors.expDate : undefined}>
        <DateInput value={v.expDate} onChange={set.expDate} />
      </Field>
      <Field
        label={multiPack ? 'Số khay vật lý' : 'Số khay'}
        required
        className="lg:col-span-2"
        error={touched ? errors.qty : undefined}
        tip={
          multiPack
            ? `Tổng khay xếp kho (${num(form.packTotal)}). Khác số đạt quy đổi 1 kg (${num(form.packWeightKg)}) — dùng để chọn vị trí còn chỗ.`
            : `Điền sẵn = số báo đạt (${num(reported)}). Sửa được nếu đếm lại thấy khác; giá vốn chia theo số này.`
        }
      >
        <Input
          type="number"
          min={1}
          className="text-right"
          value={v.qty}
          disabled={multiPack}
          onChange={(e) => set.qty(e.target.value)}
        />
      </Field>
      <Field
        label="Vị trí"
        required
        className="lg:col-span-4"
        error={touched ? errors.location : undefined}
        hint={room !== null ? `Nhập xong còn trống ${num(Math.max(0, room - (qtyValid ? n : 0)))} khay` : undefined}
        tip={
          preferredZones.length
            ? `Ưu tiên khu ${preferredZones.join('/')} theo sản phẩm; vẫn chọn được khu khác khi thiếu chỗ.`
            : 'Bấm để xem các kho còn chỗ, rê chuột vào kho để xem các chỗ trong kho.'
        }
      >
        <LocationPicker
          warehouses={form.warehouses}
          value={v.locationId}
          need={qtyValid ? n : 1}
          roomFor={(id) => form.roomOf.get(id) ?? 0}
          onChange={set.locationId}
          preferredZones={preferredZones}
        />
      </Field>
      {locationOffPrefer && (
        <div className="sm:col-span-2 lg:col-span-12">
          <Alert tone="warning">
            Vị trí đang chọn không thuộc khu gợi ý ({preferredZones.join('/')}
            ). Vẫn nhập được — nên xếp đúng khu khi còn chỗ để dễ lấy hàng.
          </Alert>
        </div>
      )}
      {!multiPack && qtyValid && reported > 0 && n !== reported && (
        <div className="sm:col-span-2 lg:col-span-12">
          <Alert tone="warning">Báo đạt {num(reported)} khay, nhập kho {num(n)} khay. Lệnh sẽ ghi lại "Báo {num(reported)} · Nhập kho {num(n)}".</Alert>
        </div>
      )}
      {multiPack && (
        <div className="sm:col-span-2 lg:col-span-12">
          <Alert tone="info">
            Giá vốn chia theo {num(form.packWeightKg)} khay quy đổi 1 kg (xếp kho {num(form.packTotal)} khay vật lý).
          </Alert>
        </div>
      )}
    </div>
  );
}

/** Popup nhập kho phiếu thành phẩm chờ — dùng ở màn Nhập kho. */
export function FinishedReceiptCompleteModal({ receipt, context, onClose }: { receipt: ReceiptListItem | null; context: FinishedReceiptContext | null; onClose: () => void }) {
  const form = useFinishedReceiptForm(receipt, context, onClose);
  return (
    <Modal
      open={!!receipt}
      title={receipt ? `Nhập kho phiếu ${receipt.code}` : ''}
      width="xl"
      onClose={onClose}
      onSubmit={form.submit}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={form.mutation.isPending}>Nhập kho</Button></>}
    >
      {receipt && (
        <div className="space-y-3">
          <p className="text-small text-ink-muted">Nguồn: lệnh <span className="num text-ink">{receipt.source ?? '—'}</span></p>
          <FinishedReceiptFields form={form} productLabel={context?.productLabel ?? receipt.items[0]?.name ?? '—'} />
          {form.unitCost !== null && (
            <div className="rounded-md bg-rowalt px-3 py-2 text-small text-ink-muted">
              Giá vốn quy đổi / khay 1 kg: ≈ <b className="tabular-nums text-ink">{money(form.unitCost)}</b>
              {context?.totalCost != null ? ` (tổng ${money(context.totalCost)})` : ''}
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
