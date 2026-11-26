import { useEffect, useState } from 'react';
import { useAuth } from '@/features/auth';
import { useAction } from '@/shared/api/useAction';
import { dateTimeFull, num, todayISO } from '@/shared/lib/format';
import { Button, Field, Input, Modal, Select } from '@/shared/ui';
import { receiptsApi } from '../api';
import type { DiscardReason, RawMaterialLot } from '../types';

const reasonLabels: Record<DiscardReason, string> = { expired: 'Hết hạn', damaged: 'Hư hỏng', other: 'Khác' };

/**
 * Bỏ phần còn lại của một lô NVL (đã chốt 24/11/2026): số còn của lô về 0, trừ tồn NVL, ghi sổ NVL.
 * Thủ kho / Chủ bấm, không cần duyệt. Mở từ popup lô hoặc từ cảnh báo lô hết hạn ở màn Xuất nguyên liệu.
 */
export function DiscardLotDialog({ lot, materialName, unit, qtyOnHand, onClose }: { lot: RawMaterialLot | null; materialName: string; unit: string; qtyOnHand?: number; onClose: () => void }) {
  const { user } = useAuth();
  // Ngày theo giờ máy (VN), không theo UTC — khớp backend tính "hết hạn" theo giờ cửa hàng.
  const expired = !!lot?.expDate && lot.expDate < todayISO();
  const [reason, setReason] = useState<DiscardReason>('expired');
  const [note, setNote] = useState('');
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!lot) return;
    setReason(expired ? 'expired' : 'damaged');
    setNote('');
    setTouched(false);
  }, [lot, expired]);

  const noteMissing = reason === 'other' && note.trim() === '';
  const discard = useAction(() => receiptsApi.discardRawLot(lot!.id, { reason, note: note.trim() || null }), {
    success: () => `Đã bỏ ${num(lot!.qtyRemaining)} ${unit} của lô ${lot!.lotCode}`,
    onDone: onClose,
  });

  return (
    <Modal
      open={!!lot}
      title={lot ? <>Bỏ {num(lot.qtyRemaining)} {unit} còn lại của lô <span className="font-mono">{lot.lotCode}</span>?</> : ''}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!noteMissing) discard.mutate();
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy</Button>
          <Button type="submit" variant="danger" loading={discard.isPending}>Xác nhận bỏ</Button>
        </>
      }
    >
      {lot && (
        <div className="space-y-3">
          <p className="text-body text-ink-muted">
            Tồn {materialName} giảm {num(lot.qtyRemaining)} {unit}
            {qtyOnHand !== undefined && <> ({num(qtyOnHand)} → {num(Math.max(0, Math.round((qtyOnHand - lot.qtyRemaining) * 1000) / 1000))} {unit})</>}. Không hoàn tác được.
          </p>
          <Field label="Lý do" required hint={expired ? 'Hết hạn · Hư hỏng · Khác (ghi rõ)' : 'Lô còn hạn: chọn Hư hỏng hoặc Khác (ghi rõ)'}>
            <Select value={reason} onChange={(e) => setReason(e.target.value as DiscardReason)}>
              {(Object.keys(reasonLabels) as DiscardReason[]).map((k) => (
                <option key={k} value={k} disabled={k === 'expired' && !expired}>
                  {reasonLabels[k]}{k === 'expired' && !expired ? ' (lô chưa hết hạn)' : ''}
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Ghi chú" required={reason === 'other'} error={touched && noteMissing ? 'Ghi rõ lý do khi chọn "Khác"' : undefined}>
            <Input value={note} onChange={(e) => setNote(e.target.value)} placeholder="Ví dụ: đã vứt sáng 25/11" />
          </Field>
          <div className="rounded-md bg-rowalt px-3 py-2.5 text-small text-ink-muted">
            Sổ nguyên liệu sẽ ghi:{' '}
            <b className="text-ink">
              Bỏ lô {lot.lotCode} · −{num(lot.qtyRemaining)} {unit} · {reasonLabels[reason]} · {user?.fullName ?? '—'} · {dateTimeFull(new Date().toISOString())}
            </b>
            . Không cần Chủ duyệt.
          </div>
        </div>
      )}
    </Modal>
  );
}
