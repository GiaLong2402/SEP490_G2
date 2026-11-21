import { useState } from 'react';
import { Pencil, Plus } from 'lucide-react';
import { Button, Card, Chip, DataTable, IconButton, PageHeader, type Column } from '@/shared/ui';
import { UnitFormModal } from '../components/UnitFormModal';
import { useUnits } from '../hooks';
import type { UnitOfMeasure } from '../types';

export default function UnitsPage() {
  const { data, isLoading, error, refetch } = useUnits();
  const [editing, setEditing] = useState<UnitOfMeasure | 'new' | null>(null);

  const columns: Column<UnitOfMeasure>[] = [
    { key: 'code', header: 'Mã', width: '140px', cell: (u) => <span className="num whitespace-nowrap font-medium">{u.code}</span> },
    { key: 'name', header: 'Tên đơn vị', cell: (u) => u.name },
    {
      key: 'status',
      header: 'Trạng thái',
      width: '120px',
      cell: (u) => <Chip tone={u.isActive ? 'green' : 'neutral'}>{u.isActive ? 'Đang dùng' : 'Ngừng dùng'}</Chip>,
    },
    {
      key: 'edit',
      header: '',
      width: '48px',
      align: 'right',
      cell: (u) => (
        <IconButton label={`Sửa ${u.name}`} onClick={() => setEditing(u)}>
          <Pencil size={14} />
        </IconButton>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Đơn vị tính"
        subtitle="Danh mục đơn vị dùng cho sản phẩm và nguyên liệu (kg, cái, cuộn...)"
        actions={
          <Button variant="primary" icon={<Plus size={15} />} onClick={() => setEditing('new')}>
            Thêm đơn vị
          </Button>
        }
      />
      <Card flush>
        <DataTable
          columns={columns}
          rows={data}
          rowKey={(u) => u.id}
          loading={isLoading}
          error={error}
          onRetry={refetch}
          emptyText="Chưa có đơn vị tính"
          emptyAction={<Button onClick={() => setEditing('new')}>Thêm đơn vị đầu tiên</Button>}
        />
      </Card>
      <UnitFormModal open={!!editing} unit={editing && editing !== 'new' ? editing : undefined} onClose={() => setEditing(null)} />
    </>
  );
}
