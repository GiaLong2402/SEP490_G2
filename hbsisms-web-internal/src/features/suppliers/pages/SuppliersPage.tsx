import { useState } from 'react';
import { Pencil, Plus } from 'lucide-react';
import { Link } from 'react-router-dom';
import { date, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, FilterBar, FilterSelect, IconButton, PageHeader, Pagination, SearchBox, useListParams, type Column } from '@/shared/ui';
import { SupplierFormModal } from '../components/SupplierFormModal';
import { useSuppliers } from '../hooks';
import type { SupplierListItem } from '../types';

export default function SuppliersPage() {
  const list = useListParams(['status'] as const);
  const { data, isLoading, error, refetch } = useSuppliers({ q: list.q, page: list.page, status: list.filters.status });
  const [form, setForm] = useState<{ supplier?: SupplierListItem } | null>(null);

  const columns: Column<SupplierListItem>[] = [
    { key: 'code', header: 'Mã', cell: (s) => <span className="num">{s.code}</span> },
    { key: 'name', header: 'Nhà cung cấp', cell: (s) => <div className="min-w-[180px]"><div className="font-medium">{s.name}</div>{s.address && <div className="text-ink-muted">{s.address}</div>}</div> },
    { key: 'contact', header: 'Liên hệ', cell: (s) => <div className="whitespace-nowrap">{s.contactName ?? '—'}{s.phone && <div className="text-ink-faint">{s.phone}</div>}</div> },
    { key: 'tax', header: 'MST', hideOnMobile: true, cell: (s) => <span className="num">{s.taxCode ?? '—'}</span> },
    { key: 'receipts', header: 'Phiếu nhập', align: 'right', cell: (s) => <Link to={`/receipts?q=${encodeURIComponent(s.name)}`} className="num text-primary hover:underline">{num(s.receiptCount)}</Link> },
    { key: 'last', header: 'Nhập gần nhất', hideOnMobile: true, cell: (s) => date(s.lastReceiptAt) },
    { key: 'status', header: 'Trạng thái', cell: (s) => <Chip tone={s.isActive ? 'green' : 'neutral'}>{s.isActive ? 'Đang giao dịch' : 'Ngừng giao dịch'}</Chip> },
    { key: 'edit', header: '', align: 'right', cell: (s) => <IconButton label={`Sửa ${s.name}`} onClick={() => setForm({ supplier: s })}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader title="Nhà cung cấp" subtitle="Bắt buộc chọn khi tạo phiếu nhập kho" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => setForm({})}>Thêm nhà cung cấp</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm tên, mã, MST, SĐT..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>
            <option value="active">Đang giao dịch</option>
            <option value="inactive">Ngừng giao dịch</option>
          </FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(s) => s.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có nhà cung cấp phù hợp" emptyAction={<Button onClick={() => setForm({})}>Thêm nhà cung cấp</Button>} />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <SupplierFormModal open={!!form} supplier={form?.supplier} onClose={() => setForm(null)} />
    </>
  );
}
