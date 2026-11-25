import { useEffect, useState } from 'react';
import { useFinishedWarehouses } from '@/features/warehouses';
import { useAction } from '@/shared/api/useAction';
import { money, num, todayISO } from '@/shared/lib/format';
import { Alert, Button, DateInput, Field, Input, Modal } from '@/shared/ui';
import { receiptsApi } from '../api';
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
}

/**
 * Trạng thái + kiểm tra + gửi của form nhập kho phiếu thành phẩm (dòng 52, đã chốt 24/11/2026):
 * vị trí, mã lô, NSX / HSD; số khay điền sẵn = số báo đạt và vẫn sửa được. Số ở bước này là số cuối,
 * giá vốn chia theo số này; nhập xong lệnh tự chuyển Hoàn thành. Dùng chung cho form trong trang lệnh và popup ở màn Nhập kho.
 */
export function useFinishedReceiptForm(receipt: ReceiptListItem | null | undefined, context: FinishedReceiptContext | null, onDone?: () => void) {
  const warehouses = useFinishedWarehouses().data ?? [];
  const [lotCode, setLotCode] = useState('');
  const [mfgDate, setMfgDate] = useState('');
  const [expDate, setExpDate] = useState('');
  const [qty, setQty] = useState('');
  const [locationId, setLocationId] = useState('');
  const [touched, setTouched] = useState(false);
  const reported = context?.reportedQty ?? receipt?.items[0]?.qty ?? 0;

  useEffect(() => {
    if (!receipt) return;
    setLotCode('');
    setMfgDate(context?.mfgDate ?? todayISO());
    setExpDate(todayISO(14));
    setQty(reported ? String(reported) : '');
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
  const qtyValid = Number.isInteger(n) && n > 0;
  const room = Number(locationId) > 0 ? roomOf.get(Number(locationId)) ?? 0 : null;
  const errors = {
    qty: !qtyValid ? 'Nhập số khay nguyên > 0' : undefined,
    expDate: !expDate ? 'Chọn hạn sử dụng' : mfgDate && expDate < mfgDate ? 'Hạn sử dụng trước ngày sản xuất' : undefined,
    location: !(Number(locationId) > 0) ? 'Chọn vị trí cất hàng' : room !== null && qtyValid && n > room ? `Vị trí chỉ còn ${num(room)} khay` : undefined,
  };
  const valid = !errors.qty && !errors.expDate && !errors.location;
  const unitCost = context?.totalCost != null && qtyValid ? Math.round(context.totalCost / n) : null;

  const mutation = useAction(
    () => receiptsApi.completeFinished(receipt!.id, { lotCode: lotCode.trim() || null, mfgDate: mfgDate || null, expDate, locationId: Number(locationId), qty: n, note: null }),
    { success: (r) => `Đã nhập kho ${r.code} - lệnh sản xuất chuyển Hoàn thành`, onDone },
  );
  const submit = () => {
    setTouched(true);
    if (valid) mutation.mutate();
  };

  return {
    warehouses, roomOf, reported, n, qtyValid, room, errors, touched, unitCost, mutation, submit,
    locationText: locationLabel.get(locationId) ?? null,
    values: { lotCode, mfgDate, expDate, qty, locationId },
    set: { lotCode: setLotCode, mfgDate: setMfgDate, expDate: setExpDate, qty: setQty, locationId: setLocationId },
  };
}
export type FinishedReceiptForm = ReturnType<typeof useFinishedReceiptForm>;

/** Các ô của form nhập kho thành phẩm (theo bản thiết kế bước 04). */
export function FinishedReceiptFields({ form, productLabel }: { form: FinishedReceiptForm; productLabel: string }) {
  const { values: v, set, errors, touched, reported, room, qtyValid, n } = form;
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-12">
      <Field label="Quy cách" className="lg:col-span-7" tip="Lấy theo lệnh, không đổi được.">
        <Input value={productLabel} disabled />
      </Field>
      <Field label="Mã lô" className="lg:col-span-5" tip="Không có mã lô thì để trống, máy tự đặt.">
        <Input placeholder={`Tự sinh: LOT-${todayISO().slice(2).replace(/-/g, '')}-01`} value={v.lotCode} onChange={(e) => set.lotCode(e.target.value)} />
      </Field>
      <Field label="Ngày sản xuất" className="lg:col-span-3" tip="Lấy từ ngày làm xong.">
        <DateInput max={todayISO()} value={v.mfgDate} onChange={set.mfgDate} />
      </Field>
      <Field label="Hạn sử dụng" required className="lg:col-span-3" error={touched ? errors.expDate : undefined}>
        <DateInput value={v.expDate} onChange={set.expDate} />
      </Field>
      <Field label="Số khay" required className="lg:col-span-2" error={touched ? errors.qty : undefined} tip={`Điền sẵn = số báo đạt (${num(reported)}). Sửa được nếu đếm lại thấy khác; giá vốn chia theo số này.`}>
        <Input type="number" min={1} className="text-right" value={v.qty} onChange={(e) => set.qty(e.target.value)} />
      </Field>
      <Field
        label="Vị trí"
        required
        className="lg:col-span-4"
        error={touched ? errors.location : undefined}
        hint={room !== null ? `Nhập xong còn trống ${num(Math.max(0, room - (qtyValid ? n : 0)))} khay` : undefined}
        tip="Bấm để xem các kho còn chỗ, rê chuột vào kho để xem các chỗ trong kho."
      >
        <LocationPicker warehouses={form.warehouses} value={v.locationId} need={qtyValid ? n : 1} roomFor={(id) => form.roomOf.get(id) ?? 0} onChange={set.locationId} />
      </Field>
      {qtyValid && reported > 0 && n !== reported && (
        <div className="sm:col-span-2 lg:col-span-12">
          <Alert tone="warning">Báo đạt {num(reported)} khay, nhập kho {num(n)} khay. Lệnh sẽ ghi lại "Báo {num(reported)} · Nhập kho {num(n)}".</Alert>
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
              Giá vốn / khay: {money(context!.totalCost)} ÷ {num(form.n)} = <b className="tabular-nums text-ink">{money(form.unitCost)}</b>
            </div>
          )}
        </div>
      )}
    </Modal>
  );
}
