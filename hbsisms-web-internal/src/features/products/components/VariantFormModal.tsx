import { useEffect, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import type { ID } from '@/shared/domain/entities';
import { formatThousands, parseThousands } from '@/shared/lib/format';
import { Button, Checkbox, Field, IconButton, Input, Modal } from '@/shared/ui';
import { productsApi } from '../api';
import type { VariantInput, VariantWithTiers } from '../types';

interface TierRow { minQtyKhay: string; unitPrice: string }

export function VariantFormModal({ open, productId, variant, onClose }: { open: boolean; productId: ID; variant?: VariantWithTiers; onClose: () => void }) {
  const [sku, setSku] = useState('');
  const [packLabel, setPackLabel] = useState('');
  const [weight, setWeight] = useState('');
  const [listPrice, setListPrice] = useState('');
  const [packPerCase, setPackPerCase] = useState('1');
  const [barcode, setBarcode] = useState('');
  const [isActive, setIsActive] = useState(true);
  const [tiers, setTiers] = useState<TierRow[]>([]);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setSku(variant?.sku ?? '');
    setPackLabel(variant?.packLabel ?? '');
    setWeight(variant?.unitWeightG ? String(variant.unitWeightG) : '');
    setListPrice(variant ? parseThousands(String(variant.listPrice)) : '');
    setPackPerCase(String(variant?.packPerCase ?? 1));
    setBarcode(variant?.barcode ?? '');
    setIsActive(variant?.isActive ?? true);
    setTiers(
      variant?.tiers.map((t) => ({
        minQtyKhay: String(t.minQtyKhay),
        unitPrice: parseThousands(String(t.unitPrice)),
      })) ?? [],
    );
  }, [open, variant]);

  const tiersValid = tiers.every((t) => Number(t.minQtyKhay) > 0 && Number(t.unitPrice) > 0);
  const valid = sku.trim() && packLabel.trim() && Number(listPrice) > 0 && Number(packPerCase) >= 1 && tiersValid;

  const save = useAction(
    () => {
      const body: VariantInput = {
        sku: sku.trim(), packLabel: packLabel.trim(), unitWeightG: weight ? Number(weight) : null, listPrice: Number(listPrice), packPerCase: Number(packPerCase),
        barcode: barcode.trim() || null, isActive, tiers: tiers.map((t) => ({ minQtyKhay: Number(t.minQtyKhay), unitPrice: Number(t.unitPrice) })),
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
      <div className="grid gap-3 sm:grid-cols-3">
        <Field label="SKU" required error={touched && !sku.trim() ? 'Bắt buộc' : undefined}><Input value={sku} onChange={(e) => setSku(e.target.value)} /></Field>
        <Field label="Quy cách" required error={touched && !packLabel.trim() ? 'Bắt buộc' : undefined}><Input placeholder="Khay 0,5kg" value={packLabel} onChange={(e) => setPackLabel(e.target.value)} /></Field>
        <Field label="Khối lượng (g)"><Input type="number" min={0} value={weight} onChange={(e) => setWeight(e.target.value)} /></Field>
        <Field label="Giá niêm yết / khay" required error={touched && !(Number(listPrice) > 0) ? 'Phải lớn hơn 0' : undefined}>
          <Input
            type="text"
            inputMode="numeric"
            autoComplete="off"
            value={formatThousands(listPrice)}
            onChange={(e) => setListPrice(parseThousands(e.target.value))}
          />
        </Field>
        <Field label="Số khay / thùng" required error={touched && !(Number(packPerCase) >= 1) ? 'Từ 1 trở lên' : undefined}><Input type="number" min={1} value={packPerCase} onChange={(e) => setPackPerCase(e.target.value)} /></Field>
        <Field label="Mã vạch"><Input value={barcode} onChange={(e) => setBarcode(e.target.value)} /></Field>
      </div>
      <div className="mt-3"><Checkbox label="Đang kinh doanh" checked={isActive} onChange={(e) => setIsActive(e.target.checked)} /></div>
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
