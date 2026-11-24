import { useEffect, useId, useMemo, useRef, useState } from 'react';
import { Check, ChevronDown, Package, Search } from 'lucide-react';
import { Link } from 'react-router-dom';
import { foldText } from '@/shared/lib/format';
import type { BomProduct } from '../hooks';

interface Props {
  value: string;
  onChange: (id: string) => void;
  items: BomProduct[];
  loading: boolean;
  /** Không xem được định mức (thiếu quyền) → đang hiện mọi sản phẩm. */
  forbidden: boolean;
  disabled?: boolean;
  invalid?: boolean;
  /** Dòng phụ dưới tên khi đã chọn (ví dụ "đã có định mức nguyên liệu"). */
  selectedSub?: string;
  /** Tên hiển thị khi danh sách chưa tải xong (lệnh đã lưu). */
  fallbackName?: string;
  fallbackCode?: string;
}

const subOf = (p: BomProduct) => (p.bomLines === null ? p.code : `${p.code} · ${p.bomLines} nguyên liệu trong định mức`);

/** Ô chọn thành phẩm dạng thẻ + bảng chọn có tìm kiếm; chỉ liệt kê sản phẩm đã có định mức (BOM). */
export function ProductPicker({ value, onChange, items, loading, forbidden, disabled, invalid, selectedSub, fallbackName, fallbackCode }: Props) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const [open, setOpen] = useState(false);
  const [q, setQ] = useState('');
  const [active, setActive] = useState(0);
  const selected = items.find((p) => String(p.id) === value);
  const filtered = useMemo(() => {
    const k = foldText(q.trim());
    return k ? items.filter((p) => foldText(`${p.name} ${p.code}`).includes(k)) : items;
  }, [items, q]);

  useEffect(() => {
    if (!open) return;
    setQ('');
    setActive(Math.max(0, items.findIndex((p) => String(p.id) === value)));
    const t = setTimeout(() => searchRef.current?.focus(), 0);
    const onDown = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => {
      clearTimeout(t);
      document.removeEventListener('mousedown', onDown);
    };
  }, [open]); // eslint-disable-line react-hooks/exhaustive-deps

  const pick = (p: BomProduct) => {
    onChange(String(p.id));
    setOpen(false);
  };
  const onKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Escape') {
      e.preventDefault();
      setOpen(false);
    } else if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => Math.min(filtered.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter') {
      e.preventDefault();
      if (filtered[active]) pick(filtered[active]);
    }
  };

  const name = selected?.name ?? fallbackName;
  const code = selected?.code ?? fallbackCode;

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        disabled={disabled}
        aria-haspopup="listbox"
        aria-expanded={open}
        aria-controls={open ? listId : undefined}
        aria-invalid={invalid || undefined}
        onClick={() => setOpen((o) => !o)}
        onKeyDown={(e) => {
          if (!open && (e.key === 'ArrowDown' || e.key === 'Enter')) {
            e.preventDefault();
            setOpen(true);
          }
        }}
        className={`flex w-full items-center gap-3 rounded-md border bg-card px-3 py-2 text-left transition disabled:cursor-default ${
          invalid ? 'border-red-fg' : open ? 'border-primary ring-2 ring-primary/20' : 'border-line hover:border-primary/50'
        }`}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary"><Package size={18} aria-hidden /></span>
        <span className="min-w-0 flex-1">
          <span className={`block truncate font-semibold ${name ? 'text-ink' : 'text-ink-faint'}`}>{name ?? (loading ? 'Đang tải...' : 'Chọn thành phẩm...')}</span>
          <span className="block truncate text-small text-ink-faint">{name ? `${code ?? ''}${selectedSub ? ` · ${selectedSub}` : ''}` : 'Chỉ sản phẩm đã có định mức nguyên liệu mới lập lệnh được'}</span>
        </span>
        {!disabled && <ChevronDown size={16} className={`shrink-0 text-ink-faint transition ${open ? 'rotate-180' : ''}`} aria-hidden />}
      </button>

      {open && (
        <div className="absolute inset-x-0 top-full z-30 mt-1.5 overflow-hidden rounded-lg border border-line bg-card shadow-pop" onKeyDown={onKey}>
          <div className="flex items-center gap-2 border-b border-line px-3 py-2">
            <Search size={15} className="shrink-0 text-ink-faint" aria-hidden />
            <input
              ref={searchRef}
              value={q}
              onChange={(e) => {
                setQ(e.target.value);
                setActive(0);
              }}
              placeholder="Tìm tên hoặc mã sản phẩm..."
              aria-label="Tìm thành phẩm"
              aria-controls={listId}
              aria-activedescendant={filtered[active] ? `${listId}-${filtered[active].id}` : undefined}
              className="h-8 flex-1 border-0 bg-transparent text-body text-ink outline-none"
            />
          </div>
          <ul id={listId} role="listbox" aria-label="Thành phẩm" className="max-h-72 overflow-y-auto py-1">
            {loading && <li className="px-3 py-4 text-center text-small text-ink-muted">Đang tải danh sách...</li>}
            {!loading && filtered.length === 0 && (
              <li className="px-3 py-4 text-center text-small text-ink-muted">
                {items.length === 0 ? 'Chưa có sản phẩm nào có định mức nguyên liệu.' : 'Không tìm thấy sản phẩm phù hợp.'}
              </li>
            )}
            {!loading &&
              filtered.map((p, i) => {
                const isSel = String(p.id) === value;
                return (
                  <li
                    key={p.id}
                    id={`${listId}-${p.id}`}
                    role="option"
                    aria-selected={isSel}
                    onMouseEnter={() => setActive(i)}
                    onMouseDown={(e) => e.preventDefault()}
                    onClick={() => pick(p)}
                    className={`mx-1 flex cursor-pointer items-center gap-3 rounded-md px-2.5 py-2 ${i === active ? 'bg-rowalt' : ''}`}
                  >
                    <span className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-md ${isSel ? 'bg-primary text-card' : 'bg-primary/10 text-primary'}`}><Package size={16} aria-hidden /></span>
                    <span className="min-w-0 flex-1">
                      <span className={`block truncate ${isSel ? 'font-semibold text-primary' : 'font-medium text-ink'}`}>{p.name}</span>
                      <span className="block truncate text-small text-ink-faint">{subOf(p)}</span>
                    </span>
                    {isSel && <Check size={16} className="shrink-0 text-primary" aria-hidden />}
                  </li>
                );
              })}
          </ul>
          <div className={`flex flex-wrap items-center justify-between gap-2 border-t border-line px-3 py-2 text-small ${forbidden ? 'bg-amber-bg text-amber-fg' : 'text-ink-muted'}`}>
            <span>{forbidden ? 'Bạn không có quyền xem định mức nên đang hiện mọi sản phẩm; sản phẩm chưa có định mức sẽ bị chặn khi lưu.' : 'Chỉ hiện sản phẩm đã có định mức nguyên liệu (BOM).'}</span>
            <Link to="/products" className="font-medium text-primary hover:underline">Thêm định mức ở màn Sản phẩm →</Link>
          </div>
        </div>
      )}
    </div>
  );
}
