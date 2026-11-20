import { useEffect, useMemo, useState } from 'react';
import { useLocations } from '@/features/inventory';
import type { LocationOption } from '@/features/inventory/types';
import { Button, Field, Input, Modal, Select } from '@/shared/ui';
import { returnsApi } from '../api';
import type { ReturnInspectDefaults, ReturnInspectLine, ReturnItemView, ReturnListItem } from '../types';

type LineDraft = {
  variantId: number;
  lotId: number | null;
  sku: string;
  label: string;
  qty: number;
  passQty: string;
  failQty: string;
  passLocationId: string;
  failLocationId: string;
  shippedHint: string | null;
  quarantineHint: string | null;
};

function isQuarantineZone(zone: string | null | undefined) {
  const z = (zone ?? '')
    .normalize('NFD')
    .replace(/\p{M}/gu, '')
    .toLowerCase();
  return z.includes('quarantine') || z.includes('cach ly') || z.includes('hang tra');
}

function pickPassFallback(locations: LocationOption[]) {
  const available = locations.find((l) => !isQuarantineZone(l.zone)) ?? locations[0];
  return available ? String(available.id) : '';
}

/** Chỉ CL cùng kho với vị trí xuất/nhập lại — không nhảy sang kho khác. */
function pickQuarantineId(
  locations: LocationOption[],
  defaults: ReturnInspectDefaults | null,
  line: ReturnInspectDefaults['lines'][number] | undefined,
  passLocationId: string,
): string {
  const pass = locations.find((l) => String(l.id) === passLocationId);
  if (pass) {
    const sameWh = locations.find((l) => isQuarantineZone(l.zone) && l.warehouseId === pass.warehouseId);
    return sameWh ? String(sameWh.id) : '';
  }
  if (line?.quarantineLocationId != null) return String(line.quarantineLocationId);
  if (defaults?.quarantineLocationId != null) return String(defaults.quarantineLocationId);
  return '';
}

function toDrafts(
  items: ReturnItemView[],
  locations: LocationOption[],
  defaults: ReturnInspectDefaults | null,
): LineDraft[] {
  const passFallback = pickPassFallback(locations);

  return items.map((item) => {
    const hint = defaults?.lines.find((l) => l.variantId === item.variantId);
    const passId = hint?.shippedLocationId != null
      ? String(hint.shippedLocationId)
      : passFallback;
    const failId = pickQuarantineId(locations, defaults, hint, passId);
    return {
      variantId: item.variantId,
      lotId: hint?.lotId ?? item.lotId,
      sku: item.sku,
      label: `${item.productName} · ${item.sku}`,
      qty: item.qty,
      // An toàn: mặc định đưa hết vào cách ly; NV chỉnh số đạt khi kiểm xong.
      passQty: '0',
      failQty: String(item.qty),
      passLocationId: passId,
      failLocationId: failId,
      shippedHint: hint?.shippedLocationLabel ?? null,
      quarantineHint: hint?.quarantineLocationLabel
        ?? defaults?.quarantineLabel
        ?? null,
    };
  });
}

function buildInspections(drafts: LineDraft[]): { ok: true; lines: ReturnInspectLine[] } | { ok: false; error: string } {
  const lines: ReturnInspectLine[] = [];
  for (const d of drafts) {
    const passQty = Number(d.passQty);
    const failQty = Number(d.failQty);
    if (!Number.isInteger(passQty) || !Number.isInteger(failQty) || passQty < 0 || failQty < 0) {
      return { ok: false, error: `Số khay kiểm của ${d.sku} phải là số nguyên ≥ 0` };
    }
    if (passQty + failQty !== d.qty) {
      return { ok: false, error: `${d.sku}: đạt + không đạt phải bằng ${d.qty} khay trên phiếu` };
    }
    if (passQty > 0) {
      if (!d.passLocationId) return { ok: false, error: `${d.sku}: chọn vị trí nhập lại (đạt)` };
      lines.push({
        variantId: d.variantId,
        lotId: d.lotId,
        locationId: Number(d.passLocationId),
        passed: true,
        qty: passQty,
      });
    }
    if (failQty > 0) {
      if (!d.failLocationId) return { ok: false, error: `${d.sku}: chọn vị trí khu hàng trả (không đạt)` };
      lines.push({
        variantId: d.variantId,
        lotId: d.lotId,
        locationId: Number(d.failLocationId),
        passed: false,
        qty: failQty,
      });
    }
  }
  if (!lines.length) return { ok: false, error: 'Chưa có dòng kiểm hợp lệ' };
  return { ok: true, lines };
}

interface Props {
  open: boolean;
  row: ReturnListItem | null;
  loading?: boolean;
  onClose: () => void;
  onConfirmInspect: (inspections: ReturnInspectLine[]) => void;
  onConfirmQuarantineAll: () => void;
}

/** Task #30 — đạt → vị trí đã xuất; không đạt → CL-01 cùng kho xuất. */
export function ReceiveInspectModal({ open, row, loading, onClose, onConfirmInspect, onConfirmQuarantineAll }: Props) {
  const { data: locations = [] } = useLocations();
  const [defaults, setDefaults] = useState<ReturnInspectDefaults | null>(null);
  const [drafts, setDrafts] = useState<LineDraft[]>([]);
  const [touched, setTouched] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!open || !row) return;
    let cancelled = false;
    setDefaults(null);
    setTouched(false);
    setError(null);
    returnsApi
      .inspectDefaults(row.id)
      .then((d) => {
        if (!cancelled) setDefaults(d);
      })
      .catch(() => {
        if (!cancelled) setDefaults(null);
      });
    return () => {
      cancelled = true;
    };
  }, [open, row]);

  useEffect(() => {
    if (!open || !row) return;
    setDrafts(toDrafts(row.items, locations, defaults));
  }, [open, row, locations, defaults]);

  const shelfOptions = useMemo(
    () => locations.filter((l) => !isQuarantineZone(l.zone)),
    [locations],
  );
  const allQuarantine = useMemo(
    () => locations.filter((l) => isQuarantineZone(l.zone)),
    [locations],
  );
  const receiveBack = row?.deliveryId != null;

  const quarantineOptionsFor = (passLocationId: string) => {
    const pass = locations.find((l) => String(l.id) === passLocationId);
    if (!pass) return allQuarantine;
    return allQuarantine.filter((l) => l.warehouseId === pass.warehouseId);
  };

  const patch = (variantId: number, part: Partial<LineDraft>) => {
    setDrafts((prev) => prev.map((d) => {
      if (d.variantId !== variantId) return d;
      const next = { ...d, ...part };
      if (part.passLocationId != null && part.failLocationId === undefined) {
        const hint = defaults?.lines.find((l) => l.variantId === variantId);
        next.failLocationId = pickQuarantineId(locations, defaults, hint, part.passLocationId);
        const q = locations.find((l) => String(l.id) === next.failLocationId);
        next.quarantineHint = q ? q.label : next.quarantineHint;
      }
      return next;
    }));
  };

  const submitInspect = () => {
    setTouched(true);
    const result = buildInspections(drafts);
    if (!result.ok) {
      setError(result.error);
      return;
    }
    setError(null);
    onConfirmInspect(result.lines);
  };

  return (
    <Modal
      open={open}
      title={row ? `Kiểm hàng · ${row.code}` : 'Kiểm hàng trả'}
      width="lg"
      onClose={onClose}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button loading={loading} onClick={onConfirmQuarantineAll}>
            {receiveBack ? 'Nhận nhanh (HOLD / khu hàng trả)' : 'Đưa hết vào khu hàng trả'}
          </Button>
          <Button variant="primary" loading={loading} onClick={submitInspect}>
            Xác nhận kiểm & cập nhật tồn
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        <p className="text-small text-ink-muted">
          {receiveBack
            ? 'Giao thất bại còn HOLD: xác nhận chỉ nhả giữ về kệ — không vào khu hàng trả (tránh nhân đôi tồn). Khu hàng trả chỉ khi hàng đã xuất (OUT) rồi trả về.'
            : 'Mỗi kho một khu hàng trả (HT-01). Đạt → kệ đã xuất; không đạt → HT cùng kho (không về kệ bán).'}
        </p>
        {!shelfOptions.length && <p className="text-small text-amber-fg">Chưa tải được danh sách vị trí kho.</p>}
        {!allQuarantine.length && (
          <p className="text-small text-amber-fg">
            Chưa có vị trí zone «Khu hàng trả» — restart API (seed HT-01 mỗi kho) hoặc tạo trong Kho.
          </p>
        )}
        <ul className="space-y-3 border-t border-line pt-3">
          {drafts.map((d) => {
            const qOpts = quarantineOptionsFor(d.passLocationId);
            return (
              <li key={d.variantId} className="space-y-2 rounded-md border border-line p-3">
                <div className="flex flex-wrap items-baseline justify-between gap-2">
                  <span className="font-medium">{d.label}</span>
                  <span className="text-small text-ink-faint">
                    Trên phiếu: {d.qty} khay{d.lotId ? ` · lot #${d.lotId}` : ''}
                  </span>
                </div>
                <div className="grid gap-2 sm:grid-cols-2">
                  <Field label="Số khay đạt (nhập lại)">
                    <Input
                      type="number"
                      min={0}
                      max={d.qty}
                      className="text-right"
                      value={d.passQty}
                      onChange={(e) => {
                        const passQty = e.target.value;
                        const n = Number(passQty);
                        const fail = Number.isInteger(n) ? String(Math.max(0, d.qty - n)) : d.failQty;
                        patch(d.variantId, { passQty, failQty: fail });
                      }}
                    />
                  </Field>
                  <Field
                    label="Vị trí nhập lại"
                    hint={d.shippedHint ? `Theo vị trí đã xuất: ${d.shippedHint}` : 'Chưa suy được vị trí xuất — chọn tay'}
                  >
                    <Select
                      aria-label={`Vị trí đạt ${d.sku}`}
                      value={d.passLocationId}
                      onChange={(e) => patch(d.variantId, { passLocationId: e.target.value })}
                      disabled={Number(d.passQty) <= 0}
                    >
                      <option value="">Chọn…</option>
                      {shelfOptions.map((l) => (
                        <option key={l.id} value={l.id}>{l.label}</option>
                      ))}
                    </Select>
                  </Field>
                  <Field label="Số khay không đạt (khu hàng trả)">
                    <Input
                      type="number"
                      min={0}
                      max={d.qty}
                      className="text-right"
                      value={d.failQty}
                      onChange={(e) => {
                        const failQty = e.target.value;
                        const n = Number(failQty);
                        const pass = Number.isInteger(n) ? String(Math.max(0, d.qty - n)) : d.passQty;
                        patch(d.variantId, { failQty, passQty: pass });
                      }}
                    />
                  </Field>
                  <Field
                    label="Vị trí khu hàng trả (cùng kho)"
                    hint={
                      d.quarantineHint
                        ? `HT cùng kho xuất: ${d.quarantineHint}`
                        : 'Chưa có HT-01 trên kho này — tạo zone «Khu hàng trả» trong Kho'
                    }
                  >
                    <Select
                      aria-label={`Vị trí khu hàng trả ${d.sku}`}
                      value={d.failLocationId}
                      onChange={(e) => patch(d.variantId, { failLocationId: e.target.value })}
                      disabled={Number(d.failQty) <= 0 || !qOpts.length}
                    >
                      <option value="">{qOpts.length ? 'Chọn…' : 'Chưa có khu hàng trả'}</option>
                      {qOpts.map((l) => (
                        <option key={l.id} value={l.id}>{l.label}</option>
                      ))}
                    </Select>
                  </Field>
                </div>
              </li>
            );
          })}
        </ul>
        {(touched || error) && error && <p className="text-small text-red-fg">{error}</p>}
      </div>
    </Modal>
  );
}
