import { useEffect, useLayoutEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode, type RefObject } from 'react';
import { createPortal } from 'react-dom';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { CategoryListItem } from '../types';

const PANEL_SCROLL = 'max-h-[min(320px,70vh)] overflow-y-auto';
const SUBMENU_MIN_W = 180;

function sortCategories(a: CategoryListItem, b: CategoryListItem) {
  return a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi');
}

function indexTree(rows: CategoryListItem[]) {
  const ids = new Set(rows.map((c) => c.id));
  const childrenByParentId = new Map<number, CategoryListItem[]>();
  const roots: CategoryListItem[] = [];

  for (const row of rows) {
    const parentMissing = row.parentId === null || !ids.has(row.parentId);
    if (parentMissing) {
      roots.push(row);
      continue;
    }
    const siblings = childrenByParentId.get(row.parentId!) ?? [];
    siblings.push(row);
    childrenByParentId.set(row.parentId!, siblings);
  }

  roots.sort(sortCategories);
  for (const siblings of childrenByParentId.values()) siblings.sort(sortCategories);
  return { childrenByParentId, roots };
}

function isInsideTreeMenu(target: EventTarget | null) {
  return target instanceof Element && !!target.closest('[data-category-tree-menu="1"]');
}

/** Native <dialog showModal> dùng top-layer — portal vào dialog (hoặc body) để menu không bị đè. */
function menuPortalRoot(from: HTMLElement | null): HTMLElement {
  return from?.closest('dialog') ?? document.body;
}

function SubmenuPanel({ anchorRef, children }: { anchorRef: RefObject<HTMLElement | null>; children: ReactNode }) {
  const [style, setStyle] = useState<CSSProperties>({ position: 'fixed', zIndex: 81, visibility: 'hidden' });

  useLayoutEffect(() => {
    const anchor = anchorRef.current;
    if (!anchor) return;
    const rect = anchor.getBoundingClientRect();
    const openLeft = window.innerWidth - rect.right < SUBMENU_MIN_W + 8;
    setStyle({
      position: 'fixed',
      top: Math.max(8, Math.min(rect.top, window.innerHeight - 48)),
      left: openLeft ? undefined : rect.right + 2,
      right: openLeft ? window.innerWidth - rect.left + 2 : undefined,
      zIndex: 81,
      minWidth: SUBMENU_MIN_W,
    });
  }, [anchorRef]);

  if (typeof document === 'undefined') return null;
  return createPortal(
    <div data-category-tree-menu="1" style={style} className={`rounded-md border border-line bg-card shadow-pop ${PANEL_SCROLL}`}>
      {children}
    </div>,
    menuPortalRoot(anchorRef.current),
  );
}

function CategoryBranch({
  nodes,
  childrenByParentId,
  selectedId,
  openPath,
  onOpenPath,
  onPick,
  allowPickParent,
}: {
  nodes: CategoryListItem[];
  childrenByParentId: Map<number, CategoryListItem[]>;
  selectedId: string;
  openPath: number[];
  onOpenPath: (path: number[]) => void;
  onPick: (id: string) => void;
  allowPickParent: boolean;
}) {
  return (
    <ul className="min-w-[180px] py-1" role="menu">
      {nodes.map((node) => (
        <CategoryBranchItem
          key={node.id}
          node={node}
          childrenByParentId={childrenByParentId}
          selectedId={selectedId}
          openPath={openPath}
          onOpenPath={onOpenPath}
          onPick={onPick}
          allowPickParent={allowPickParent}
        />
      ))}
    </ul>
  );
}

function CategoryBranchItem({
  node,
  childrenByParentId,
  selectedId,
  openPath,
  onOpenPath,
  onPick,
  allowPickParent,
}: {
  node: CategoryListItem;
  childrenByParentId: Map<number, CategoryListItem[]>;
  selectedId: string;
  openPath: number[];
  onOpenPath: (path: number[]) => void;
  onPick: (id: string) => void;
  allowPickParent: boolean;
}) {
  const rowRef = useRef<HTMLLIElement>(null);
  const children = childrenByParentId.get(node.id) ?? [];
  const hasChildren = children.length > 0;
  const open = openPath[0] === node.id;
  const selected = selectedId === String(node.id);
  const canPick = allowPickParent || !hasChildren;

  return (
    <li
      ref={rowRef}
      className="relative"
      role="none"
      onMouseEnter={() => onOpenPath(hasChildren ? [node.id] : [])}
    >
      <div className="flex items-stretch">
        <button
          type="button"
          role="menuitem"
          className={`flex flex-1 items-center px-3 py-1.5 text-left text-small hover:bg-line/60 ${selected ? 'font-medium text-primary' : 'text-ink'} ${!canPick ? 'cursor-default' : ''}`}
          onClick={() => {
            if (!canPick) {
              onOpenPath(open ? [] : [node.id]);
              return;
            }
            onPick(String(node.id));
          }}
        >
          {node.name}
        </button>
        {hasChildren ? (
          <button
            type="button"
            aria-label={`Mở ${node.name}`}
            aria-expanded={open}
            className={`flex items-center px-2 text-ink-muted hover:bg-line/60 ${open ? 'bg-line/40' : ''}`}
            onClick={(e) => {
              e.stopPropagation();
              onOpenPath(open ? [] : [node.id]);
            }}
          >
            <ChevronRight size={14} />
          </button>
        ) : null}
      </div>
      {hasChildren && open ? (
        <SubmenuPanel anchorRef={rowRef}>
          <CategoryBranch
            nodes={children}
            childrenByParentId={childrenByParentId}
            selectedId={selectedId}
            openPath={openPath.slice(1)}
            onOpenPath={(childPath) => onOpenPath([node.id, ...childPath])}
            onPick={onPick}
            allowPickParent={allowPickParent}
          />
        </SubmenuPanel>
      ) : null}
    </li>
  );
}

export function CategoryTreePicker({
  categories,
  value,
  onChange,
  emptyLabel,
  formatTrigger,
  allowPickParent,
  className = 'relative w-full',
  ariaLabel = 'Loại sản phẩm',
}: {
  categories: CategoryListItem[];
  value: string;
  onChange: (value: string) => void;
  emptyLabel: string;
  formatTrigger: (selectedName: string | undefined) => string;
  allowPickParent: boolean;
  className?: string;
  ariaLabel?: string;
}) {
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const [open, setOpen] = useState(false);
  const [openPath, setOpenPath] = useState<number[]>([]);
  const [menuStyle, setMenuStyle] = useState<CSSProperties>({});

  const visible = useMemo(
    () => categories.filter((c) => c.isActive || String(c.id) === value),
    [categories, value],
  );
  const { roots, childrenByParentId } = useMemo(() => indexTree(visible), [visible]);
  const selectedName = visible.find((c) => String(c.id) === value)?.name;

  const updateMenuPosition = () => {
    const trigger = triggerRef.current;
    if (!trigger) return;
    const rect = trigger.getBoundingClientRect();
    const gap = 4;
    const maxHeight = Math.min(320, window.innerHeight * 0.7);
    const spaceBelow = window.innerHeight - rect.bottom - gap;
    const openUp = spaceBelow < Math.min(160, maxHeight) && rect.top > spaceBelow;
    const width = Math.max(rect.width, 200);
    setMenuStyle({
      position: 'fixed',
      left: Math.max(8, Math.min(rect.left, window.innerWidth - width - 8)),
      top: openUp ? undefined : rect.bottom + gap,
      bottom: openUp ? window.innerHeight - rect.top + gap : undefined,
      minWidth: width,
      zIndex: 80,
    });
  };

  useLayoutEffect(() => {
    if (!open) return;
    updateMenuPosition();
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onReposition = () => updateMenuPosition();
    window.addEventListener('resize', onReposition);
    window.addEventListener('scroll', onReposition, true);
    return () => {
      window.removeEventListener('resize', onReposition);
      window.removeEventListener('scroll', onReposition, true);
    };
  }, [open]);

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (rootRef.current?.contains(e.target as Node) || isInsideTreeMenu(e.target)) return;
      setOpen(false);
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') setOpen(false);
    };
    document.addEventListener('mousedown', onDoc);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDoc);
      document.removeEventListener('keydown', onKey);
    };
  }, [open]);

  useEffect(() => {
    if (!open) setOpenPath([]);
  }, [open]);

  const pick = (id: string) => {
    onChange(id);
    setOpen(false);
  };

  const menu =
    open && typeof document !== 'undefined'
      ? createPortal(
          <div data-category-tree-menu="1" style={menuStyle} className={`rounded-md border border-line bg-card shadow-pop ${PANEL_SCROLL}`}>
            <ul className="min-w-[200px] py-1" role="menu">
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  className={`w-full px-3 py-1.5 text-left text-small hover:bg-line/60 ${!value ? 'font-medium text-primary' : 'text-ink'}`}
                  onMouseEnter={() => setOpenPath([])}
                  onClick={() => pick('')}
                >
                  {emptyLabel}
                </button>
              </li>
              <li className="my-1 border-t border-line" role="separator" />
            </ul>
            <CategoryBranch
              nodes={roots}
              childrenByParentId={childrenByParentId}
              selectedId={value}
              openPath={openPath}
              onOpenPath={setOpenPath}
              onPick={pick}
              allowPickParent={allowPickParent}
            />
          </div>,
          menuPortalRoot(rootRef.current),
        )
      : null;

  return (
    <div ref={rootRef} className={className}>
      <button
        ref={triggerRef}
        type="button"
        aria-haspopup="menu"
        aria-expanded={open}
        aria-label={ariaLabel}
        className="field-control flex w-full items-center justify-between gap-2 text-left"
        onClick={() => setOpen((v) => !v)}
      >
        <span className="truncate">{formatTrigger(selectedName)}</span>
        <ChevronDown size={14} className={`shrink-0 text-ink-faint transition ${open ? 'rotate-180' : ''}`} aria-hidden />
      </button>
      {menu}
    </div>
  );
}

/** Filter trang sản phẩm: click cha được (API gồm hậu duệ). */
export function CategoryFilterMenu({
  categories,
  value,
  onChange,
}: {
  categories: CategoryListItem[];
  value: string;
  onChange: (value: string) => void;
}) {
  return (
    <CategoryTreePicker
      categories={categories}
      value={value}
      onChange={onChange}
      emptyLabel="Loại: tất cả"
      formatTrigger={(name) => (name ? `Loại: ${name}` : 'Loại: tất cả')}
      allowPickParent
      className="relative w-full sm:w-[220px]"
      ariaLabel="Loại"
    />
  );
}

/** Form sản phẩm: hover cha → con; chỉ chọn loại lá / Chưa phân loại. */
export function CategorySelectMenu({
  categories,
  value,
  onChange,
}: {
  categories: CategoryListItem[];
  value: number | null;
  onChange: (value: number | null) => void;
}) {
  return (
    <CategoryTreePicker
      categories={categories}
      value={value != null ? String(value) : ''}
      onChange={(v) => onChange(v ? Number(v) : null)}
      emptyLabel="Chưa phân loại"
      formatTrigger={(name) => name ?? 'Chưa phân loại'}
      allowPickParent={false}
      ariaLabel="Loại sản phẩm"
    />
  );
}
