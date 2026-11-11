import { useEffect, useId, useRef, useState, type InputHTMLAttributes, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import { Input } from '@/shared/ui';
import { customersApi } from '../api';
import { useCustomer } from '../hooks';
import type { CustomerListItem } from '../types';

type FieldAria = Pick<InputHTMLAttributes<HTMLInputElement>, 'id' | 'aria-invalid' | 'aria-describedby'>;

interface CustomerSearchFieldProps extends FieldAria {
  value: number | null;
  onChange: (customerId: number | null) => void;
  placeholder?: string;
}

function labelOf(c: Pick<CustomerListItem, 'name' | 'code' | 'phone'>) {
  const parts = [c.name];
  if (c.phone) parts.push(c.phone);
  if (c.code) parts.push(c.code);
  return parts.join(' · ');
}

/** Ô tìm khách theo SĐT / tên / mã — gọi GET /customers?q=. */
export function CustomerSearchField({
  value,
  onChange,
  id,
  'aria-invalid': ariaInvalid,
  'aria-describedby': ariaDescribedBy,
  placeholder = 'Tìm theo SĐT, tên hoặc mã KH…',
}: CustomerSearchFieldProps) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = useCustomer(value).data;
  const [text, setText] = useState('');
  const [debounced, setDebounced] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const picking = useRef(false);

  useEffect(() => {
    if (picking.current) {
      picking.current = false;
      return;
    }
    // Chỉ đồng bộ nhãn khi đã chọn khách (sửa đơn / pick). Đừng xóa chữ đang gõ khi value → null.
    if (value != null && selected) setText(labelOf(selected));
  }, [value, selected]);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(text.trim()), 300);
    return () => clearTimeout(t);
  }, [text]);

  const q = debounced;
  const searching = open && q.length >= 2 && !(selected && text === labelOf(selected));

  const results = useQuery({
    queryKey: ['customer-search', q],
    queryFn: ({ signal }) => customersApi.list({ q, isActive: true, pageSize: 20 }, signal),
    enabled: searching,
    select: (page) => page.items,
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

  const pick = (c: CustomerListItem) => {
    picking.current = true;
    setText(labelOf(c));
    setOpen(false);
    onChange(c.id);
  };

  const clear = () => {
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
      const c = items[active];
      if (c) pick(c);
    }
  };

  return (
    <div ref={rootRef} className="relative">
      <div className="relative">
        <Search size={14} className="pointer-events-none absolute left-2.5 top-[10px] text-ink-faint" aria-hidden />
        <Input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-invalid={ariaInvalid}
          aria-describedby={ariaDescribedBy}
          autoComplete="off"
          className="pl-8 pr-8"
          placeholder={placeholder}
          value={text}
          onChange={(e) => {
            const next = e.target.value;
            setText(next);
            setOpen(true);
            if (value != null) onChange(null);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {(text || value != null) && (
          <button
            type="button"
            className="absolute right-2 top-[8px] rounded p-0.5 text-ink-faint hover:bg-rowalt hover:text-ink"
            aria-label="Xóa khách đã chọn"
            onMouseDown={(e) => e.preventDefault()}
            onClick={clear}
          >
            <X size={14} />
          </button>
        )}
      </div>
      {open && (
        <ul
          id={listId}
          role="listbox"
          className="absolute z-20 mt-1 max-h-60 w-full overflow-auto rounded-sm border border-line bg-card py-1 shadow-md"
        >
          {q.length < 2 && (
            <li className="px-3 py-2 text-small text-ink-faint">Gõ ít nhất 2 ký tự (SĐT, tên hoặc mã)…</li>
          )}
          {searching && results.isFetching && (
            <li className="px-3 py-2 text-small text-ink-faint">Đang tìm…</li>
          )}
          {searching && !results.isFetching && items.length === 0 && (
            <li className="px-3 py-2 text-small text-ink-faint">Không tìm thấy khách phù hợp</li>
          )}
          {items.map((c, i) => (
            <li key={c.id} role="option" aria-selected={i === active}>
              <button
                type="button"
                className={`flex w-full flex-col gap-0.5 px-3 py-2 text-left hover:bg-rowalt ${i === active ? 'bg-rowalt' : ''}`}
                onMouseEnter={() => setActive(i)}
                onMouseDown={(e) => e.preventDefault()}
                onClick={() => pick(c)}
              >
                <span className="text-body font-medium text-ink">{c.name}</span>
                <span className="text-small text-ink-muted">
                  {[c.phone, c.code].filter(Boolean).join(' · ') || '—'}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
