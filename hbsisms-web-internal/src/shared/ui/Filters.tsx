import { useEffect, useState, type ReactNode } from 'react';
import { Search } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { Select } from './Form';

/** Tham số danh sách lưu trên URL: giữ được bộ lọc khi quay lại và khi chia sẻ link. */
export function useListParams<K extends string = string>(filterKeys: readonly K[] = []) {
  const [params, setParams] = useSearchParams();
  const filters = Object.fromEntries(filterKeys.map((k) => [k, params.get(k) ?? ''])) as Record<K, string>;
  const apply = (entries: Array<[string, string]>) =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        let resetPage = false;
        for (const [key, value] of entries) {
          if (value) next.set(key, value);
          else next.delete(key);
          if (key !== 'page') resetPage = true;
        }
        if (resetPage) next.delete('page');
        return next;
      },
      { replace: true },
    );
  const clearFilters = () =>
    setParams(
      (prev) => {
        const next = new URLSearchParams(prev);
        next.delete('q');
        next.delete('page');
        for (const key of filterKeys) next.delete(key);
        return next;
      },
      { replace: true },
    );

  return {
    q: params.get('q') ?? '',
    page: Number(params.get('page') ?? 1),
    filters,
    setQ: (value: string) => apply([['q', value]]),
    setPage: (page: number) => apply([['page', page > 1 ? String(page) : '']]),
    setFilter: (key: K, value: string) => apply([[key, value]]),
    setFilters: (patch: Partial<Record<K, string>>) =>
      apply(Object.entries(patch) as Array<[string, string]>),
    clearFilters,
  };
}

export function SearchBox({ value, onChange, placeholder = 'Tìm kiếm...' }: { value: string; onChange: (value: string) => void; placeholder?: string }) {
  const [text, setText] = useState(value);
  useEffect(() => setText(value), [value]);
  useEffect(() => {
    if (text === value) return;
    const timer = setTimeout(() => onChange(text), 300);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [text]);
  return (
    <div className="relative w-full sm:w-[260px]">
      <Search size={14} className="pointer-events-none absolute left-2.5 top-[10px] text-ink-faint" aria-hidden />
      <input type="search" aria-label={placeholder} className="field-control pl-8" placeholder={placeholder} value={text} onChange={(e) => setText(e.target.value)} />
    </div>
  );
}

export function FilterSelect({ label, value, onChange, children }: { label: string; value: string; onChange: (value: string) => void; children: ReactNode }) {
  return (
    <Select aria-label={label} className="w-full sm:w-[190px]" value={value} onChange={(e) => onChange(e.target.value)}>
      <option value="">{label}: tất cả</option>
      {children}
    </Select>
  );
}

export function FilterBar({ children, actions }: { children: ReactNode; actions?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center gap-2 border-b border-line p-3">
      {children}
      {actions && <div className="ml-auto flex items-center gap-2">{actions}</div>}
    </div>
  );
}
