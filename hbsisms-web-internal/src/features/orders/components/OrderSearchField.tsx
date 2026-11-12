import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Search, X } from 'lucide-react';
import { customersApi, useCustomer, type CustomerListItem } from '@/features/customers';
import { Input } from '@/shared/ui';

function labelOf(c: Pick<CustomerListItem, 'name' | 'code' | 'phone'>) {
  const parts = [c.name];
  if (c.phone) parts.push(c.phone);
  if (c.code) parts.push(c.code);
  return parts.join(' · ');
}

interface OrderSearchFieldProps {
  q: string;
  customerId: number | null;
  onQChange: (q: string) => void;
  onCustomerChange: (customerId: number | null) => void;
  placeholder?: string;
}

/**
 * Một ô: gõ text → lọc đơn theo q (mã đơn / tên / SĐT / mã KH);
 * chọn gợi ý khách → lọc đúng customerId.
 */
export function OrderSearchField({
  q,
  customerId,
  onQChange,
  onCustomerChange,
  placeholder = 'Tìm mã đơn, tên, SĐT, mã khách…',
}: OrderSearchFieldProps) {
  const listId = useId();
  const rootRef = useRef<HTMLDivElement>(null);
  const selected = useCustomer(customerId).data;
  const [text, setText] = useState(q);
  const [debouncedSuggest, setDebouncedSuggest] = useState('');
  const [open, setOpen] = useState(false);
  const [active, setActive] = useState(0);
  const picking = useRef(false);

  // Đồng bộ từ URL / clear filters.
  useEffect(() => {
    if (picking.current) {
      picking.current = false;
      return;
    }
    if (customerId != null && selected) {
      setText(labelOf(selected));
      return;
    }
    if (customerId == null) setText(q);
  }, [customerId, selected, q]);

  // Debounce: cập nhật q khi đang gõ (chưa chọn khách).
  useEffect(() => {
    // picking: tránh đẩy nhãn khách vừa chọn vào q trước khi URL kịp cập nhật customerId.
    if (customerId != null || picking.current) return;
    if (text === q) return;
    const timer = setTimeout(() => onQChange(text), 300);
    return () => clearTimeout(timer);
    // Giống SearchBox: không phụ thuộc onQChange (đổi mỗi render từ URL params).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text, q, customerId]);

  // Debounce gợi ý khách.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSuggest(text.trim()), 300);
    return () => clearTimeout(timer);
  }, [text]);

  const suggestQ = debouncedSuggest;
  const selectedLabel = selected ? labelOf(selected) : '';
  const searching =
    open
    && suggestQ.length >= 2
    && !(customerId != null && text === selectedLabel);

  const results = useQuery({
    queryKey: ['customer-search', suggestQ],
    queryFn: ({ signal }) => customersApi.list({ q: suggestQ, isActive: true, pageSize: 20 }, signal),
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
    if (q) onQChange('');
    onCustomerChange(c.id);
  };

  const clear = () => {
    setText('');
    setDebouncedSuggest('');
    setOpen(false);
    if (q) onQChange('');
    if (customerId != null) onCustomerChange(null);
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
    <div ref={rootRef} className="relative w-full sm:w-[320px]">
      <div className="relative">
        <Search size={14} className="pointer-events-none absolute left-2.5 top-[10px] text-ink-faint" aria-hidden />
        <Input
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-label={placeholder}
          autoComplete="off"
          className="pl-8 pr-8"
          placeholder={placeholder}
          value={text}
          onChange={(e) => {
            const next = e.target.value;
            setText(next);
            setOpen(true);
            if (customerId != null) onCustomerChange(null);
          }}
          onFocus={() => setOpen(true)}
          onKeyDown={onKeyDown}
        />
        {(text || customerId != null || q) && (
          <button
            type="button"
            className="absolute right-2 top-[8px] rounded p-0.5 text-ink-faint hover:bg-rowalt hover:text-ink"
            aria-label="Xóa tìm kiếm"
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
          {suggestQ.length < 2 && (
            <li className="px-3 py-2 text-small text-ink-faint">
              Gõ mã đơn để tìm nhanh, hoặc ≥2 ký tự để chọn khách cụ thể…
            </li>
          )}
          {searching && results.isFetching && (
            <li className="px-3 py-2 text-small text-ink-faint">Đang tìm khách…</li>
          )}
          {searching && !results.isFetching && items.length === 0 && (
            <li className="px-3 py-2 text-small text-ink-faint">
              Không có khách khớp — vẫn lọc đơn theo chữ đang gõ
            </li>
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
