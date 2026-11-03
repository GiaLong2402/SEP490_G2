import { useMemo, useState, type FormEvent, type RefObject } from 'react';
import { ScanBarcode } from 'lucide-react';
import { foldText, money, num } from '@/shared/lib/format';
import { ErrorState, useToast } from '@/shared/ui';
import type { CatalogItem } from '../types';

interface Props {
  items: CatalogItem[] | undefined;
  loading: boolean;
  error: unknown;
  onRetry: () => void;
  inCart: Map<number, number>;
  onAdd: (item: CatalogItem) => void;
  searchRef: RefObject<HTMLInputElement>;
}

export function CatalogPanel({ items, loading, error, onRetry, inCart, onAdd, searchRef }: Props) {
  const toast = useToast();
  const [query, setQuery] = useState('');
  const [categoryId, setCategoryId] = useState<number | null>(null);

  const categories = useMemo(() => {
    const map = new Map<number, string>();
    items?.forEach((i) => {
      if (i.categoryId !== null) map.set(i.categoryId, i.categoryName ?? '');
    });
    return [...map.entries()];
  }, [items]);

  const needle = foldText(query.trim());
  const visible = (items ?? []).filter((i) => (categoryId === null || i.categoryId === categoryId) && (!needle || foldText(`${i.productName} ${i.packLabel} ${i.sku} ${i.barcode ?? ''}`).includes(needle)));

  // Máy quét gõ mã rồi nhấn Enter: khớp đúng mã trước, không thì lấy kết quả duy nhất.
  const submit = (event: FormEvent) => {
    event.preventDefault();
    const code = query.trim().toLowerCase();
    if (!code) return;
    const match = items?.find((i) => i.sku.toLowerCase() === code || i.barcode?.toLowerCase() === code) ?? (visible.length === 1 ? visible[0] : undefined);
    if (!match) {
      toast.error(`Không tìm thấy sản phẩm "${query.trim()}"`);
      return;
    }
    onAdd(match);
    setQuery('');
  };

  return (
    <section aria-label="Danh mục sản phẩm" className="flex min-h-0 flex-col rounded-md border border-line bg-card shadow-card">
      <div className="space-y-2 border-b border-line p-3">
        <form onSubmit={submit} className="relative">
          <ScanBarcode size={16} className="pointer-events-none absolute left-2.5 top-[9px] text-ink-faint" aria-hidden />
          <input ref={searchRef} aria-label="Quét mã hoặc tìm sản phẩm (F4)" className="field-control pl-8" placeholder="Quét mã hoặc tìm sản phẩm (F4)" value={query} onChange={(e) => setQuery(e.target.value)} />
        </form>
        {categories.length > 1 && (
          <div className="flex flex-wrap gap-1.5" role="group" aria-label="Lọc theo loại">
            {[[null, 'Tất cả'] as const, ...categories].map(([id, name]) => (
              <button key={id ?? 'all'} type="button" aria-pressed={categoryId === id} onClick={() => setCategoryId(id)} className={`rounded-full border px-2.5 py-1 text-small ${categoryId === id ? 'border-primary bg-primary-soft font-semibold text-primary' : 'border-field text-ink-muted hover:border-primary'}`}>
                {name}
              </button>
            ))}
          </div>
        )}
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto p-3" aria-busy={loading}>
        {!!error && <ErrorState error={error} onRetry={onRetry} />}
        {!error && !loading && visible.length === 0 && <p className="py-10 text-center text-ink-muted">Không có sản phẩm phù hợp</p>}
        <ul className="grid grid-cols-2 gap-2 md:grid-cols-3 2xl:grid-cols-4">
          {loading && Array.from({ length: 6 }).map((_, i) => <li key={i} className="h-[92px] animate-pulse rounded-md bg-rowalt" />)}
          {visible.map((item) => {
            const soldOut = item.available <= 0;
            const count = inCart.get(item.variantId);
            return (
              <li key={item.variantId}>
                <button
                  type="button"
                  disabled={soldOut}
                  onClick={() => onAdd(item)}
                  className={`relative flex h-full min-h-[92px] w-full flex-col rounded-md border p-2.5 text-left transition-colors disabled:cursor-not-allowed disabled:bg-rowalt disabled:opacity-70 ${count ? 'border-primary bg-primary-soft' : 'border-line hover:border-primary'}`}
                >
                  <span className="pr-6 font-semibold leading-tight">{item.productName}</span>
                  <span className="text-small text-ink-muted">{item.packLabel}</span>
                  <span className="mt-auto flex items-end justify-between gap-2 pt-1.5">
                    <span className="num font-medium text-ink">{money(item.listPrice)}</span>
                    <span className={`text-small ${soldOut ? 'font-semibold text-red-fg' : item.available < 15 ? 'text-amber-fg' : 'text-ink-faint'}`}>{soldOut ? 'Hết hàng' : `Còn ${num(item.available)}`}</span>
                  </span>
                  {count && <span className="absolute right-2 top-2 flex h-5 min-w-5 items-center justify-center rounded-full bg-primary px-1 text-micro text-white">{count}</span>}
                </button>
              </li>
            );
          })}
        </ul>
      </div>
    </section>
  );
}
