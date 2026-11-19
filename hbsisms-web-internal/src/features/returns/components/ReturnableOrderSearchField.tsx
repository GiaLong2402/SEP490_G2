import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import { Input } from '@/shared/ui';
import { returnsApi } from '../api';
import type { ReturnableOrder } from '../types';

function labelOf(o: Pick<ReturnableOrder, 'code' | 'customerName'>) {
  return `${o.code} · ${o.customerName ?? 'Khách lẻ'}`;
}

function hintOf(o: ReturnableOrder) {
  const trays = o.lines.reduce((sum, l) => sum + l.maxQty, 0);
  const products = o.lines.length;
  const customer = o.customerName ?? 'Khách lẻ';
  if (products === 1) return `${customer} · còn trả tối đa ${trays} khay`;
  return `${customer} · ${products} mặt hàng, còn trả tối đa ${trays} khay`;
}

interface Props {
  value: number | null;
  onChange: (order: ReturnableOrder | null) => void;
  placeholder?: string;
}

/** Ô tìm đơn đã giao theo mã đơn / tên khách — GET /returns/returnable-orders?q=. */
export function ReturnableOrderSearchField({
  value,
  onChange,
  placeholder = 'Tìm mã đơn hoặc tên khách…',
}: Props) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const [text, setText] = useState('');
  const [debounced, setDebounced] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const picking = useRef(false);
  const selectedRef = useRef<ReturnableOrder | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(text.trim()), 250);
    return () => clearTimeout(t);
  }, [text]);

  const q = debounced;
  const selectedLabel = selectedRef.current ? labelOf(selectedRef.current) : '';
  const searching = open && !(value != null && text === selectedLabel);

  const results = useQuery({
    queryKey: ['returnable-orders', q],
    queryFn: () => returnsApi.returnableOrders(q || undefined),
    enabled: searching,
  });

  const items = results.data ?? [];

  useEffect(() => setActive(0), [items]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    return () => document.removeEventListener('mousedown', onDoc);
  }, [open]);

  // Prefill khi mở từ /returns?orderId=
  useEffect(() => {
    if (value == null) {
      if (!picking.current) {
        selectedRef.current = null;
        setText('');
      }
      return;
    }
    if (selectedRef.current?.id === value) return;
    let cancelled = false;
    returnsApi.returnableOrders().then((list) => {
      if (cancelled) return;
      const found = list.find((o) => o.id === value) ?? null;
      selectedRef.current = found;
      if (found) setText(labelOf(found));
    });
    return () => {
      cancelled = true;
    };
  }, [value]);

  const pick = (o: ReturnableOrder) => {
    picking.current = true;
    selectedRef.current = o;
    setText(labelOf(o));
    setOpen(false);
    onChange(o);
    queueMicrotask(() => {
      picking.current = false;
    });
  };

  const clear = () => {
    selectedRef.current = null;
    setText('');
    setDebounced('');
    setOpen(false);
    onChange(null);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Escape') {
      setOpen(false);
      return;
    }
    if (!open || !items.length) return;
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      setActive((i) => (i + 1) % items.length);
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => (i - 1 + items.length) % items.length);
    } else if (e.key === 'Enter') {
      e.preventDefault();
      const o = items[active];
      if (o) pick(o);
    }
  };

  return (
    <div ref={rootRef} className="relative">
      <div className="relative">
        <Search size={14} className="pointer-events-none absolute left-2.5 top-[10px] text-ink-faint" aria-hidden />
        <Input
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          autoComplete="off"
          className="pl-8 pr-8"
          placeholder={placeholder}
          value={text}
          onChange={(e) => {
            setText(e.target.value);
            if (value != null) {
              selectedRef.current = null;
              onChange(null);
            }
            setOpen(true);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {text && (
          <button
            type="button"
            className="absolute right-2 top-[9px] rounded p-0.5 text-ink-faint hover:bg-rowalt hover:text-ink"
            aria-label="Xóa đơn đã chọn"
            onMouseDown={(e) => e.preventDefault()}
            onClick={clear}
          >
            <X size={14} />
          </button>
        )}
      </div>
      {/* In-flow (không absolute) để modal overflow không cắt list và không đè “Lý do”. */}
      {open && (
        <ul
          id={listId}
          role="listbox"
          className="mt-1.5 max-h-52 w-full overflow-y-auto rounded-sm border border-line bg-card py-1 shadow-sm"
        >
          {results.isFetching && <li className="px-3 py-2 text-small text-ink-faint">Đang tìm…</li>}
          {!results.isFetching && items.length === 0 && (
            <li className="px-3 py-2 text-small text-ink-faint">
              {q ? 'Không có đơn khớp' : 'Gõ mã đơn hoặc tên khách để lọc'}
            </li>
          )}
          {items.map((o, i) => (
            <li key={o.id} role="option" aria-selected={i === active}>
              <button
                type="button"
                className={`flex w-full flex-col gap-0.5 px-3 py-2.5 text-left hover:bg-rowalt ${i === active ? 'bg-rowalt' : ''}`}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(o)}
              >
                <span className="num text-body font-medium text-ink">{o.code}</span>
                <span className="text-small text-ink-muted">{hintOf(o)}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
