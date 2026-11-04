import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { customerTypeMeta } from '@/shared/domain/enums';
import { money, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, FilterBar, FilterSelect, Meter, PageHeader, Pagination, SearchBox, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { CustomerFormModal } from '../components/CustomerFormModal';
import { useCustomers } from '../hooks';
import type { CustomerListItem } from '../types';

export default function CustomersPage() {
  const list = useListParams(['type', 'active'] as const);
  const navigate = useNavigate();
  const [creating, setCreating] = useState(false);
  const isActiveFilter =
    list.filters.active === 'true' ? true : list.filters.active === 'false' ? false : undefined;
  const { data, isLoading, error, refetch } = useCustomers({
    q: list.q,
    page: list.page,
    type: list.filters.type,
    isActive: isActiveFilter,
  });

  const columns: Column<CustomerListItem>[] = [
    { key: 'code', header: 'Mã', cell: (c) => <Link to={`/customers/${c.id}`} className="num text-primary hover:underline">{c.code}</Link> },
    { key: 'name', header: 'Tên khách', cell: (c) => <span className="font-medium">{c.name}</span> },
    { key: 'type', header: 'Loại', cell: (c) => <StatusChip meta={customerTypeMeta} value={c.customerType} /> },
    { key: 'phone', header: 'Điện thoại', hideOnMobile: true, cell: (c) => c.phone ?? '—' },
    { key: 'orders', header: 'Số đơn', align: 'right', hideOnMobile: true, cell: (c) => <span className="num">{num(c.orderCount)}</span> },
    { key: 'debt', header: 'Công nợ', align: 'right', cell: (c) => (c.customerType === 'b2b' ? <span className="num">{money(c.currentDebt)}</span> : '—') },
    { key: 'limit', header: 'Sử dụng hạn mức', width: '160px', hideOnMobile: true, cell: (c) => (c.customerType === 'b2b' ? <Meter value={c.currentDebt} max={c.creditLimit} /> : '—') },
    { key: 'active', header: 'Trạng thái', cell: (c) => <Chip tone={c.isActive ? 'green' : 'neutral'}>{c.isActive ? 'Hoạt động' : 'Ngừng'}</Chip> },
  ];

  return (
    <>
      <PageHeader title="Khách hàng" subtitle="Khách lẻ và doanh nghiệp" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => setCreating(true)}>Thêm khách hàng</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm tên, mã, SĐT, MST..." />
          <FilterSelect label="Loại" value={list.filters.type} onChange={(v) => list.setFilter('type', v)}>{enumOptions(customerTypeMeta)}</FilterSelect>
          <FilterSelect label="Trạng thái" value={list.filters.active} onChange={(v) => list.setFilter('active', v)}>
            <option value="true">Hoạt động</option>
            <option value="false">Ngừng</option>
          </FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(c) => c.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có khách hàng phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <CustomerFormModal open={creating} onClose={() => setCreating(false)} onSaved={(id) => navigate(`/customers/${id}`)} />
    </>
  );
}
