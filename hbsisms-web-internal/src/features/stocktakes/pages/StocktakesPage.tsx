import { useState } from 'react';
import { Plus } from 'lucide-react';
import { Link, useNavigate } from 'react-router-dom';
import { useLocations } from '@/features/inventory';
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
  const zones = [...new Set(locations.map((l) => l.zone).filter(Boolean))] as string[];
  const [open, setOpen] = useState(false);
  const [zone, setZone] = useState('');
  const [note, setNote] = useState('');
  const create = useAction(() => stocktakesApi.create({ zone: zone || null, note: note.trim() || null }), { success: (s) => `Đã tạo phiếu ${s.code}`, onDone: (s) => navigate(`/stocktakes/${s.id}`) });

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

  return (
    <>
      <PageHeader title="Kiểm kê" subtitle="Đối chiếu số đếm thực tế với tồn hệ thống" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => { setZone(''); setNote(''); setOpen(true); }}>Tạo phiếu kiểm kê</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã phiếu, phạm vi..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(stocktakeStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(s) => s.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu kiểm kê" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <Modal open={open} title="Tạo phiếu kiểm kê" onClose={() => setOpen(false)} onSubmit={() => create.mutate()} footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Tạo phiếu</Button></>}>
        <div className="space-y-3">
          <Field label="Phạm vi" hint="Hệ thống tự lấy các lô đang có hàng trong phạm vi này">
            <Select value={zone} onChange={(e) => setZone(e.target.value)}>
              <option value="">Toàn kho</option>
              {zones.map((z) => <option key={z} value={z}>{z}</option>)}
            </Select>
          </Field>
          <Field label="Ghi chú"><Input value={note} maxLength={2000} onChange={(e) => setNote(e.target.value)} /></Field>
        </div>
      </Modal>
    </>
  );
}
