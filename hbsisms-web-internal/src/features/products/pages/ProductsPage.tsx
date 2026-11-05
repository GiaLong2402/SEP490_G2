import { useState } from 'react';
import { Plus, Tags } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { money, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, FilterBar, LinkButton, PageHeader, Pagination, SearchBox, useListParams, type Column } from '@/shared/ui';
import { CategoryFilterMenu } from '../components/CategoryFilterMenu';
import { ProductFormModal } from '../components/ProductFormModal';
import { useCategories, useProducts } from '../hooks';
import type { ProductListItem } from '../types';

export default function ProductsPage() {
  const list = useListParams(['categoryId'] as const);
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const categories = useCategories();
  const { data, isLoading, error, refetch } = useProducts({ q: list.q, page: list.page, categoryId: list.filters.categoryId });

  const columns: Column<ProductListItem>[] = [
    { key: 'code', header: 'Mã', cell: (p) => <Link to={`/products/${p.id}`} className="num text-primary hover:underline">{p.code}</Link> },
    { key: 'name', header: 'Tên sản phẩm', cell: (p) => <span className="font-medium">{p.name}</span> },
    { key: 'category', header: 'Loại', hideOnMobile: true, cell: (p) => p.categoryName ?? '—' },
    { key: 'variants', header: 'Quy cách', align: 'right', cell: (p) => <span className="num">{p.variants.length}</span> },
    { key: 'stock', header: 'Khả dụng (khay)', align: 'right', cell: (p) => <span className="num">{num(p.variants.reduce((s, v) => s + v.available, 0))}</span> },
    { key: 'status', header: 'Trạng thái', cell: (p) => (p.isActive ? <Chip tone={p.isPublished ? 'green' : 'neutral'}>{p.isPublished ? 'Đang bán online' : 'Chỉ nội bộ'}</Chip> : <Chip>Ngừng bán</Chip>) },
  ];

  return (
    <>
      <PageHeader title="Sản phẩm" subtitle="Loại sản phẩm, quy cách đóng khay và bậc giá" actions={<><LinkButton to="/categories" icon={<Tags size={15} />}>Quản lý loại</LinkButton><Button variant="primary" icon={<Plus size={15} />} onClick={() => setCreating(true)}>Thêm sản phẩm</Button></>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm tên, mã, SKU..." />
          <CategoryFilterMenu
            categories={categories.data ?? []}
            value={list.filters.categoryId}
            onChange={(v) => list.setFilter('categoryId', v)}
          />
        </FilterBar>
        <DataTable
          columns={columns}
          rows={data?.items}
          rowKey={(p) => p.id}
          loading={isLoading}
          error={error}
          onRetry={refetch}
          emptyText="Không có sản phẩm phù hợp"
          expandable={(p) => (
            <table className="w-full text-small">
              <thead>
                <tr className="text-left text-micro font-semibold uppercase text-ink-muted">
                  <th className="py-1">SKU</th><th>Quy cách</th><th className="text-right">Giá niêm yết</th><th className="text-right">Khay / thùng</th><th>Bậc số lượng</th><th className="text-right">Khả dụng</th>
                </tr>
              </thead>
              <tbody>
                {p.variants.map((v) => (
                  <tr key={v.id} className="border-t border-line">
                    <td className="num py-1.5">{v.sku}</td>
                    <td>{v.packLabel}</td>
                    <td className="num text-right">{money(v.listPrice)}</td>
                    <td className="num text-right">{v.packPerCase}</td>
                    <td className="text-ink-muted">{v.tiers.length ? v.tiers.map((t) => `≥${t.minQtyKhay}: ${num(t.unitPrice)}`).join(' · ') : '—'}</td>
                    <td className="num text-right">{num(v.available)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <ProductFormModal open={creating} onClose={() => setCreating(false)} onSaved={(id) => navigate(`/products/${id}`)} />
    </>
  );
}
