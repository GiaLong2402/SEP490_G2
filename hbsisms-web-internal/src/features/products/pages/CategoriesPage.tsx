import { useMemo, useState } from 'react';
import { ChevronDown, Pencil, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, IconButton, PageHeader, type Column } from '@/shared/ui';
import { CategoryFormModal } from '../components/CategoryFormModal';
import { useCategories } from '../hooks';
import type { CategoryListItem } from '../types';

type Row = CategoryListItem & { depth: number; hasChildren: boolean };

function sortCategories(a: CategoryListItem, b: CategoryListItem) {
  return a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi');
}

/** Index con theo parent + danh sách gốc (parent null hoặc parent không còn trong list). */
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

/** Chỉ bung các nhánh đang mở trong expandedIds. */
function flattenVisible(
  roots: CategoryListItem[],
  childrenByParentId: Map<number, CategoryListItem[]>,
  expandedIds: Set<number>,
): Row[] {
  const result: Row[] = [];
  const walk = (nodes: CategoryListItem[], depth: number) => {
    for (const node of nodes) {
      const children = childrenByParentId.get(node.id) ?? [];
      result.push({ ...node, depth, hasChildren: children.length > 0 });
      if (children.length > 0 && expandedIds.has(node.id)) walk(children, depth + 1);
    }
  };
  walk(roots, 0);
  return result;
}

export default function CategoriesPage() {
  const { data, isLoading, error, refetch } = useCategories();
  const [editing, setEditing] = useState<CategoryListItem | 'new' | null>(null);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(() => new Set());

  const { childrenByParentId, roots } = useMemo(() => indexTree(data ?? []), [data]);
  const visibleRows = useMemo(
    () => flattenVisible(roots, childrenByParentId, expandedIds),
    [roots, childrenByParentId, expandedIds],
  );

  const toggleExpand = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const columns: Column<Row>[] = [
    {
      key: 'name',
      header: 'Tên loại',
      width: '280px',
      cell: (c) => (
        <span className="inline-flex items-center gap-0.5 whitespace-nowrap font-medium" style={{ paddingLeft: c.depth * 16 }}>
          {c.hasChildren ? (
            <IconButton
              label={expandedIds.has(c.id) ? `Thu gọn ${c.name}` : `Mở rộng ${c.name}`}
              aria-expanded={expandedIds.has(c.id)}
              onClick={(e) => {
                e.stopPropagation();
                toggleExpand(c.id);
              }}
            >
              <ChevronDown size={15} className={`transition-transform ${expandedIds.has(c.id) ? '' : '-rotate-90'}`} />
            </IconButton>
          ) : (
            <span className="inline-block w-8 shrink-0" aria-hidden />
          )}
          {c.name}
        </span>
      ),
    },
    { key: 'code', header: 'Mã', width: '120px', cell: (c) => <span className="num whitespace-nowrap">{c.code}</span> },
    {
      key: 'description',
      header: 'Mô tả',
      hideOnMobile: true,
      cell: (c) => <span className="line-clamp-1 text-ink-muted">{c.description ?? '—'}</span>,
    },
    {
      key: 'products',
      header: 'Sản phẩm',
      align: 'right',
      width: '90px',
      cell: (c) => (
        <Link to={`/products?categoryId=${c.id}`} className="num text-primary hover:underline">
          {num(c.productCount)}
        </Link>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      width: '120px',
      cell: (c) => <Chip tone={c.isActive ? 'green' : 'neutral'}>{c.isActive ? 'Đang dùng' : 'Ngừng dùng'}</Chip>,
    },
    {
      key: 'edit',
      header: '',
      width: '48px',
      align: 'right',
      cell: (c) => (
        <IconButton label={`Sửa ${c.name}`} onClick={() => setEditing(c)}>
          <Pencil size={14} />
        </IconButton>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={
          <>
            <Link to="/products" className="hover:underline">
              Sản phẩm
            </Link>{' '}
            / Loại sản phẩm
          </>
        }
        title="Loại sản phẩm"
        subtitle="Nhóm sản phẩm để lọc nội bộ và hiển thị trên cổng B2C"
        actions={
          <Button variant="primary" icon={<Plus size={15} />} onClick={() => setEditing('new')}>
            Thêm loại
          </Button>
        }
      />
      <Card flush>
        <DataTable
          columns={columns}
          rows={data ? visibleRows : undefined}
          rowKey={(c) => c.id}
          loading={isLoading}
          error={error}
          onRetry={refetch}
          emptyText="Chưa có loại sản phẩm"
          emptyAction={<Button onClick={() => setEditing('new')}>Thêm loại đầu tiên</Button>}
        />
      </Card>
      <CategoryFormModal
        open={!!editing}
        category={editing && editing !== 'new' ? editing : undefined}
        onClose={() => setEditing(null)}
        onSaved={(saved) => {
          if (saved.parentId != null) {
            setExpandedIds((prev) => new Set(prev).add(saved.parentId!));
          }
        }}
      />
    </>
  );
}
