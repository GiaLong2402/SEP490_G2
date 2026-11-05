import { useEffect, useMemo, useRef, useState } from 'react';
import { ChevronDown, ChevronRight } from 'lucide-react';
import type { CategoryListItem } from '../types';

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
      {nodes.map((node) => {
        const children = childrenByParentId.get(node.id) ?? [];
        const hasChildren = children.length > 0;
        const open = openPath[0] === node.id;
        const selected = selectedId === String(node.id);
        const canPick = allowPickParent || !hasChildren;
        return (
          <li
            key={node.id}
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
              <div className="absolute left-full top-0 z-20 ml-0.5 rounded-md border border-line bg-card shadow-pop">
                <CategoryBranch
                  nodes={children}
                  childrenByParentId={childrenByParentId}
                  selectedId={selectedId}
                  openPath={openPath.slice(1)}
                  onOpenPath={(childPath) => onOpenPath([node.id, ...childPath])}
                  onPick={onPick}
                  allowPickParent={allowPickParent}
                />
              </div>
            ) : null}
          </li>
        );
      })}
    </ul>
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
  const [open, setOpen] = useState(false);
  const [openPath, setOpenPath] = useState<number[]>([]);

  const visible = useMemo(
    () => categories.filter((c) => c.isActive || String(c.id) === value),
    [categories, value],
  );
  const { roots, childrenByParentId } = useMemo(() => indexTree(visible), [visible]);
  const selectedName = visible.find((c) => String(c.id) === value)?.name;

  useEffect(() => {
    if (!open) return;
    const onDoc = (e: MouseEvent) => {
      if (!rootRef.current?.contains(e.target as Node)) setOpen(false);
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

  return (
    <div ref={rootRef} className={className}>
      <button
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
      {open ? (
        <div className="absolute left-0 top-[calc(100%+4px)] z-30 rounded-md border border-line bg-card shadow-pop">
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
        </div>
      ) : null}
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
