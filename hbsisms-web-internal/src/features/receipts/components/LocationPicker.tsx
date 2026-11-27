import { useEffect, useMemo, useRef, useState, type CSSProperties } from 'react';
import { Check, ChevronDown, ChevronRight } from 'lucide-react';
import type { WarehouseDetail } from '@/features/warehouses';
import { num } from '@/shared/lib/format';
import { locationMatchesPreferred } from '../preferredFinishedZones';

interface LocationPickerProps {
  id?: string;
  'aria-invalid'?: boolean;
  'aria-describedby'?: string;
  'aria-label'?: string;
  warehouses: WarehouseDetail[];
  /** id vị trí đang chọn ('' = chưa chọn) */
  value: string;
  /** Số khay cần chỗ (chưa nhập thì coi như 1). */
  need: number;
  /** Số khay còn để được ở vị trí cho dòng này (đã trừ các dòng khác cùng phiếu). */
  roomFor: (locationId: number) => number;
  onChange: (locationId: string) => void;
  /** Mã khu gợi ý theo SP (CHA, CA…) — ưu tiên hiện trước, vẫn cho chọn khu khác. */
  preferredZones?: string[];
}

const PANEL_WIDTH = 520;

/**
 * Chọn vị trí 2 tầng: bấm → danh sách kho còn chỗ; rê chuột / chọn một kho → các vị trí của kho đó
 * đủ chỗ cho số khay cần nhập. Có preferredZones thì xếp gợi ý lên trước + gắn nhãn.
 */
export function LocationPicker({
  warehouses,
  value,
  need,
  roomFor,
  onChange,
  preferredZones = [],
  ...aria
}: LocationPickerProps) {
  const buttonRef = useRef<HTMLButtonElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);
  const [pos, setPos] = useState<CSSProperties | null>(null);
  const [activeId, setActiveId] = useState<number | null>(null);
  const needed = Math.max(1, Math.floor(need) || 0);
  const hasPrefer = preferredZones.length > 0;

  const usable = (zone: string | null | undefined, code: string) =>
    !(zone && zone.toLowerCase() === 'ngừng dùng') && !code.toUpperCase().startsWith('XX-');

  const options = useMemo(
    () =>
      warehouses
        .filter((w) => w.isActive)
        .map((w) => {
          const locations = w.locations
            .filter((l) => usable(l.zone, l.code) && roomFor(Number(l.id)) >= needed)
            .map((l) => ({
              ...l,
              preferred: hasPrefer && locationMatchesPreferred(l, preferredZones),
            }))
            .sort((a, b) => Number(b.preferred) - Number(a.preferred) || a.code.localeCompare(b.code));
          const preferCount = locations.filter((l) => l.preferred).length;
          return {
            w,
            room: Math.max(0, w.capacity - w.totalQty),
            locations,
            preferCount,
          };
        })
        .filter((o) => o.locations.length > 0)
        .sort((a, b) => b.preferCount - a.preferCount || a.w.name.localeCompare(b.w.name)),
    [warehouses, needed, roomFor, preferredZones, hasPrefer],
  );

  const selected = warehouses.flatMap((w) => w.locations.map((l) => ({ w, l }))).find((x) => String(x.l.id) === value);
  const active = options.find((o) => Number(o.w.id) === activeId) ?? options[0];
  const selectedPreferred =
    selected && hasPrefer ? locationMatchesPreferred(selected.l, preferredZones) : true;

  const close = () => setPos(null);
  const open = () => {
    const r = buttonRef.current?.getBoundingClientRect();
    if (!r) return;
    const left = Math.max(8, Math.min(r.left, window.innerWidth - PANEL_WIDTH - 8));
    setPos(
      window.innerHeight - r.bottom > 320
        ? { left, top: r.bottom + 4, width: PANEL_WIDTH }
        : { left, bottom: window.innerHeight - r.top + 4, width: PANEL_WIDTH },
    );
    const preferWh = options.find((o) => o.preferCount > 0);
    setActiveId(selected ? Number(selected.w.id) : preferWh ? Number(preferWh.w.id) : null);
  };

  useEffect(() => {
    if (!pos) return;
    const inside = (target: EventTarget | null) =>
      !!target && (panelRef.current?.contains(target as Node) || buttonRef.current?.contains(target as Node));
    const onDown = (e: MouseEvent) => {
      if (!inside(e.target)) close();
    };
    const onScroll = (e: Event) => {
      if (!panelRef.current?.contains(e.target as Node)) close();
    };
    document.addEventListener('mousedown', onDown);
    window.addEventListener('scroll', onScroll, true);
    window.addEventListener('resize', close);
    return () => {
      document.removeEventListener('mousedown', onDown);
      window.removeEventListener('scroll', onScroll, true);
      window.removeEventListener('resize', close);
    };
  }, [pos]);

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        {...aria}
        aria-haspopup="dialog"
        aria-expanded={!!pos}
        onClick={() => (pos ? close() : open())}
        className="field-control flex items-center gap-2 text-left"
      >
        <span className={`min-w-0 flex-1 truncate ${selected ? 'text-ink' : 'text-ink-faint'}`}>
          {selected ? (
            <>
              {selected.l.code}
              {selected.l.zone ? ` · ${selected.l.zone}` : ''} <span className="text-ink-faint">- {selected.w.code}</span>
              {hasPrefer && !selectedPreferred ? (
                <span className="ml-1 text-amber-fg">· lệch khu gợi ý</span>
              ) : null}
            </>
          ) : (
            'Chọn vị trí...'
          )}
        </span>
        <ChevronDown size={14} className="shrink-0 text-ink-faint" aria-hidden />
      </button>

      {pos && (
        <div
          ref={panelRef}
          role="dialog"
          aria-label="Chọn vị trí nhập hàng"
          style={pos}
          className="fixed z-50 overflow-hidden rounded-md border border-line bg-card text-body shadow-pop"
          onKeyDown={(e) => {
            if (e.key === 'Escape') {
              e.preventDefault();
              e.stopPropagation();
              close();
              buttonRef.current?.focus();
            }
          }}
        >
          <div className="border-b border-line bg-rowalt/60 px-3 py-2 text-small text-ink-muted">
            Cần chỗ để <span className="font-semibold text-ink">{num(needed)} khay</span>
            {need > 0 ? ' — chỉ hiện chỗ còn đủ trống' : ' — hãy nhập số khay trước để lọc đúng chỗ'}
            {hasPrefer ? (
              <>
                {' '}
                · gợi ý khu <span className="font-semibold text-primary">{preferredZones.join(' / ')}</span>
              </>
            ) : null}
          </div>
          {options.length === 0 || !active ? (
            <p className="px-3 py-6 text-center text-small text-ink-muted">Không còn vị trí nào đủ chỗ cho {num(needed)} khay.</p>
          ) : (
            <div className="grid grid-cols-2">
              <ul className="max-h-72 overflow-y-auto border-r border-line py-1" aria-label="Kho">
                {options.map(({ w, room, locations, preferCount }) => {
                  const isActive = active.w.id === w.id;
                  return (
                    <li key={w.id}>
                      <button
                        type="button"
                        onMouseEnter={() => setActiveId(Number(w.id))}
                        onFocus={() => setActiveId(Number(w.id))}
                        onClick={() => setActiveId(Number(w.id))}
                        className={`flex w-full items-center gap-2 px-3 py-2 text-left ${isActive ? 'bg-primary-soft' : 'hover:bg-rowalt'}`}
                      >
                        <span className="min-w-0 flex-1">
                          <span className={`block truncate font-medium ${isActive ? 'text-primary' : 'text-ink'}`}>{w.name}</span>
                          <span className="block text-small text-ink-muted">Kho còn trống {num(room)} khay</span>
                          <span className="block text-small text-ink-faint">
                            {locations.length} chỗ đủ · {preferCount > 0 ? `${preferCount} gợi ý` : 'không khớp gợi ý'}
                          </span>
                        </span>
                        <ChevronRight size={14} className="shrink-0 text-ink-faint" aria-hidden />
                      </button>
                    </li>
                  );
                })}
              </ul>
              <ul className="max-h-72 overflow-y-auto py-1" aria-label={`Vị trí trong ${active.w.name}`}>
                {active.locations.map((l) => {
                  const isSelected = String(l.id) === value;
                  return (
                    <li key={l.id}>
                      <button
                        type="button"
                        onClick={() => {
                          onChange(String(l.id));
                          close();
                          buttonRef.current?.focus();
                        }}
                        className={`flex w-full items-center gap-2 px-3 py-2 text-left hover:bg-rowalt ${l.preferred ? 'bg-primary/[0.04]' : ''}`}
                      >
                        <span className="min-w-0 flex-1">
                          <span className={`flex flex-wrap items-center gap-1.5 font-medium ${isSelected ? 'text-primary' : 'text-ink'}`}>
                            <span>
                              {l.code}
                              {l.zone ? <span className="font-normal text-ink-muted"> · {l.zone}</span> : null}
                            </span>
                            {l.preferred ? (
                              <span className="rounded border border-primary/30 bg-primary/10 px-1.5 py-0.5 text-micro font-semibold uppercase tracking-wide text-primary">
                                Gợi ý
                              </span>
                            ) : hasPrefer ? (
                              <span className="text-micro font-normal text-ink-faint">khác khu</span>
                            ) : null}
                          </span>
                          <span className="block text-small text-ink-muted">Còn trống {num(roomFor(Number(l.id)))} khay</span>
                          <span className="block text-small text-ink-faint">Nhập xong còn trống {num(roomFor(Number(l.id)) - needed)} khay</span>
                        </span>
                        {isSelected && <Check size={15} className="shrink-0 text-primary" aria-hidden />}
                      </button>
                    </li>
                  );
                })}
              </ul>
            </div>
          )}
        </div>
      )}
    </>
  );
}
