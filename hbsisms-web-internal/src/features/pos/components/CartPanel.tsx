import { Minus, Plus, ShoppingCart, Trash2 } from 'lucide-react';
import type { CustomerListItem } from '@/features/customers';
import type { PricingResult } from '@/features/pricing';
import { priceBasisMeta, sellUnitLabels, type SellUnit } from '@/shared/domain/enums';
import { money, num } from '@/shared/lib/format';
import { Alert, Button, Field, IconButton, Input, Meter, Select, StatusChip, enumOptions } from '@/shared/ui';
import { posPaymentLabels, type CatalogItem, type PosPaymentMethod, type PosTab } from '../types';

interface Props {
  tab: PosTab;
  catalog: CatalogItem[];
  customers: CustomerListItem[];
  pricing: PricingResult | undefined;
  pricingError: Error | null;
  blockReason: string | null;
  busy: boolean;
  onChange: (patch: Partial<PosTab>) => void;
  onCheckout: () => void;
}

/** Mệnh giá gợi ý: đúng số tiền rồi làm tròn lên theo các tờ thường gặp. */
function quickAmounts(total: number) {
  const rounded = [10_000, 50_000, 100_000, 500_000].map((step) => Math.ceil(total / step) * step);
  return [...new Set([total, ...rounded])].filter((v) => v > 0).slice(0, 4);
}

export function CartPanel({ tab, catalog, customers, pricing, pricingError, blockReason, busy, onChange, onCheckout }: Props) {
  const customer = customers.find((c) => c.id === tab.customerId);
  const isB2b = customer?.customerType === 'b2b';
  const methods: PosPaymentMethod[] = isB2b ? ['cash', 'bank_transfer', 'credit_net'] : ['cash', 'bank_transfer'];
  const total = pricing?.total ?? 0;
  const tendered = tab.tendered === '' ? null : Number(tab.tendered);
  const change = tab.paymentMethod === 'cash' && tendered !== null ? tendered - total : null;

  const setLine = (variantId: number, patch: Partial<PosTab['lines'][number]>) => onChange({ lines: tab.lines.map((l) => (l.variantId === variantId ? { ...l, ...patch } : l)) });
  const removeLine = (variantId: number) => onChange({ lines: tab.lines.filter((l) => l.variantId !== variantId) });

  return (
    <aside aria-label="Đơn đang bán" className="flex min-h-0 flex-col overflow-y-auto rounded-md border border-line bg-card shadow-card">
      <div className="border-b border-line p-3">
        <Field label="Khách hàng">
          <Select
            value={tab.customerId ?? ''}
            onChange={(e) => {
              const id = e.target.value ? Number(e.target.value) : null;
              const next = customers.find((c) => c.id === id);
              onChange({ customerId: id, ...(tab.paymentMethod === 'credit_net' && next?.customerType !== 'b2b' ? { paymentMethod: 'cash' as const } : {}) });
            }}
          >
            <option value="">Khách lẻ</option>
            {customers.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.phone ?? c.code})</option>)}
          </Select>
        </Field>
        {isB2b && customer && (
          <div className="mt-2">
            <div className="mb-1 flex justify-between text-small text-ink-muted"><span>Nợ / hạn mức</span><span className="num">{money(customer.currentDebt)} / {money(customer.creditLimit)}</span></div>
            <Meter value={customer.currentDebt} max={customer.creditLimit} label="Tỷ lệ sử dụng hạn mức" />
          </div>
        )}
      </div>

      <div className="min-h-[120px] flex-1 overflow-y-auto">
        {tab.lines.length === 0 && (
          <div className="flex h-full min-h-[120px] flex-col items-center justify-center text-ink-muted">
            <ShoppingCart size={22} className="mb-2 text-ink-faint" aria-hidden />
            Chọn sản phẩm hoặc quét mã để bắt đầu
          </div>
        )}
        <ul>
          {tab.lines.map((line) => {
            const item = catalog.find((i) => i.variantId === line.variantId);
            const qty = Number(line.qty);
            const valid = Number.isInteger(qty) && qty > 0;
            const row = valid ? pricing?.lines.find((p) => p.variantId === line.variantId && p.unit === line.unit && p.qty === qty) : undefined;
            const qtyKhay = valid && item ? (line.unit === 'case' ? qty * item.packPerCase : qty) : null;
            const short = item && qtyKhay !== null && qtyKhay > item.available;
            return (
              <li key={line.variantId} className="border-b border-line px-3 py-2">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="font-medium">{item?.productName ?? 'Sản phẩm không còn bán'}</div>
                    <div className="text-small text-ink-muted">{item ? `${item.packLabel} · ${money(row?.unitPrice ?? item.listPrice)}` : 'Hãy xóa dòng này'}</div>
                  </div>
                  <IconButton label={`Xóa ${item?.sku ?? 'dòng'}`} className="!h-7 !w-7 shrink-0" onClick={() => removeLine(line.variantId)}><Trash2 size={14} /></IconButton>
                </div>
                <div className="mt-1.5 flex items-center gap-1.5">
                  <IconButton label={`Giảm ${item?.sku ?? ''}`} className="border border-field" disabled={qty <= 1} onClick={() => setLine(line.variantId, { qty: String(qty - 1) })}><Minus size={14} /></IconButton>
                  <Input aria-label={`Số lượng ${item?.sku ?? ''}`} aria-invalid={!valid || undefined} type="number" min={1} className={`!h-8 !w-14 !px-1 text-center ${valid ? '' : 'border-red-fg'}`} value={line.qty} onChange={(e) => setLine(line.variantId, { qty: e.target.value })} />
                  <IconButton label={`Tăng ${item?.sku ?? ''}`} className="border border-field" onClick={() => setLine(line.variantId, { qty: String((valid ? qty : 0) + 1) })}><Plus size={14} /></IconButton>
                  <Select aria-label={`Đơn vị ${item?.sku ?? ''}`} className="!h-8 !w-[82px] !pl-2 !pr-1" value={line.unit} onChange={(e) => setLine(line.variantId, { unit: e.target.value as SellUnit })}>{enumOptions(sellUnitLabels)}</Select>
                  <span className="num ml-auto font-medium">{row ? money(row.lineTotal) : '—'}</span>
                </div>
                <div className="mt-1 flex items-center justify-between gap-2 text-small empty:hidden">
                  {row && row.priceBasis !== 'list' && <StatusChip meta={priceBasisMeta} value={row.priceBasis} />}
                  {line.unit === 'case' && qtyKhay !== null && <span className="text-ink-faint">{num(qtyKhay)} khay</span>}
                  {short && <span className="ml-auto font-semibold text-red-fg">Chỉ còn {num(item.available)} khay</span>}
                </div>
              </li>
            );
          })}
        </ul>
      </div>

      <div className="space-y-2.5 border-t border-line p-3">
        <dl className="space-y-1">
          <div className="flex items-center justify-between"><dt className="text-ink-muted">Tạm tính</dt><dd className="num">{money(pricing?.subtotal ?? 0)}</dd></div>
          <div className="flex items-center justify-between gap-3">
            <dt><label htmlFor="pos-discount" className="text-ink-muted">Giảm giá (₫)</label></dt>
            <dd><Input id="pos-discount" type="number" min={0} className="!h-7 !w-[120px] text-right" placeholder="0" value={tab.discount} onChange={(e) => onChange({ discount: e.target.value })} /></dd>
          </div>
          <div className="flex items-center justify-between pt-1 text-h1"><dt>Tổng cộng</dt><dd className="tabular-nums">{money(total)}</dd></div>
        </dl>

        <div role="radiogroup" aria-label="Phương thức thanh toán" className="flex gap-1">
          {methods.map((method) => (
            <button key={method} type="button" role="radio" aria-checked={tab.paymentMethod === method} onClick={() => onChange({ paymentMethod: method })} className={`h-8 flex-1 whitespace-nowrap rounded-md border px-1 text-small ${tab.paymentMethod === method ? 'border-primary bg-primary-soft font-semibold text-primary' : 'border-field text-ink-muted hover:border-primary'}`}>
              {posPaymentLabels[method]}
            </button>
          ))}
        </div>

        {tab.paymentMethod === 'cash' && (
          <div>
            <div className="flex items-center justify-between gap-3">
              <label htmlFor="pos-tendered" className="text-ink-muted">Khách đưa (₫)</label>
              <Input id="pos-tendered" type="number" min={0} className="!h-8 !w-[140px] text-right" placeholder={total ? `Đủ tiền: ${num(total)}` : ''} value={tab.tendered} onChange={(e) => onChange({ tendered: e.target.value })} />
            </div>
            {total > 0 && (
              <div className="mt-1.5 flex flex-wrap gap-1">
                {quickAmounts(total).map((amount) => (
                  <button key={amount} type="button" onClick={() => onChange({ tendered: String(amount) })} className="num rounded border border-field px-1.5 py-0.5 text-ink-muted hover:border-primary hover:text-primary">{num(amount)}</button>
                ))}
              </div>
            )}
            {change !== null && <div className={`mt-1.5 flex justify-between font-semibold ${change < 0 ? 'text-red-fg' : 'text-green-fg'}`}><span>{change < 0 ? 'Còn thiếu' : 'Tiền thối'}</span><span className="tabular-nums">{money(Math.abs(change))}</span></div>}
          </div>
        )}
        {tab.paymentMethod === 'bank_transfer' && (
          <div className="flex items-center justify-between gap-3">
            <label htmlFor="pos-reference" className="whitespace-nowrap text-ink-muted">Mã giao dịch</label>
            <Input id="pos-reference" className="!h-8" value={tab.reference} onChange={(e) => onChange({ reference: e.target.value })} />
          </div>
        )}

        {pricing?.isOverLimit && <Alert tone="danger" title={`Vượt hạn mức ${money(pricing.overLimitAmount)}`}>Hãy thu tiền, hoặc tạo đơn thường để xin Chủ cửa hàng duyệt.</Alert>}
        {pricingError && <Alert tone="danger">{pricingError.message}</Alert>}

        <Button variant="primary" className="!h-11 w-full !text-h2" loading={busy} disabled={!!blockReason} title={blockReason ?? undefined} onClick={onCheckout}>Thanh toán{total > 0 ? ` ${money(total)}` : ''} (F9)</Button>
        {blockReason && tab.lines.length > 0 && !pricing?.isOverLimit && <p className="text-center text-small text-red-fg">{blockReason}</p>}
      </div>
    </aside>
  );
}
