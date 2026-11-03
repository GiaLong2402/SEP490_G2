import { useState } from 'react';
import { Pencil, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, IconButton, PageHeader, type Column } from '@/shared/ui';
import { CategoryFormModal } from '../components/CategoryFormModal';
import { useCategories } from '../hooks';
import type { CategoryListItem } from '../types';

/** Xếp loại con ngay dưới loại cha, kèm độ sâu để thụt lề. */
function toTree(rows: CategoryListItem[]) {
  const ids = new Set(rows.map((c) => c.id));
  const result: (CategoryListItem & { depth: number })[] = [];
  const visit = (parentId: number | null, depth: number) =>
    rows
      .filter((c) => (parentId === null ? c.parentId === null || !ids.has(c.parentId) : c.parentId === parentId))
      .forEach((c) => {
        result.push({ ...c, depth });
        visit(c.id, depth + 1);
      });
  visit(null, 0);
  return result;
}

export default function CategoriesPage() {
  const { data, isLoading, error, refetch } = useCategories();
  const [editing, setEditing] = useState<CategoryListItem | 'new' | null>(null);

  const columns: Column<CategoryListItem & { depth: number }>[] = [
    { key: 'name', header: 'Tên loại', cell: (c) => <span className="font-medium" style={{ paddingLeft: c.depth * 20 }}>{c.name}</span> },
    { key: 'code', header: 'Mã', cell: (c) => <span className="num">{c.code}</span> },
    { key: 'description', header: 'Mô tả', hideOnMobile: true, cell: (c) => c.description ?? '—' },
    { key: 'products', header: 'Sản phẩm', align: 'right', cell: (c) => <Link to={`/products?categoryId=${c.id}`} className="num text-primary hover:underline">{num(c.productCount)}</Link> },
    { key: 'sort', header: 'Thứ tự', align: 'right', hideOnMobile: true, cell: (c) => <span className="num">{c.sortOrder}</span> },
    { key: 'status', header: 'Trạng thái', cell: (c) => <Chip tone={c.isActive ? 'green' : 'neutral'}>{c.isActive ? 'Đang dùng' : 'Ngừng dùng'}</Chip> },
    { key: 'edit', header: '', align: 'right', cell: (c) => <IconButton label={`Sửa ${c.name}`} onClick={() => setEditing(c)}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/products" className="hover:underline">Sản phẩm</Link> / Loại sản phẩm</>}
        title="Loại sản phẩm"
        subtitle="Nhóm sản phẩm để lọc nội bộ và hiển thị trên cổng B2C"
        actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => setEditing('new')}>Thêm loại</Button>}
      />
      <Card flush>
        <DataTable columns={columns} rows={data && toTree(data)} rowKey={(c) => c.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có loại sản phẩm" emptyAction={<Button onClick={() => setEditing('new')}>Thêm loại đầu tiên</Button>} />
      </Card>
      <CategoryFormModal open={!!editing} category={editing && editing !== 'new' ? editing : undefined} onClose={() => setEditing(null)} />
    </>
  );
}
