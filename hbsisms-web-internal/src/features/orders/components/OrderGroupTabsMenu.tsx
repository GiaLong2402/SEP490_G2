import { useEffect, useId, useRef, useState } from 'react';
import { Check, ChevronDown, Columns3 } from 'lucide-react';
import type { OrderListGroup } from '../types';

export type OrderGroupOption = { key: OrderListGroup; label: string };

export function OrderGroupTabsMenu({
  options,
  visible,
  onChange,
}: {
  options: OrderGroupOption[];
  visible: OrderListGroup[];
  onChange: (next: OrderListGroup[]) => void;
}) {
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);
  const menuId = useId();
  const visibleSet = new Set(visible);

  useEffect(() => {
    if (!open) return;
    const onPointer = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onPointer);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onPointer);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  const toggle = (key: OrderListGroup) => {
    if (visibleSet.has(key)) {
      onChange(visible.filter((k) => k !== key));
    } else {
      onChange([...visible, key]);
    }
  };

  return (
    <div ref={rootRef} className="relative shrink-0">
      <button
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-controls={menuId}
        onClick={() => setOpen((v) => !v)}
        className="inline-flex h-8 items-center gap-1.5 rounded-md border border-field bg-card px-2.5 text-small font-medium text-ink hover:bg-rowalt"
      >
        <Columns3 size={14} aria-hidden />
        Tùy chỉnh nhóm
        <ChevronDown size={14} className={`text-ink-muted transition-transform ${open ? 'rotate-180' : ''}`} aria-hidden />
      </button>
      {open && (
        <div
          id={menuId}
          role="menu"
          aria-label="Chọn nhóm đơn hiển thị"
          className="absolute right-0 top-[calc(100%+4px)] z-30 min-w-[220px] rounded-md border border-line bg-card py-1 shadow-pop"
        >
          {options.map((opt) => {
            const checked = visibleSet.has(opt.key);
            return (
              <button
                key={opt.key}
                type="button"
                role="menuitemcheckbox"
                aria-checked={checked}
                onClick={() => toggle(opt.key)}
                className="flex w-full items-center gap-2 px-3 py-1.5 text-left text-body text-ink hover:bg-rowalt"
              >
                <span className="min-w-0 flex-1">{opt.label}</span>
                <span className={`inline-flex h-4 w-4 shrink-0 items-center justify-center ${checked ? 'text-primary' : 'text-transparent'}`} aria-hidden>
                  <Check size={14} strokeWidth={2.5} />
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
