import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { ChevronDown, MapPin, Pencil, Plus, Trash2, Warehouse } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { num } from '@/shared/lib/format';
import { Button, Chip, ConfirmDialog, DataTable, ErrorState, IconButton, Meter, PageHeader, PageLoading, type Column } from '@/shared/ui';
import { warehousesApi } from '../api';
import { LocationFormModal } from '../components/LocationFormModal';
import { WarehouseFormModal } from '../components/WarehouseFormModal';
import type { WarehouseDetail, WarehouseLocation } from '../types';

function CapacityStat({
  label,
  hint,
  value,
  max,
  meterLabel,
}: {
  label: string;
  hint: string;
  value: number;
  max: number;
  meterLabel: string;
}) {
  return (
    <div className="min-w-[200px] flex-1 rounded-md border border-line bg-canvas px-3 py-2.5">
      <div className="text-small font-medium text-ink">{label}</div>
      <p className="mt-0.5 text-micro text-ink-faint">{hint}</p>
      <div className="mt-2 flex items-baseline gap-1.5">
        <span className="num text-h2 text-ink">{num(value)}</span>
        <span className="text-small text-ink-muted">/ {num(max)} khay</span>
      </div>
      <div className="mt-2">
        <Meter value={value} max={max} label={meterLabel} />
      </div>
    </div>
  );
}

export default function WarehousesPage() {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [warehouseForm, setWarehouseForm] = useState<{ warehouse?: WarehouseDetail } | null>(null);
  const [locationForm, setLocationForm] = useState<{ warehouse: WarehouseDetail; location?: WarehouseLocation } | null>(null);
  const [deleting, setDeleting] = useState<WarehouseLocation | null>(null);
  const remove = useAction(() => warehousesApi.deleteLocation(deleting!.id), { success: 'Đã xóa vị trí', onDone: () => setDeleting(null) });

  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;

  const toggle = (id: number) => {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const columnsFor = (warehouse: WarehouseDetail): Column<WarehouseLocation>[] => [
    { key: 'code', header: 'Mã vị trí', cell: (l) => <span className="num font-medium">{l.code}</span> },
    { key: 'zone', header: 'Khu', cell: (l) => l.zone ?? '—' },
    { key: 'used', header: 'Đang chứa', align: 'right', cell: (l) => <span className="num">{num(l.usedQty)}</span> },
    { key: 'capacity', header: 'Sức chứa', align: 'right', cell: (l) => <span className="num">{num(l.capacity)}</span> },
    { key: 'ratio', header: 'Lấp đầy', width: '160px', hideOnMobile: true, cell: (l) => <Meter value={l.usedQty} max={l.capacity} label={`Lấp đầy ${l.code}`} /> },
    { key: 'note', header: 'Ghi chú', hideOnMobile: true, cell: (l) => l.note ?? '—' },
    {
      key: 'actions',
      header: '',
      align: 'right',
      cell: (l) => (
        <div className="flex justify-end">
          <IconButton label={`Sửa ${l.code}`} onClick={() => setLocationForm({ warehouse, location: l })}>
            <Pencil size={14} />
          </IconButton>
          <IconButton
            label={l.canDelete ? `Xóa ${l.code}` : `Không thể xóa ${l.code} vì đã có dữ liệu tồn kho`}
            disabled={!l.canDelete}
            onClick={() => setDeleting(l)}
          >
            <Trash2 size={14} />
          </IconButton>
        </div>
      ),
    },
  ];

  return (
    <>
      <PageHeader
        title="Kho và vị trí"
        subtitle="Bấm vào kho để xem vị trí. Sức chứa kho giới hạn tổng chỗ các vị trí và lượng hàng nhập vào."
        actions={
          <Button variant="primary" icon={<Plus size={15} />} onClick={() => setWarehouseForm({})}>
            Thêm kho
          </Button>
        }
      />

      {data.length === 0 ? (
        <div className="rounded-md border border-dashed border-line bg-card px-4 py-12 text-center text-ink-muted">
          Chưa có kho. Hãy thêm kho rồi tạo các vị trí bên trong.
        </div>
      ) : (
        <div className="space-y-3">
          {data.map((warehouse) => {
            const open = expandedIds.has(warehouse.id);
            const freeSlots = Math.max(0, warehouse.capacity - warehouse.allocatedCapacity);
            return (
              <section key={warehouse.id} className="overflow-hidden rounded-md border border-line bg-card shadow-card">
                <div className="flex items-stretch gap-0">
                  <button
                    type="button"
                    aria-expanded={open}
                    onClick={() => toggle(warehouse.id)}
                    className="flex min-w-0 flex-1 items-center gap-3 px-4 py-3 text-left transition-colors hover:bg-canvas"
                  >
                    <ChevronDown
                      size={18}
                      className={`shrink-0 text-ink-muted transition-transform ${open ? '' : '-rotate-90'}`}
                      aria-hidden
                    />
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary/10 text-primary">
                      <Warehouse size={18} aria-hidden />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-h2 text-ink">{warehouse.name}</span>
                        <span className="num text-small text-ink-faint">{warehouse.code}</span>
                        {!warehouse.isActive && <Chip>Ngừng dùng</Chip>}
                        <Chip tone="blue">{warehouse.locations.length} vị trí</Chip>
                      </div>
                      {warehouse.address && (
                        <p className="mt-1 flex items-center gap-1 text-small text-ink-muted">
                          <MapPin size={13} className="shrink-0" aria-hidden />
                          <span className="truncate">{warehouse.address}</span>
                        </p>
                      )}
                    </div>
                  </button>
                  <div className="flex shrink-0 items-center border-l border-line px-3 py-3">
                    <Button
                      icon={<Pencil size={14} />}
                      onClick={(e) => {
                        e.stopPropagation();
                        setWarehouseForm({ warehouse });
                      }}
                    >
                      Sửa kho
                    </Button>
                  </div>
                </div>

                {open && (
                  <div className="border-t border-line bg-canvas/40">
                    <div className="flex flex-col gap-2 border-b border-line px-4 py-3 sm:flex-row">
                      <CapacityStat
                        label="Hàng đang trong kho"
                        hint="Số khay thực tế đang nằm trong kho"
                        value={warehouse.totalQty}
                        max={warehouse.capacity}
                        meterLabel={`Hàng trong kho ${warehouse.code}`}
                      />
                      <CapacityStat
                        label="Chỗ đã chia cho vị trí"
                        hint={freeSlots > 0 ? `Còn ${num(freeSlots)} khay chưa gán vị trí` : 'Đã chia hết sức chứa kho cho các vị trí'}
                        value={warehouse.allocatedCapacity}
                        max={warehouse.capacity}
                        meterLabel={`Phân bổ vị trí ${warehouse.code}`}
                      />
                    </div>
                    <div className="flex flex-wrap items-center justify-between gap-2 border-b border-line px-4 py-2">
                      <p className="text-small text-ink-muted">
                        Vị trí trong <span className="num font-medium text-ink">{warehouse.code}</span>
                        {warehouse.locations.length === 0
                          ? ' — chưa có vị trí, hãy thêm để nhập hàng'
                          : ` · ${warehouse.locations.length} ô`}
                      </p>
                      <Button
                        icon={<Plus size={14} />}
                        onClick={() => setLocationForm({ warehouse })}
                      >
                        Thêm vị trí
                      </Button>
                    </div>
                    <DataTable
                      columns={columnsFor(warehouse)}
                      rows={warehouse.locations}
                      rowKey={(l) => l.id}
                      emptyText="Kho chưa có vị trí nào"
                    />
                  </div>
                )}
              </section>
            );
          })}
        </div>
      )}

      <WarehouseFormModal open={!!warehouseForm} warehouse={warehouseForm?.warehouse} onClose={() => setWarehouseForm(null)} />
      <LocationFormModal open={!!locationForm} warehouse={locationForm?.warehouse} location={locationForm?.location} onClose={() => setLocationForm(null)} />
      <ConfirmDialog
        open={!!deleting}
        danger
        title={`Xóa vị trí ${deleting?.code ?? ''}?`}
        message="Thao tác này không thể hoàn tác."
        confirmLabel="Xóa vị trí"
        loading={remove.isPending}
        onConfirm={() => remove.mutate()}
        onClose={() => setDeleting(null)}
      />
    </>
  );
}
