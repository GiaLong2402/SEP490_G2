import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, DataTable, ErrorState, IconButton, Meter, PageHeader, PageLoading, type Column } from '@/shared/ui';
import { warehousesApi } from '../api';
import { LocationFormModal } from '../components/LocationFormModal';
import { WarehouseFormModal } from '../components/WarehouseFormModal';
import type { WarehouseDetail, WarehouseLocation } from '../types';

export default function WarehousesPage() {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const [warehouseForm, setWarehouseForm] = useState<{ warehouse?: WarehouseDetail } | null>(null);
  const [locationForm, setLocationForm] = useState<{ warehouse: WarehouseDetail; location?: WarehouseLocation } | null>(null);
  const [deleting, setDeleting] = useState<WarehouseLocation | null>(null);
  const remove = useAction(() => warehousesApi.deleteLocation(deleting!.id), { success: 'Đã xóa vị trí', onDone: () => setDeleting(null) });

  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;

  const columnsFor = (warehouse: WarehouseDetail): Column<WarehouseLocation>[] => [
    { key: 'code', header: 'Mã vị trí', cell: (l) => <span className="num font-medium">{l.code}</span> },
    { key: 'zone', header: 'Khu', cell: (l) => l.zone ?? '—' },
    { key: 'used', header: 'Đang chứa', align: 'right', cell: (l) => <span className="num">{num(l.usedQty)}</span> },
    { key: 'capacity', header: 'Sức chứa', align: 'right', cell: (l) => <span className="num">{l.capacity === null ? 'Không giới hạn' : num(l.capacity)}</span> },
    { key: 'ratio', header: 'Lấp đầy', width: '160px', hideOnMobile: true, cell: (l) => (l.capacity === null ? '—' : <Meter value={l.usedQty} max={l.capacity} label={`Lấp đầy ${l.code}`} />) },
    { key: 'note', header: 'Ghi chú', hideOnMobile: true, cell: (l) => l.note ?? '—' },
    {
      key: 'actions', header: '', align: 'right',
      cell: (l) => (
        <div className="flex justify-end">
          <IconButton label={`Sửa ${l.code}`} onClick={() => setLocationForm({ warehouse, location: l })}><Pencil size={14} /></IconButton>
          <IconButton label={l.canDelete ? `Xóa ${l.code}` : `Không thể xóa ${l.code} vì đã có dữ liệu tồn kho`} disabled={!l.canDelete} onClick={() => setDeleting(l)}><Trash2 size={14} /></IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader title="Kho và vị trí" subtitle="Vị trí dùng khi nhập kho, chuyển hàng và kiểm kê" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => setWarehouseForm({})}>Thêm kho</Button>} />
      <div className="space-y-4">
        {data.map((warehouse) => (
          <Card
            key={warehouse.id}
            flush
            title={<span className="flex flex-wrap items-center gap-2"><span>{warehouse.name}</span><span className="num font-normal text-ink-faint">{warehouse.code}</span>{!warehouse.isActive && <Chip>Ngừng dùng</Chip>}</span>}
            actions={
              <>
                <Button icon={<Pencil size={14} />} onClick={() => setWarehouseForm({ warehouse })}>Sửa kho</Button>
                <Button icon={<Plus size={14} />} onClick={() => setLocationForm({ warehouse })}>Thêm vị trí</Button>
              </>
            }
          >
            {warehouse.address && <p className="border-b border-line px-4 py-2 text-ink-muted">{warehouse.address}</p>}
            <DataTable columns={columnsFor(warehouse)} rows={warehouse.locations} rowKey={(l) => l.id} emptyText="Kho chưa có vị trí nào" />
          </Card>
        ))}
      </div>
      <WarehouseFormModal open={!!warehouseForm} warehouse={warehouseForm?.warehouse} onClose={() => setWarehouseForm(null)} />
      <LocationFormModal open={!!locationForm} warehouse={locationForm?.warehouse} location={locationForm?.location} onClose={() => setLocationForm(null)} />
      <ConfirmDialog open={!!deleting} danger title={`Xóa vị trí ${deleting?.code ?? ''}?`} message="Thao tác này không thể hoàn tác." confirmLabel="Xóa vị trí" loading={remove.isPending} onConfirm={() => remove.mutate()} onClose={() => setDeleting(null)} />
    </>
  );
}
