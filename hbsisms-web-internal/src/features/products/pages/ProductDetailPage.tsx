import { useState } from 'react';
import { Pencil, Plus } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { isOwnerOrAdmin, useAuth } from '@/features/auth';
import { money, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, ErrorState, IconButton, InfoList, PageHeader, PageLoading, type Column } from '@/shared/ui';
import { BomFormModal } from '../components/BomFormModal';
import { ProductFormModal } from '../components/ProductFormModal';
import { VariantFormModal } from '../components/VariantFormModal';
import { useProduct, useProductBom } from '../hooks';
import type { BomLine, ProductLotRow, VariantWithTiers } from '../types';

function ExpiryChip({ daysLeft }: { daysLeft: number | null }) {
  if (daysLeft === null) return <Chip>Không có HSD</Chip>;
  if (daysLeft < 0) return <Chip tone="red">Quá hạn {-daysLeft} ngày</Chip>;
  if (daysLeft === 0) return <Chip tone="red">Hết hạn hôm nay</Chip>;
  if (daysLeft <= 5) return <Chip tone="amber">Còn {daysLeft} ngày</Chip>;
  return <Chip tone="green">Còn {daysLeft} ngày</Chip>;
}

export default function ProductDetailPage() {
  const id = Number(useParams().id);
  const { user } = useAuth();
  const canEditCatalog = isOwnerOrAdmin(user?.roleCode);
  const { data: product, isLoading, error, refetch } = useProduct(id);
  const bomQuery = useProductBom(id, Number.isFinite(id) && id > 0);
  const [editing, setEditing] = useState(false);
  const [variantModal, setVariantModal] = useState<{ variant?: VariantWithTiers } | null>(null);
  const [bomOpen, setBomOpen] = useState(false);

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
    ...(canEditCatalog
      ? [{ key: 'edit', header: '', align: 'right' as const, cell: (v: VariantWithTiers) => <IconButton label={`Sửa ${v.sku}`} onClick={() => setVariantModal({ variant: v })}><Pencil size={14} /></IconButton> }]
      : []),
  ];

  const bomLines = bomQuery.data?.lines ?? [];
  const bomColumns: Column<BomLine>[] = [
    {
      key: 'material',
      header: 'Nguyên liệu',
      cell: (line) =>
        line.rawMaterialName ? (
          <span>
            {line.rawMaterialName} <span className="num text-ink-faint">({line.rawMaterialCode})</span>
          </span>
        ) : line.componentProductName ? (
          <span>
            {line.componentProductName} <span className="num text-ink-faint">({line.componentProductCode})</span>
          </span>
        ) : (
          '—'
        ),
    },
    { key: 'qty', header: 'Định mức', align: 'right', cell: (line) => <span className="num">{num(line.qty)}</span> },
    { key: 'unit', header: 'ĐVT', cell: (line) => line.unitCode },
    {
      key: 'waste',
      header: '% hao hụt',
      align: 'right',
      cell: (line) => <span className="num">{line.wastePercent != null ? num(line.wastePercent) : '—'}</span>,
    },
  ];

  const lotColumns: Column<ProductLotRow>[] = [
    { key: 'lot', header: 'Mã lô', cell: (l) => <span className="num">{l.lotCode}</span> },
    { key: 'sku', header: 'SKU', cell: (l) => <span className="num">{l.sku}</span> },
    { key: 'mfg', header: 'NSX', hideOnMobile: true, cell: (l) => <span className="num">{l.mfgDate ?? '—'}</span> },
    { key: 'exp', header: 'HSD', cell: (l) => <span className="num">{l.expDate ?? '—'}</span> },
    { key: 'left', header: 'Thời hạn', cell: (l) => <ExpiryChip daysLeft={l.daysLeft} /> },
    { key: 'avail', header: 'Khả dụng', align: 'right', cell: (l) => <span className="num">{num(l.available)}</span> },
  ];

  const lots = product.lots ?? [];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/products" className="hover:underline">Sản phẩm</Link> / {product.code}</>}
        title={product.name}
        actions={canEditCatalog ? <Button icon={<Pencil size={14} />} onClick={() => setEditing(true)}>Sửa sản phẩm</Button> : undefined}
      />
      <div className="grid items-start gap-4 lg:grid-cols-[1fr_320px]">
        <div className="space-y-4">
          <Card flush title="Quy cách và bậc giá" actions={canEditCatalog ? <Button icon={<Plus size={14} />} onClick={() => setVariantModal({})}>Thêm quy cách</Button> : undefined}>
            <DataTable columns={columns} rows={product.variants} rowKey={(v) => v.id} emptyText="Sản phẩm chưa có quy cách" />
          </Card>
          <Card flush title="Lô hàng (chỉ xem)">
            <DataTable columns={lotColumns} rows={lots} rowKey={(l) => l.lotId} emptyText="Chưa có tồn theo lô" />
          </Card>
          <Card
            flush
            title="Định mức NVL (BOM)"
            actions={canEditCatalog ? <Button icon={<Pencil size={14} />} onClick={() => setBomOpen(true)}>{bomLines.length ? 'Sửa định mức' : 'Thiết lập BOM'}</Button> : undefined}
          >
            {bomQuery.isLoading ? (
              <p className="p-4 text-small text-ink-faint">Đang tải định mức…</p>
            ) : bomQuery.error ? (
              <div className="p-4">
                <ErrorState error={bomQuery.error} onRetry={bomQuery.refetch} />
              </div>
            ) : (
              <DataTable columns={bomColumns} rows={bomLines} rowKey={(line) => line.id} emptyText="Chưa có định mức nguyên liệu" />
            )}
          </Card>
        </div>
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
              ['Tổng tồn khả dụng', <span className="num">{num(product.totalStockQty ?? 0)}</span>],
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
      <BomFormModal
        open={bomOpen}
        productId={product.id}
        productCode={product.code}
        bom={bomQuery.data}
        onClose={() => setBomOpen(false)}
      />
    </>
  );
}
