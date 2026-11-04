import { Fragment, useState, type ReactNode } from 'react';
import { ChevronDown, ChevronLeft, ChevronRight, Inbox, TriangleAlert } from 'lucide-react';
import { Button, IconButton } from './Button';

export interface Column<T> {
  key: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  align?: 'left' | 'right' | 'center';
  width?: string;
  /** Ẩn trên màn hình hẹp để bảng vẫn đọc được. */
  hideOnMobile?: boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[] | undefined;
  rowKey: (row: T) => string | number;
  loading?: boolean;
  error?: unknown;
  onRetry?: () => void;
  emptyText?: string;
  emptyAction?: ReactNode;
  expandable?: (row: T) => ReactNode;
  footer?: ReactNode;
}

export function DataTable<T>({ columns, rows, rowKey, loading, error, onRetry, emptyText = 'Chưa có dữ liệu', emptyAction, expandable, footer }: DataTableProps<T>) {
  const [expanded, setExpanded] = useState<Set<string | number>>(new Set());
  const colCount = columns.length + (expandable ? 1 : 0);
  const alignClass = (align?: Column<T>['align']) => (align === 'right' ? 'text-right' : align === 'center' ? 'text-center' : 'text-left');
  const mobile = (c: Column<T>) => (c.hideOnMobile ? 'hidden md:table-cell' : '');

  const toggle = (key: string | number) =>
    setExpanded((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });

  return (
    <div className="overflow-x-auto" aria-busy={loading}>
      <table className="w-full border-collapse text-small">
        <thead>
          <tr className="bg-thead text-ink-muted">
            {expandable && <th className="w-9" aria-label="Mở rộng" />}
            {columns.map((c) => (
              <th key={c.key} scope="col" style={c.width ? { width: c.width, minWidth: c.width } : undefined} className={`h-[34px] whitespace-nowrap px-3 text-micro font-semibold uppercase tracking-wide ${alignClass(c.align)} ${mobile(c)}`}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading &&
            Array.from({ length: 6 }).map((_, i) => (
              <tr key={i} className="border-t border-line">
                <td colSpan={colCount} className="h-[38px] px-3">
                  <div className="h-3 animate-pulse rounded bg-rowalt" style={{ width: `${88 - i * 7}%` }} />
                </td>
              </tr>
            ))}
          {!loading && !!error && (
            <tr>
              <td colSpan={colCount}>
                <ErrorState error={error} onRetry={onRetry} />
              </td>
            </tr>
          )}
          {!loading && !error && rows?.length === 0 && (
            <tr>
              <td colSpan={colCount} className="px-3 py-10 text-center text-ink-muted">
                <Inbox size={22} className="mx-auto mb-2 text-ink-faint" aria-hidden />
                {emptyText}
                {emptyAction && <div className="mt-3">{emptyAction}</div>}
              </td>
            </tr>
          )}
          {!loading &&
            !error &&
            rows?.map((row) => {
              const key = rowKey(row);
              const isOpen = expanded.has(key);
              return (
                <Fragment key={key}>
                  <tr className="border-t border-line hover:bg-rowalt/60">
                    {expandable && (
                      <td className="pl-1.5">
                        <IconButton label={isOpen ? 'Thu gọn' : 'Mở rộng'} aria-expanded={isOpen} onClick={() => toggle(key)}>
                          <ChevronDown size={15} className={`transition-transform ${isOpen ? '' : '-rotate-90'}`} />
                        </IconButton>
                      </td>
                    )}
                    {columns.map((c) => (
                      <td key={c.key} className={`h-[38px] px-3 py-1.5 ${alignClass(c.align)} ${mobile(c)}`}>
                        {c.cell(row)}
                      </td>
                    ))}
                  </tr>
                  {expandable && isOpen && (
                    <tr className="border-t border-line bg-canvas">
                      <td colSpan={colCount} className="px-4 py-3">
                        {expandable(row)}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
        </tbody>
        {footer && !loading && !error && <tfoot>{footer}</tfoot>}
      </table>
    </div>
  );
}

export function Pagination({ page, pageSize, total, onChange }: { page: number; pageSize: number; total: number; onChange: (page: number) => void }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
  return (
    <nav aria-label="Phân trang" className="flex items-center justify-between gap-3 border-t border-line px-3 py-2 text-small text-ink-muted">
      <span>
        {from}–{Math.min(page * pageSize, total)} / {total}
      </span>
      <div className="flex items-center gap-1">
        <IconButton label="Trang trước" disabled={page <= 1} onClick={() => onChange(page - 1)}>
          <ChevronLeft size={15} />
        </IconButton>
        <span className="px-1 tabular-nums">
          {page} / {pages}
        </span>
        <IconButton label="Trang sau" disabled={page >= pages} onClick={() => onChange(page + 1)}>
          <ChevronRight size={15} />
        </IconButton>
      </div>
    </nav>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div role="alert" className="px-4 py-10 text-center text-body text-ink-muted">
      <TriangleAlert size={22} className="mx-auto mb-2 text-red-fg" aria-hidden />
      <p>{error instanceof Error ? error.message : 'Không tải được dữ liệu'}</p>
      {onRetry && (
        <Button className="mt-3" onClick={onRetry}>
          Thử lại
        </Button>
      )}
    </div>
  );
}

export function PageLoading() {
  return (
    <div aria-busy className="space-y-3">
      <div className="h-7 w-52 animate-pulse rounded bg-rowalt" />
      <div className="h-24 animate-pulse rounded-md bg-rowalt" />
      <div className="h-64 animate-pulse rounded-md bg-rowalt" />
    </div>
  );
}
