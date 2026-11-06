import { useState } from 'react';
import { Pencil, Plus } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { money, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, ErrorState, IconButton, InfoList, PageHeader, PageLoading, type Column } from '@/shared/ui';
import { ProductFormModal } from '../components/ProductFormModal';
import { VariantFormModal } from '../components/VariantFormModal';
import { useProduct } from '../hooks';
import type { VariantWithTiers } from '../types';

export default function ProductDetailPage() {
  const id = Number(useParams().id);
  const { data: product, isLoading, error, refetch } = useProduct(id);
  const [editing, setEditing] = useState(false);
  const [variantModal, setVariantModal] = useState<{ variant?: VariantWithTiers } | null>(null);

  if (isLoading) return <PageLoading />;
  if (error || !product) return <ErrorState error={error} onRetry={refetch} />;

  const columns: Column<VariantWithTiers>[] = [
    { key: 'sku', header: 'SKU', cell: (v) => <span className="num">{v.sku}</span> },
    { key: 'pack', header: 'Quy cách', cell: (v) => v.packLabel },
    { key: 'price', header: 'Giá niêm yết', align: 'right', cell: (v) => <span className="num">{money(v.listPrice)}</span> },
    { key: 'case', header: 'Khay / thùng', align: 'right', cell: (v) => <span className="num">{v.packPerCase}</span> },
    { key: 'tiers', header: 'Bậc số lượng', hideOnMobile: true, cell: (v) => (v.tiers.length ? v.tiers.map((t) => `≥${t.minQtyKhay}: ${num(t.unitPrice)}`).join(' · ') : '—') },
    { key: 'avail', header: 'Khả dụng', align: 'right', cell: (v) => <span className="num">{num(v.available)}</span> },
    {
      key: 'active',
      header: 'TT quy cách',
      cell: (v) => {
        const selling = product.isActive && v.isActive;
        return <Chip tone={selling ? 'green' : 'neutral'}>{selling ? 'Đang bán' : 'Ngừng bán'}</Chip>;
      },
    },
    { key: 'edit', header: '', align: 'right', cell: (v) => <IconButton label={`Sửa ${v.sku}`} onClick={() => setVariantModal({ variant: v })}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/products" className="hover:underline">Sản phẩm</Link> / {product.code}</>}
        title={product.name}
        actions={<Button icon={<Pencil size={14} />} onClick={() => setEditing(true)}>Sửa sản phẩm</Button>}
      />
      <div className="grid items-start gap-4 lg:grid-cols-[1fr_320px]">
        <Card flush title="Quy cách và bậc giá" actions={<Button icon={<Plus size={14} />} onClick={() => setVariantModal({})}>Thêm quy cách</Button>}>
          <DataTable columns={columns} rows={product.variants} rowKey={(v) => v.id} emptyText="Sản phẩm chưa có quy cách" />
        </Card>
        <Card title="Thông tin">
          {product.imageUrl ? (
            <img
              src={product.imageUrl}
              alt={product.name}
              className="mb-3 h-40 w-full rounded-md border border-line object-cover"
            />
          ) : (
            <p className="mb-3 text-small text-ink-faint">Chưa có ảnh chính</p>
          )}
          {product.gallery && product.gallery.length > 0 ? (
            <div className="mb-3 flex flex-wrap gap-2">
              {product.gallery.map((url) => (
                <img
                  key={url}
                  src={url}
                  alt=""
                  className="h-16 w-16 rounded border border-line object-cover"
                />
              ))}
            </div>
          ) : null}
          <InfoList
            items={[
              ['Mã', <span className="num">{product.code}</span>],
              ['Loại', product.categoryName],
              ['Xuất xứ', product.origin],
              ['Cổng B2C', product.isPublished ? 'Đang hiển thị' : 'Ẩn'],
              ['Kinh doanh', product.isActive ? 'Đang bán' : 'Ngừng bán'],
              ['Gallery', product.gallery?.length ? `${product.gallery.length} ảnh` : '—'],
            ]}
          />
          {product.description && <p className="mt-3 border-t border-line pt-3 text-ink-muted">{product.description}</p>}
          {product.storageNote && <p className="mt-2 text-ink-muted">{product.storageNote}</p>}
        </Card>
      </div>
      <ProductFormModal open={editing} product={product} onClose={() => setEditing(false)} />
      <VariantFormModal
        open={!!variantModal}
        productId={product.id}
        productActive={product.isActive}
        variant={variantModal?.variant}
        onClose={() => setVariantModal(null)}
      />
    </>
  );
}
