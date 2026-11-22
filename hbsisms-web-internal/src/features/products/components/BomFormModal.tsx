import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import { Button, Field, IconButton, Input, Modal, Select } from '@/shared/ui';
import { productsApi } from '../api';
import { useBomRawMaterials, useUnits } from '../hooks';
import type { BomDetail, BomLineInput } from '../types';

interface Row {
  rawMaterialId: string;
  qty: string;
  unitId: string;
  wastePercent: string;
}

const emptyRow = (): Row => ({ rawMaterialId: '', qty: '', unitId: '', wastePercent: '' });

export function BomFormModal({
  open,
  productId,
  productCode,
  bom,
  onClose,
}: {
  open: boolean;
  productId: ID;
  productCode: string;
  bom?: BomDetail;
  onClose: () => void;
}) {
  const materials = useBomRawMaterials(open);
  const units = useUnits({ activeOnly: true });
  const [rows, setRows] = useState<Row[]>([emptyRow()]);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    if (bom?.lines.length) {
      setRows(
        bom.lines.map((line) => ({
          rawMaterialId: line.rawMaterialId != null ? String(line.rawMaterialId) : '',
          qty: String(line.qty),
          unitId: String(line.unitId),
          wastePercent: line.wastePercent != null ? String(line.wastePercent) : '',
        })),
      );
    } else {
      setRows([emptyRow()]);
    }
  }, [open, bom]);

  const materialOptions = (materials.data ?? []).filter((m) => m.isActive && m.code.toLowerCase() !== productCode.toLowerCase());

  const parsed: BomLineInput[] = rows.map((row) => ({
    rawMaterialId: row.rawMaterialId ? Number(row.rawMaterialId) : null,
    componentProductId: null,
    qty: Number(row.qty),
    unitId: Number(row.unitId),
    wastePercent: row.wastePercent.trim() === '' ? null : Number(row.wastePercent),
  }));

  const rowErrors = rows.map((row, index) => {
    if (!row.rawMaterialId) return 'Chọn nguyên liệu';
    if (!(Number(row.qty) > 0)) return 'Số lượng > 0';
    if (!row.unitId) return 'Chọn ĐVT';
    if (row.wastePercent.trim() !== '') {
      const w = Number(row.wastePercent);
      if (Number.isNaN(w) || w < 0 || w > 100) return '% hao hụt 0–100';
    }
    const rawId = Number(row.rawMaterialId);
    if (rows.findIndex((r) => Number(r.rawMaterialId) === rawId) !== index) return 'Nguyên liệu trùng';
    return null;
  });
  // Cho phép lưu danh sách rỗng (xóa hết định mức).
  const valid = rows.length === 0 || (rows.every((_, i) => !rowErrors[i]) && rows.every((r) => r.rawMaterialId));

  const save = useAction(
    () => productsApi.replaceBom(productId, { lines: rows.length === 0 ? [] : parsed }),
    { success: 'Đã lưu định mức NVL', onDone: onClose },
  );

  const setRow = (index: number, patch: Partial<Row>) => {
    setRows((list) => list.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  const onPickMaterial = (index: number, rawMaterialId: string) => {
    const material = materialOptions.find((m) => String(m.id) === rawMaterialId);
    setRow(index, {
      rawMaterialId,
      unitId: material?.unitId != null ? String(material.unitId) : rows[index]?.unitId ?? '',
    });
  };

  return (
    <Modal
      open={open}
      title="Định mức nguyên vật liệu (BOM)"
      onClose={onClose}
      width="lg"
      onSubmit={() => {
        setTouched(true);
        if (valid) save.mutate();
      }}
      footer={
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={save.isPending}>
            Lưu
          </Button>
        </>
      }
    >
      <p className="mb-3 text-small text-ink-muted">
        Số lượng NVL cho <strong>1 đơn vị thành phẩm</strong> (vd. 1 kg chả). Không chọn chính sản phẩm đang sửa làm nguyên liệu.
      </p>
      <div className="space-y-3">
        {rows.map((row, index) => (
          <div key={index} className="grid gap-2 rounded border border-line p-3 sm:grid-cols-[1.4fr_0.7fr_0.9fr_0.7fr_auto]">
            <Field label="Nguyên liệu" required error={touched ? rowErrors[index] ?? undefined : undefined}>
              <Select
                aria-label={`Nguyên liệu dòng ${index + 1}`}
                value={row.rawMaterialId}
                onChange={(e) => onPickMaterial(index, e.target.value)}
              >
                <option value="">Chọn...</option>
                {materialOptions.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} ({m.code})
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="Số lượng" required>
              <Input inputMode="decimal" value={row.qty} onChange={(e) => setRow(index, { qty: e.target.value })} />
            </Field>
            <Field label="ĐVT" required>
              <Select aria-label={`ĐVT dòng ${index + 1}`} value={row.unitId} onChange={(e) => setRow(index, { unitId: e.target.value })}>
                <option value="">Chọn...</option>
                {(units.data ?? []).map((u) => (
                  <option key={u.id} value={u.id}>
                    {u.name} ({u.code})
                  </option>
                ))}
              </Select>
            </Field>
            <Field label="% hao hụt" hint="Để trống = 0">
              <Input inputMode="decimal" value={row.wastePercent} onChange={(e) => setRow(index, { wastePercent: e.target.value })} />
            </Field>
            <div className="flex items-end justify-end pb-1">
              <IconButton
                label="Xóa dòng"
                onClick={() => setRows((list) => (list.length <= 1 ? [emptyRow()] : list.filter((_, i) => i !== index)))}
              >
                <Trash2 size={14} />
              </IconButton>
            </div>
          </div>
        ))}
      </div>
      <div className="mt-3 flex gap-2">
        <Button icon={<Plus size={14} />} onClick={() => setRows((list) => [...list, emptyRow()])}>
          Thêm dòng
        </Button>
        {rows.length > 0 ? (
          <Button
            onClick={() => {
              setRows([]);
            }}
          >
            Xóa hết
          </Button>
        ) : null}
      </div>
    </Modal>
  );
}
