import type { ReactNode } from 'react';

export function Tabs<K extends string>({
  tabs,
  value,
  onChange,
  label,
  trailing,
}: {
  tabs: { key: K; label: ReactNode }[];
  value: K;
  onChange: (key: K) => void;
  label: string;
  /** Nút / action cùng hàng tab (vd. tùy chỉnh nhóm). */
  trailing?: ReactNode;
}) {
  return (
    <div className="flex items-stretch border-b border-line">
      <div role="tablist" aria-label={label} className="flex min-w-0 flex-1 gap-1 overflow-x-auto px-3">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={tab.key === value}
            onClick={() => onChange(tab.key)}
            className={`-mb-px h-10 whitespace-nowrap border-b-2 px-3 text-body font-medium ${tab.key === value ? 'border-primary text-primary' : 'border-transparent text-ink-muted hover:text-ink'}`}
          >
            {tab.label}
          </button>
        ))}
      </div>
      {trailing != null && (
        <div className="flex shrink-0 items-center border-l border-line px-2">
          {trailing}
        </div>
      )}
    </div>
  );
}
