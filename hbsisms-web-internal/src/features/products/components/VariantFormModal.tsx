import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import { formatThousands, money, parseThousands } from '@/shared/lib/format';
import { Button, Checkbox, Field, IconButton, Input, Modal, Select } from '@/shared/ui';
import { productsApi } from '../api';
import type { VariantInput, VariantWithTiers } from '../types';

interface TierRow { minQtyKhay: string; unitPrice: string }

type PackSize = '500' | '1000';

const PACK_PRESETS: Record<PackSize, { label: string; weightG: number }> = {
  '500': { label: 'Khay 0,5 kg', weightG: 500 },
  '1000': { label: 'Khay 1 kg', weightG: 1000 },
};

function detectPackSize(packLabel: string, weightG: number | null | undefined): PackSize {
  if (weightG === 1000) return '1000';
  if (weightG === 500) return '500';
  const n = packLabel.toLowerCase();
  if (/(^|[^0-9])1\s*kg/.test(n) || n.includes('1kg')) return '1000';
  return '500';
}

export function VariantFormModal({
  open,
  productId,
  productActive = true,
  variant,
  onClose,
}: {
  open: boolean;
  productId: ID;
  /** false khi sản phẩm đang ngừng kinh doanh — khóa checkbox quy cách. */
  productActive?: boolean;
  variant?: VariantWithTiers;
  onClose: () => void;
}) {
  const [sku, setSku] = useState('');
  const [packSize, setPackSize] = useState<PackSize>('500');
  const [barcode, setBarcode] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [tiers, setTiers] = useState<TierRow[]>([]);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setSku(variant?.sku ?? '');
    setPackSize(detectPackSize(variant?.packLabel ?? '', variant?.unitWeightG));
    setBarcode(variant?.barcode ?? '');
    setIsActive(variant?.isActive ?? true);
    setTiers(
      variant?.tiers.map((t) => ({
        minQtyKhay: String(t.minQtyKhay),
        unitPrice: parseThousands(String(t.unitPrice)),
      })) ?? [],
    );
  }, [open, variant]);

  const preset = PACK_PRESETS[packSize];
  const tiersValid = tiers.every((t) => Number(t.minQtyKhay) > 0 && Number(t.unitPrice) > 0);
  const valid = sku.trim() && tiersValid;
  const displayListPrice = variant ? Number(variant.listPrice) : 0;

  const save = useAction(
    () => {
      const body: VariantInput = {
        sku: sku.trim(),
        packLabel: preset.label,
        unitWeightG: preset.weightG,
        listPrice: displayListPrice,
        packPerCase: 1,
        barcode: barcode.trim() || null,
        isActive,
        tiers: tiers.map((t) => ({ minQtyKhay: Number(t.minQtyKhay), unitPrice: Number(t.unitPrice) })),
      };
      return variant ? productsApi.updateVariant(variant.id, body) : productsApi.addVariant(productId, body);
    },
    { success: 'Đã lưu quy cách', onDone: onClose },
  );

  return (
    <Modal
      open={open}
      title={variant ? `Sửa quy cách ${variant.sku}` : 'Thêm quy cách'}
      onClose={onClose}
      width="lg"
      onSubmit={() => {
        setTouched(true);
        if (valid) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="SKU" required error={touched && !sku.trim() ? 'Bắt buộc' : undefined}>
          <Input value={sku} onChange={(e) => setSku(e.target.value)} autoComplete="off" placeholder="VD: SP-CHA-CA-05" />
        </Field>
        <Field label="Loại khay" required tip="Chỉ hai quy cách chuẩn; khối lượng dùng cho BOM và gợi ý giá bán theo WAC sau SX.">
          <Select value={packSize} onChange={(e) => setPackSize(e.target.value as PackSize)}>
            <option value="500">Khay 0,5 kg</option>
            <option value="1000">Khay 1 kg</option>
          </Select>
        </Field>
        <Field label="Mã vạch" className="sm:col-span-2">
          <Input value={barcode} onChange={(e) => setBarcode(e.target.value)} placeholder="Không bắt buộc" autoComplete="off" />
        </Field>
      </div>

      <div className="mt-3 rounded-md border border-line bg-rowalt/50 px-3 py-2.5">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <span className="text-small font-medium text-ink-muted">Giá niêm yết / khay</span>
          <span className={`text-h2 tabular-nums ${displayListPrice > 0 ? 'text-ink' : 'text-ink-faint'}`}>
            {displayListPrice > 0 ? money(displayListPrice) : 'Chưa có'}
          </span>
        </div>
        <p className="mt-1 text-small text-ink-faint">
          Không nhập tay. Giá gợi ý xem ở bước hoàn thành của lệnh SX.
        </p>
      </div>

      <div className="mt-3">
        <Checkbox
          label="Quy cách đang bán"
          checked={isActive}
          disabled={!productActive}
          onChange={(e) => setIsActive(e.target.checked)}
        />
        {!productActive && (
          <p className="mt-1 text-small text-ink-faint">Sản phẩm đang ngừng kinh doanh.</p>
        )}
      </div>
      <div className="mt-4 border-t border-line pt-3">
        <div className="mb-2 flex items-center justify-between">
          <h3 className="text-h2">Bậc số lượng</h3>
          <Button icon={<Plus size={14} />} onClick={() => setTiers([...tiers, { minQtyKhay: '', unitPrice: '' }])}>Thêm bậc</Button>
        </div>
        {tiers.length === 0 && <p className="text-ink-faint">Chưa có bậc giá, luôn bán theo giá niêm yết.</p>}
        <div className="space-y-2">
          {tiers.map((tier, i) => (
            <div key={i} className="flex items-center gap-2">
              <Input aria-label={`Từ số khay, bậc ${i + 1}`} type="number" min={1} placeholder="Từ số khay" value={tier.minQtyKhay} onChange={(e) => setTiers(tiers.map((t, j) => (j === i ? { ...t, minQtyKhay: e.target.value } : t)))} />
              <Input
                aria-label={`Đơn giá, bậc ${i + 1}`}
                type="text"
                inputMode="numeric"
                autoComplete="off"
                placeholder="Đơn giá / khay"
                value={formatThousands(tier.unitPrice)}
                onChange={(e) =>
                  setTiers(tiers.map((t, j) => (j === i ? { ...t, unitPrice: parseThousands(e.target.value) } : t)))
                }
              />
              <IconButton label={`Xóa bậc ${i + 1}`} onClick={() => setTiers(tiers.filter((_, j) => j !== i))}><Trash2 size={14} /></IconButton>
            </div>
          ))}
        </div>
        {touched && !tiersValid && <p className="mt-1 text-small text-red-fg">Mỗi bậc cần số khay và đơn giá lớn hơn 0.</p>}
      </div>
    </Modal>
  );
}
