import type { ReactNode } from 'react';

export function Tabs<K extends string>({ tabs, value, onChange, label }: { tabs: { key: K; label: ReactNode }[]; value: K; onChange: (key: K) => void; label: string }) {
  return (
    <div role="tablist" aria-label={label} className="flex gap-1 overflow-x-auto border-b border-line px-3">
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
  );
}
