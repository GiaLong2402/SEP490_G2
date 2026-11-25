import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useLocations } from '@/features/inventory';
import { warehousesApi } from '@/features/warehouses/api';
import { useAction } from '@/shared/api/useAction';
import { stocktakeStatusMeta } from '@/shared/domain/enums';
import { dateTime, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, Field, FilterBar, FilterSelect, Input, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { stocktakesApi } from '../api';
import { useStocktakes } from '../hooks';
import type { StocktakeListItem } from '../types';

export default function StocktakesPage() {
  const list = useListParams(['status'] as const);
  const navigate = useNavigate();
  const { data, isLoading, error, refetch } = useStocktakes({ q: list.q, page: list.page, status: list.filters.status });
  const locations = useLocations().data ?? [];
  const { data: warehouses = [] } = useQuery({ queryKey: ['warehouses'], queryFn: warehousesApi.list });
  // Kiểm kê thành phẩm: không hiện kho nguyên liệu (khu cất không chứa tồn theo lô).
  const activeWarehouses = useMemo(() => warehouses.filter((w) => w.isActive && w.kind !== 'raw'), [warehouses]);

  const [open, setOpen] = useState(false);
  const [warehouseId, setWarehouseId] = useState('');
  const [zone, setZone] = useState('');
  const [note, setNote] = useState('');

  const zonesForWarehouse = useMemo(() => {
    const whId = warehouseId ? Number(warehouseId) : null;
    const scoped = whId == null
      ? locations
      : locations.filter((l) => l.warehouseId === whId);
    const zoneNames = [...new Set(scoped.map((l) => l.zone).filter(Boolean))] as string[];
    return zoneNames.map((z) => {
      const bins = scoped.filter((l) => l.zone === z).map((l) => l.code);
      const hint = bins.length === 1 ? `${z} · ${bins[0]}` : z;
      return { value: z, label: hint };
    });
  }, [locations, warehouseId]);

  const create = useAction(
    () => stocktakesApi.create({
      warehouseId: warehouseId ? Number(warehouseId) : null,
      zone: zone || null,
      note: note.trim() || null,
    }),
    { success: (s) => `Đã tạo phiếu ${s.code}`, onDone: (s) => navigate(`/stocktakes/${s.id}`) },
  );

  const columns: Column<StocktakeListItem>[] = [
    { key: 'code', header: 'Mã phiếu', cell: (s) => <Link to={`/stocktakes/${s.id}`} className="num text-primary hover:underline">{s.code}</Link> },
    { key: 'scope', header: 'Phạm vi', cell: (s) => s.scope ?? '—' },
    { key: 'progress', header: 'Tiến độ', align: 'right', cell: (s) => <span className="num">{num(s.countedCount)} / {num(s.lineCount)}</span> },
    { key: 'diff', header: 'Chênh lệch', align: 'right', cell: (s) => <span className={`num ${s.diffTotal !== 0 ? 'font-semibold text-red-fg' : ''}`}>{s.diffTotal > 0 ? '+' : ''}{num(s.diffTotal)}</span> },
    { key: 'creator', header: 'Người tạo', hideOnMobile: true, cell: (s) => s.creatorName ?? '—' },
    { key: 'by', header: 'Người đếm', hideOnMobile: true, cell: (s) => s.counterName ?? '—' },
    { key: 'note', header: 'Ghi chú', hideOnMobile: true, cell: (s) => (s.note?.trim() ? s.note : '—') },
    { key: 'at', header: 'Bắt đầu', hideOnMobile: true, cell: (s) => dateTime(s.startedAt) },
    { key: 'status', header: 'Trạng thái', cell: (s) => (s.pendingApproval ? <Chip tone="amber">Chờ duyệt chênh lệch</Chip> : <StatusChip meta={stocktakeStatusMeta} value={s.status} />) },
  ];

  const openCreate = () => {
    const first = activeWarehouses[0];
    setWarehouseId(first ? String(first.id) : '');
    setZone('');
    setNote('');
    setOpen(true);
  };

  return (
    <>
      <PageHeader
        title="Kiểm kê"
        subtitle="Đối chiếu số đếm với tồn — chọn kho, rồi khu (zone) trong kho"
        actions={<Button variant="primary" icon={<Plus size={15} />} onClick={openCreate}>Tạo phiếu kiểm kê</Button>}
      />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã phiếu, phạm vi..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(stocktakeStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(s) => s.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu kiểm kê" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <Modal
        open={open}
        title="Tạo phiếu kiểm kê"
        onClose={() => setOpen(false)}
        onSubmit={() => create.mutate()}
        footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Tạo phiếu</Button></>}
      >
        <div className="space-y-3">
          <Field label="Kho" hint="Trong kho có vị trí (A-01, B-02…); zone là nhãn nhóm vị trí." required={activeWarehouses.length > 1}>
            <Select
              value={warehouseId}
              onChange={(e) => {
                setWarehouseId(e.target.value);
                setZone('');
              }}
            >
              {activeWarehouses.length === 0 && <option value="">Chưa có kho active</option>}
              {activeWarehouses.map((w) => (
                <option key={w.id} value={w.id}>{w.code} — {w.name}</option>
              ))}
            </Select>
          </Field>
          <Field label="Phạm vi khu (zone)" hint="Chọn zone trong kho đã chọn — hoặc để trống = toàn kho">
            <Select value={zone} onChange={(e) => setZone(e.target.value)} disabled={!warehouseId && activeWarehouses.length > 1}>
              <option value="">Toàn kho</option>
              {zonesForWarehouse.map((z) => <option key={z.value} value={z.value}>{z.label}</option>)}
            </Select>
          </Field>
          <Field label="Ghi chú"><Input value={note} maxLength={2000} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
      </Modal>
    </>
  );
}
