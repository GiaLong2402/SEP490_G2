import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { formatThousands, money, num, parseThousands, todayISO } from '@/shared/lib/format';
import { Button, Field, Input, Modal } from '@/shared/ui';
import { receiptsApi } from '../api';
import type { ReceiptListItem } from '../types';

interface Row {
  lineId: number;
  qtyReceived: string;
  lotCode: string;
  mfgDate: string;
  expDate: string;
  unitPrice: string;
}

/**
 * Xác nhận nhập kho phiếu NVL đang chờ (Inter 1 dòng 47): mỗi dòng nhập SL thực nhận (có thể khác dự kiến,
 * 0 = không về), mã lô (trống = tự sinh), NSX / HSD (nếu có), đơn giá nhập → cộng tồn theo lô.
 */
export function RawReceiptCompleteModal({ receipt, onClose }: { receipt: ReceiptListItem | null; onClose: () => void }) {
  const [rows, setRows] = useState<Row[]>([]);
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!receipt) return;
    setRows(receipt.lines.map((l) => ({ lineId: l.id, qtyReceived: String(l.qtyExpected), lotCode: '', mfgDate: '', expDate: '', unitPrice: '' })));
    setNote('');
    setTouched(false);
  }, [receipt]);

  const today = todayISO();
  const errorOf = (r: Row): string | null => {
    const qty = Number(r.qtyReceived);
    if (r.qtyReceived.trim() === '' || !(qty >= 0)) return 'Nhập SL thực nhận (≥ 0)';
    if (qty === 0) return null;
    if (!(Number(parseThousands(r.unitPrice)) > 0)) return 'Nhập đơn giá > 0';
    if (r.mfgDate && r.expDate && r.expDate < r.mfgDate) return 'HSD trước ngày SX';
    if (r.expDate && r.expDate < today) return 'HSD đã qua';
    return null;
  };
  const anyReceived = rows.some((r) => Number(r.qtyReceived) > 0);
  const valid = rows.length > 0 && rows.every((r) => !errorOf(r)) && anyReceived;
  const total = rows.reduce((sum, r) => sum + (Number(r.qtyReceived) > 0 ? Number(r.qtyReceived) * Number(parseThousands(r.unitPrice)) : 0), 0);

  const complete = useAction(
    () =>
      receiptsApi.complete(receipt!.id, {
        lines: rows.map((r) => ({
          lineId: r.lineId,
          qtyReceived: Number(r.qtyReceived),
          lotCode: r.lotCode.trim() || null,
          mfgDate: r.mfgDate || null,
          expDate: r.expDate || null,
          unitPrice: Number(r.qtyReceived) > 0 ? Number(parseThousands(r.unitPrice)) : null,
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
          <span className="mr-auto text-small text-ink-muted">Tổng tiền nhập: <span className="num font-medium text-ink">{money(total)}</span></span>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={complete.isPending}>Xác nhận nhập kho</Button>
        </>
      }
    >
      {receipt && (
        <>
          <p className="mb-3 text-small text-ink-muted">
            Nguồn: <span className="font-medium text-ink">{receipt.source}</span>. Nhập số lượng <b>thực nhận</b> (có thể khác dự kiến; nhập 0 nếu dòng đó không về). Bỏ trống mã lô để hệ thống tự sinh.
          </p>
          <div className="overflow-x-auto">
            <table className="w-full min-w-[860px] text-small">
              <thead>
                <tr className="text-left text-micro font-semibold uppercase text-ink-muted">
                  <th className="pb-1 pr-2">Nguyên liệu</th><th className="pr-2 text-right">Dự kiến</th><th className="w-24 pr-2">Thực nhận</th>
                  <th className="pr-2">Mã lô</th><th className="pr-2">Ngày SX</th><th className="pr-2">HSD</th><th className="w-32 pr-2">Đơn giá (₫)</th>
                </tr>
              </thead>
              <tbody>
                {receipt.lines.map((line, i) => {
                  const r = rows[i];
                  if (!r) return null;
                  const patch = (p: Partial<Row>) => setRows(rows.map((x) => (x.lineId === r.lineId ? { ...x, ...p } : x)));
                  const error = touched ? errorOf(r) : null;
                  const diff = Number(r.qtyReceived) - line.qtyExpected;
                  return (
                    <tr key={line.id} className="align-top">
                      <td className="py-1 pr-2">
                        <div className="font-medium">{line.rawMaterialName}</div>
                        <div className="text-micro text-ink-faint">{line.rawMaterialCode} · {line.unit}</div>
                        {error && <div className="text-micro text-red-fg">{error}</div>}
                      </td>
                      <td className="num py-2 pr-2 text-right">{num(line.qtyExpected)}</td>
                      <td className="py-1 pr-2">
                        <Input aria-label={`Thực nhận ${line.rawMaterialName}`} type="number" min={0} step="0.001" className="text-right" value={r.qtyReceived} onChange={(e) => patch({ qtyReceived: e.target.value })} />
                        {r.qtyReceived !== '' && diff !== 0 && Number.isFinite(diff) && (
                          <div className={`text-right text-micro ${diff < 0 ? 'text-amber-fg' : 'text-blue-fg'}`}>{diff > 0 ? '+' : ''}{num(diff)}</div>
                        )}
                      </td>
                      <td className="py-1 pr-2"><Input aria-label={`Mã lô ${line.rawMaterialName}`} placeholder="Tự sinh" value={r.lotCode} onChange={(e) => patch({ lotCode: e.target.value })} /></td>
                      <td className="py-1 pr-2"><Input aria-label={`Ngày sản xuất ${line.rawMaterialName}`} type="date" max={today} value={r.mfgDate} onChange={(e) => patch({ mfgDate: e.target.value })} /></td>
                      <td className="py-1 pr-2"><Input aria-label={`Hạn sử dụng ${line.rawMaterialName}`} type="date" min={today} value={r.expDate} onChange={(e) => patch({ expDate: e.target.value })} /></td>
                      <td className="py-1 pr-2"><Input aria-label={`Đơn giá ${line.rawMaterialName}`} inputMode="numeric" className="text-right" value={r.unitPrice} onChange={(e) => patch({ unitPrice: formatThousands(e.target.value.replace(/\D/g, '')) })} /></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          {touched && !anyReceived && <p className="mt-2 text-small text-red-fg">Phải có ít nhất một dòng thực nhận lớn hơn 0 — nếu hàng không về, hãy hủy phiếu.</p>}
          <Field label="Ghi chú nhập kho" className="mt-3"><Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: thiếu 0,5 kg so với dự kiến" /></Field>
        </>
      )}
    </Modal>
  );
}
