import { Plus, Trash2 } from 'lucide-react';
import type { VariantOption } from '@/features/products';
import { priceBasisMeta, sellUnitLabels, type SellUnit } from '@/shared/domain/enums';
import { money, num } from '@/shared/lib/format';
import { Button, IconButton, Input, Select, StatusChip, enumOptions } from '@/shared/ui';
import type { PricedLine, PricingLineInput } from '../types';

export interface EditableLine {
  key: number;
  variantId: number;
  unit: SellUnit;
  qty: string;
  manualPrice: string;
}

let lineKey = 1;
export const newLine = (variantId = 0, qty = '1'): EditableLine => ({ key: lineKey++, variantId, unit: 'pack', qty, manualPrice: '' });

/** Dòng đã đủ sản phẩm và số lượng hợp lệ, có thể gửi đi tính giá. */
export const isCompleteLine = (l: EditableLine) => l.variantId > 0 && Number.isInteger(Number(l.qty)) && Number(l.qty) > 0;

export const toPricingLines = (lines: EditableLine[]): PricingLineInput[] =>
  lines.map((l) => ({ variantId: l.variantId, unit: l.unit, qty: Number(l.qty), manualPrice: l.manualPrice === '' ? null : Number(l.manualPrice) }));

interface Props {
  lines: EditableLine[];
  onChange: (lines: EditableLine[]) => void;
  variants: VariantOption[];
  priced?: PricedLine[];
  showStock?: boolean;
}

/** Bảng dòng hàng dùng chung cho đơn và báo giá; giá luôn do máy chủ tính, trừ khi nhập tay. */
export function LineItemsEditor({ lines, onChange, variants, priced, showStock = true }: Props) {
  const update = (key: number, patch: Partial<EditableLine>) => onChange(lines.map((l) => (l.key === key ? { ...l, ...patch } : l)));
  const complete = lines.filter(isCompleteLine);
  return (
    <div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[920px] text-small">
          <thead>
            <tr className="bg-thead text-left text-micro font-semibold uppercase tracking-wide text-ink-muted">
              <th className="h-[34px] min-w-[240px] px-3">Sản phẩm · quy cách</th>
              <th className="w-[104px] px-2">Đơn vị</th>
              <th className="w-[76px] px-2">Số lượng</th>
              <th className="w-[56px] px-2 text-right">Khay</th>
              {showStock && <th className="w-[72px] px-2 text-right">Khả dụng</th>}
              <th className="w-[112px] px-2 text-right">Đơn giá</th>
              <th className="w-[104px] px-2">Nguồn giá</th>
              <th className="w-[112px] px-2 text-right">Thành tiền</th>
              <th className="w-10" aria-label="Xóa" />
            </tr>
          </thead>
          <tbody>
            {lines.map((line, index) => {
              // Bỏ qua kết quả cũ còn giữ lại trong lúc chờ máy chủ tính giá mới.
              const candidate = priced?.[complete.indexOf(line)];
              const row = candidate && candidate.variantId === line.variantId && candidate.unit === line.unit && candidate.qty === Number(line.qty) ? candidate : undefined;
              const short = showStock && row && row.qtyKhay > row.available;
              const qtyInvalid = line.qty !== '' && (!Number.isInteger(Number(line.qty)) || Number(line.qty) <= 0);
              return (
                <tr key={line.key} className="border-t border-line align-middle">
                  <td className="px-3 py-1.5">
                    <Select aria-label={`Sản phẩm dòng ${index + 1}`} value={line.variantId || ''} onChange={(e) => update(line.key, { variantId: Number(e.target.value), manualPrice: '' })}>
                      <option value="">Chọn sản phẩm...</option>
                      {variants.map((v) => {
                        const stock =
                          typeof v.available === 'number'
                            ? v.available > 0
                              ? ` — Còn ${num(v.available)}`
                              : ' — Hết'
                            : '';
                        return (
                          <option key={v.id} value={v.id}>
                            {v.productName} · {v.packLabel}{stock}
                          </option>
                        );
                      })}
                    </Select>
                  </td>
                  <td className="px-2">
                    <Select aria-label={`Đơn vị dòng ${index + 1}`} value={line.unit} onChange={(e) => update(line.key, { unit: e.target.value as SellUnit })}>
                      {enumOptions(sellUnitLabels)}
                    </Select>
                  </td>
                  <td className="px-2">
                    <Input aria-label={`Số lượng dòng ${index + 1}`} aria-invalid={qtyInvalid || undefined} type="number" min={1} step={1} className={`text-right ${qtyInvalid ? 'border-red-fg' : ''}`} value={line.qty} onChange={(e) => update(line.key, { qty: e.target.value })} />
                  </td>
                  <td className="num px-2 text-right">{row ? num(row.qtyKhay) : '—'}</td>
                  {showStock && <td className={`num px-2 text-right ${short ? 'font-semibold text-red-fg' : ''}`}>{row ? num(row.available) : '—'}</td>}
                  <td className="px-2">
                    <Input
                      aria-label={`Đơn giá dòng ${index + 1}, để trống để dùng giá hệ thống`}
                      type="number"
                      min={0}
                      className="text-right"
                      placeholder={row ? String(row.unitPrice) : ''}
                      value={line.manualPrice}
                      onChange={(e) => update(line.key, { manualPrice: e.target.value })}
                    />
                  </td>
                  <td className="px-2">{row ? <StatusChip meta={priceBasisMeta} value={row.priceBasis} /> : '—'}</td>
                  <td className="num px-2 text-right font-medium">{row ? money(row.lineTotal) : '—'}</td>
                  <td className="pr-1.5">
                    <IconButton label={`Xóa dòng ${index + 1}`} disabled={lines.length === 1} onClick={() => onChange(lines.filter((l) => l.key !== line.key))}>
                      <Trash2 size={14} />
                    </IconButton>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      <div className="border-t border-line p-3">
        <Button icon={<Plus size={14} />} onClick={() => onChange([...lines, newLine()])}>
          Thêm dòng
        </Button>
      </div>
    </div>
  );
}
