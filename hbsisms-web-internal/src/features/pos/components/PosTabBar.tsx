import { Plus, X } from 'lucide-react';
import { IconButton } from '@/shared/ui';
import { MAX_TABS } from '../usePosTabs';
import type { PosTab } from '../types';

interface Props {
  tabs: PosTab[];
  activeId: string;
  onSelect: (id: string) => void;
  onClose: (tab: PosTab) => void;
  onAdd: () => void;
}

export function PosTabBar({ tabs, activeId, onSelect, onClose, onAdd }: Props) {
  return (
    <div className="flex items-end gap-1 overflow-x-auto overflow-y-hidden border-b border-line">
      <div role="tablist" aria-label="Các đơn đang mở" className="flex items-end gap-1">
        {tabs.map((tab, index) => {
          const active = tab.id === activeId;
          const count = tab.lines.length;
          return (
            <div key={tab.id} className={`-mb-px flex h-[38px] shrink-0 items-center rounded-t-md border ${active ? 'border-line border-b-card bg-card' : 'border-transparent bg-rowalt hover:bg-thead'}`}>
              <button
                type="button"
                role="tab"
                aria-selected={active}
                title={index < 9 ? `Alt+${index + 1}` : undefined}
                onClick={() => onSelect(tab.id)}
                className={`flex h-full items-center gap-1.5 whitespace-nowrap pl-3 pr-1.5 text-body ${active ? 'font-semibold text-primary' : 'text-ink-muted'}`}
              >
                Đơn {tab.number}
                {count > 0 && <span className="rounded-full bg-primary-soft px-1.5 text-micro text-primary">{count}</span>}
              </button>
              <IconButton label={`Đóng đơn ${tab.number}`} className="mr-1 !h-6 !w-6" onClick={() => onClose(tab)}><X size={13} /></IconButton>
            </div>
          );
        })}
      </div>
      <IconButton label={tabs.length >= MAX_TABS ? `Tối đa ${MAX_TABS} đơn cùng lúc` : 'Thêm đơn (F2)'} disabled={tabs.length >= MAX_TABS} className="mb-1 shrink-0" onClick={onAdd}><Plus size={16} /></IconButton>
    </div>
  );
}
