import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { CheckCircle2, XCircle } from 'lucide-react';

interface ToastItem {
  id: number;
  kind: 'success' | 'error';
  message: string;
}

const ToastContext = createContext<{ success: (m: string) => void; error: (m: unknown) => void } | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);
  const hostRef = useRef<HTMLDivElement>(null);
  // <dialog> nằm ở top layer nên che mọi z-index; mở lại popover để toast luôn nổi trên dialog đang mở.
  useEffect(() => {
    const host = hostRef.current;
    if (!host?.showPopover) return;
    host.setAttribute('popover', 'manual');
    if (host.matches(':popover-open')) host.hidePopover();
    if (items.length > 0) host.showPopover();
  }, [items]);
  const push = useCallback((kind: ToastItem['kind'], message: string) => {
    const id = nextId.current++;
    setItems((list) => [...list, { id, kind, message }]);
    setTimeout(() => setItems((list) => list.filter((t) => t.id !== id)), kind === 'error' ? 6000 : 3500);
  }, []);
  const value = useMemo(
    () => ({
      success: (m: string) => push('success', m),
      error: (m: unknown) => push('error', m instanceof Error ? m.message : String(m)),
    }),
    [push],
  );
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div ref={hostRef} aria-live="polite" className="pointer-events-none fixed inset-auto bottom-4 right-4 z-[100] m-0 flex w-[340px] max-w-[calc(100vw-32px)] flex-col gap-2 overflow-visible border-0 bg-transparent p-0">
        {items.map((t) => (
          <div key={t.id} role="status" className="flex items-start gap-2 rounded-md border border-line bg-card px-3 py-2.5 text-body shadow-pop">
            {t.kind === 'success' ? <CheckCircle2 size={16} className="mt-px shrink-0 text-green-fg" aria-hidden /> : <XCircle size={16} className="mt-px shrink-0 text-red-fg" aria-hidden />}
            <span>{t.message}</span>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast phải nằm trong ToastProvider');
  return ctx;
}
