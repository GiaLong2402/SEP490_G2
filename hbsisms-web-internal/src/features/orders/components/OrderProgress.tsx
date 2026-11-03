import { Check } from 'lucide-react';
import { orderStateMeta, type OrderState } from '@/shared/domain/enums';

const FLOW: OrderState[] = ['pending', 'confirmed', 'preparing', 'to_ship', 'partial', 'delivered', 'completed'];

export function OrderProgress({ state }: { state: OrderState }) {
  if (state === 'cancelled') return null;
  const current = FLOW.indexOf(state);
  return (
    <ol className="mb-4 flex overflow-x-auto rounded-md border border-line bg-card p-3 shadow-card">
      {FLOW.map((step, i) => {
        const done = i < current;
        const active = i === current;
        return (
          <li key={step} aria-current={active ? 'step' : undefined} className="flex min-w-[112px] flex-1 items-center gap-2">
            <span className={`flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-micro ${done ? 'bg-primary text-white' : active ? 'border-2 border-primary text-primary' : 'border border-field text-ink-faint'}`}>
              {done ? <Check size={12} aria-hidden /> : i + 1}
            </span>
            <span className={`whitespace-nowrap text-small ${active ? 'font-semibold text-ink' : done ? 'text-ink-muted' : 'text-ink-faint'}`}>{orderStateMeta[step].label}</span>
            {i < FLOW.length - 1 && <span className={`mx-1.5 h-px min-w-3 flex-1 ${done ? 'bg-primary' : 'bg-line'}`} />}
          </li>
        );
      })}
    </ol>
  );
}
