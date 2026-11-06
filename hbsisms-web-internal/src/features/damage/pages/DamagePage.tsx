import { useMemo, useState } from 'react';
import { Plus } from 'lucide-react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useLots } from '@/features/inventory';
import type { LotStockRow } from '@/features/inventory';
import { useAction } from '@/shared/api/useAction';
import { approvalStatusMeta } from '@/shared/domain/enums';
import { dateTime, num } from '@/shared/lib/format';
import { Button, Card, DataTable, Field, FilterBar, FilterSelect, Input, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, enumOptions, useListParams, type Column } from '@/shared/ui';
import { damageApi } from '../api';
import type { DamageListItem } from '../types';

function damageableQty(l: LotStockRow) {
  return l.available + l.quarantine + l.expired;
}

export default function DamagePage() {
  const list = useListParams(['status'] as const);
  const query = { q: list.q, page: list.page, status: list.filters.status };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['damage', query], queryFn: ({ signal }) => damageApi.list(query, signal), placeholderData: keepPreviousData });
  const [open, setOpen] = useState(false);
  const lots = useLots({ pageSize: 500 }, open);
  const [variantId, setVariantId] = useState('');
  const [lotId, setLotId] = useState('');
  const [stockId, setStockId] = useState('');
  const [qty, setQty] = useState('');
  const [reason, setReason] = useState('');
  const [touched, setTouched] = useState(false);

  const rows = useMemo(
    () => (lots.data?.items ?? []).filter((l) => damageableQty(l) > 0),
    [lots.data?.items],
  );

  const products = useMemo(() => {
    const map = new Map<number, { variantId: number; label: string }>();
    for (const l of rows) {
      if (!map.has(l.variantId)) {
        map.set(l.variantId, { variantId: l.variantId, label: `${l.productName} · ${l.sku}` });
      }
    }
    return [...map.values()].sort((a, b) => a.label.localeCompare(b.label, 'vi'));
  }, [rows]);

  const lotOptions = useMemo(() => {
    if (!variantId) return [];
    const map = new Map<number, { lotId: number; lotCode: string }>();
    for (const l of rows) {
      if (l.variantId !== Number(variantId) || map.has(l.lotId)) continue;
      map.set(l.lotId, { lotId: l.lotId, lotCode: l.lotCode });
    }
    return [...map.values()].sort((a, b) => a.lotCode.localeCompare(b.lotCode, 'vi'));
  }, [rows, variantId]);

  const locationOptions = useMemo(() => {
    if (!variantId || !lotId) return [];
    return rows
      .filter((l) => l.variantId === Number(variantId) && l.lotId === Number(lotId))
      .sort((a, b) => a.locationCode.localeCompare(b.locationCode, 'vi'));
  }, [rows, variantId, lotId]);

  const selected = rows.find((l) => l.stockId === Number(stockId));
  const maxQty = selected ? damageableQty(selected) : 0;
  const qtyValid = Number.isInteger(Number(qty)) && Number(qty) > 0 && Number(qty) <= maxQty;

  const resetForm = () => {
    setVariantId('');
    setLotId('');
    setStockId('');
    setQty('');
    setReason('');
    setTouched(false);
  };

  const create = useAction(
    () =>
      damageApi.create({
        variantId: selected!.variantId,
        lotId: selected!.lotId,
        locationId: selected!.locationId,
        qty: Number(qty),
        reason: reason.trim(),
        note: null,
      }),
    {
      success: (d) => `Đã gửi ${d.code} để Chủ cửa hàng duyệt`,
      onDone: () => setOpen(false),
    },
  );

  const columns: Column<DamageListItem>[] = [
    { key: 'code', header: 'Mã', cell: (d) => <span className="num">{d.code}</span> },
    { key: 'product', header: 'Sản phẩm', cell: (d) => <div><div className="font-medium">{d.productName}</div><div className="num text-ink-faint">{d.sku}</div></div> },
    { key: 'lot', header: 'Lô · vị trí', cell: (d) => <span className="num whitespace-nowrap">{d.lotCode ?? '—'} · {d.locationCode ?? '—'}</span> },
    { key: 'qty', header: 'Số khay', align: 'right', cell: (d) => <span className="num">{num(d.qty)}</span> },
    { key: 'reason', header: 'Lý do', cell: (d) => d.reason ?? '—' },
    { key: 'by', header: 'Người báo', hideOnMobile: true, cell: (d) => d.reporterName ?? '—' },
    { key: 'at', header: 'Thời gian', hideOnMobile: true, cell: (d) => <span className="whitespace-nowrap">{dateTime(d.reportedAt)}</span> },
    { key: 'status', header: 'Trạng thái', cell: (d) => <StatusChip meta={approvalStatusMeta} value={d.status} /> },
  ];

  return (
    <>
      <PageHeader
        title="Báo hỏng"
        subtitle="Tồn kho chỉ bị trừ sau khi Chủ cửa hàng duyệt"
        actions={
          <Button
            variant="primary"
            icon={<Plus size={15} />}
            onClick={() => {
              resetForm();
              setOpen(true);
            }}
          >
            Tạo báo hỏng
          </Button>
        }
      />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã, SKU, lô..." />
          <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(approvalStatusMeta)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(d) => d.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có báo hỏng" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <Modal
        open={open}
        title="Tạo báo hỏng"
        onClose={() => setOpen(false)}
        onSubmit={() => {
          setTouched(true);
          if (selected && qtyValid && reason.trim()) create.mutate();
        }}
        footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Gửi duyệt</Button></>}
      >
        <div className="space-y-3">
          <Field label="Sản phẩm" required error={touched && !variantId ? 'Vui lòng chọn sản phẩm' : undefined}>
            <Select
              value={variantId}
              onChange={(e) => {
                setVariantId(e.target.value);
                setLotId('');
                setStockId('');
              }}
            >
              <option value="">Chọn...</option>
              {products.map((p) => (
                <option key={p.variantId} value={p.variantId}>{p.label}</option>
              ))}
            </Select>
          </Field>
          <Field label="Lô" required error={touched && !lotId ? 'Vui lòng chọn lô' : undefined}>
            <Select
              value={lotId}
              disabled={!variantId}
              onChange={(e) => {
                const nextLot = e.target.value;
                setLotId(nextLot);
                const locs = rows.filter((l) => l.variantId === Number(variantId) && l.lotId === Number(nextLot));
                setStockId(locs.length === 1 ? String(locs[0].stockId) : '');
              }}
            >
              <option value="">{variantId ? 'Chọn...' : 'Chọn sản phẩm trước'}</option>
              {lotOptions.map((l) => (
                <option key={l.lotId} value={l.lotId}>{l.lotCode}</option>
              ))}
            </Select>
          </Field>
          <Field
            label="Vị trí"
            required
            error={touched && !selected ? 'Vui lòng chọn vị trí' : undefined}
            hint="Khu và kho đã gắn sẵn theo vị trí — không chọn lại"
          >
            <Select
              value={stockId}
              disabled={!lotId}
              onChange={(e) => setStockId(e.target.value)}
            >
              <option value="">{lotId ? 'Chọn...' : 'Chọn lô trước'}</option>
              {locationOptions.map((l) => (
                <option key={l.stockId} value={l.stockId}>
                  {l.locationCode} · còn {damageableQty(l)} khay
                </option>
              ))}
            </Select>
          </Field>
          <Field label="Số khay hỏng" required hint={selected ? `Tối đa ${maxQty} khay` : undefined} error={touched && !qtyValid ? `Nhập số nguyên từ 1 đến ${maxQty}` : undefined}>
            <Input type="number" min={1} max={maxQty || undefined} value={qty} onChange={(e) => setQty(e.target.value)} disabled={!selected} />
          </Field>
          <Field label="Lý do" required error={touched && !reason.trim() ? 'Vui lòng nhập lý do' : undefined}>
            <Input value={reason} onChange={(e) => setReason(e.target.value)} />
          </Field>
        </div>
      </Modal>
    </>
  );
}
