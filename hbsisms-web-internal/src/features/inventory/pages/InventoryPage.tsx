import { useState } from 'react';
import { ArrowRightLeft, PackagePlus, SlidersHorizontal } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { ledgerEntryMeta } from '@/shared/domain/enums';
import { date, dateTime, num } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, Field, FilterBar, FilterSelect, IconButton, Input, LinkButton, Modal, PageHeader, Pagination, SearchBox, Select, StatCard, StatGrid, StatusChip, Tabs, Textarea, enumOptions, useListParams, type Column } from '@/shared/ui';
import { inventoryApi } from '../api';
import { useInventorySummary, useLedger, useLocations, useLots, useNearExpiry, useVariantStock } from '../hooks';
import type { LedgerRow, LotStockRow, VariantStockRow } from '../types';

type Tab = 'variants' | 'lots' | 'expiry' | 'ledger';

function ExpiryChip({ daysLeft }: { daysLeft: number | null }) {
  if (daysLeft === null) return <Chip>Không có HSD</Chip>;
  if (daysLeft < 0) return <Chip tone="red">Quá hạn {-daysLeft} ngày</Chip>;
  if (daysLeft === 0) return <Chip tone="red">Hết hạn hôm nay</Chip>;
  if (daysLeft <= 5) return <Chip tone="amber">Còn {daysLeft} ngày</Chip>;
  return <Chip tone="green">Còn {daysLeft} ngày</Chip>;
}

function isAdjustQtyValid(qtyDelta: string, available: number) {
  if (!Number.isInteger(Number(qtyDelta)) || Number(qtyDelta) === 0) return false;
  const delta = Number(qtyDelta);
  if (delta < 0 && Math.abs(delta) > available) return false;
  return true;
}

export default function InventoryPage() {
  const [params, setParams] = useSearchParams();
  const tab = (params.get('tab') as Tab) ?? 'variants';
  const lowOnly = params.get('lowOnly') === '1' || params.get('lowOnly') === 'true';
  const list = useListParams(['locationId', 'entryType', 'from', 'to'] as const);
  const summary = useInventorySummary().data;
  const locations = useLocations().data ?? [];
  const query = { q: list.q, page: list.page, locationId: list.filters.locationId };
  const variants = useVariantStock({ ...query, lowOnly: lowOnly || undefined }, tab === 'variants');
  const lots = useLots(query, tab === 'lots');
  const expiry = useNearExpiry({ q: list.q, page: list.page }, tab === 'expiry');
  const ledger = useLedger(
    { q: list.q, page: list.page, entryType: list.filters.entryType, from: list.filters.from, to: list.filters.to },
    tab === 'ledger',
  );

  const [moving, setMoving] = useState<LotStockRow | null>(null);
  const [moveForm, setMoveForm] = useState({ toLocationId: '', qty: '' });
  const [moveTouched, setMoveTouched] = useState(false);
  const moveValid = moving && Number(moveForm.toLocationId) > 0 && Number.isInteger(Number(moveForm.qty)) && Number(moveForm.qty) > 0 && Number(moveForm.qty) <= moving.available;
  const move = useAction(() => inventoryApi.move({ stockId: moving!.stockId, toLocationId: Number(moveForm.toLocationId), qty: Number(moveForm.qty) }), { success: 'Đã chuyển vị trí', onDone: () => setMoving(null) });

  const [adjusting, setAdjusting] = useState<LotStockRow | null>(null);
  const [adjustForm, setAdjustForm] = useState({ qtyDelta: '', note: '' });
  const [adjustTouched, setAdjustTouched] = useState(false);
  const adjustValid = adjusting && isAdjustQtyValid(adjustForm.qtyDelta, adjusting.available) && adjustForm.note.trim().length > 0;
  const adjust = useAction(
    () => inventoryApi.adjust({ stockId: adjusting!.stockId, qtyDelta: Number(adjustForm.qtyDelta), note: adjustForm.note.trim() }),
    { success: 'Đã điều chỉnh tồn', onDone: () => setAdjusting(null) },
  );

  const openMove = (row: LotStockRow) => {
    setMoveForm({ toLocationId: '', qty: String(row.available) });
    setMoveTouched(false);
    setMoving(row);
  };

  const openAdjust = (row: LotStockRow) => {
    setAdjustForm({ qtyDelta: '', note: '' });
    setAdjustTouched(false);
    setAdjusting(row);
  };

  const setTab = (next: Tab) => {
    const nextParams: Record<string, string> = { tab: next };
    if (next === 'variants' && lowOnly) nextParams.lowOnly = '1';
    setParams(nextParams);
  };

  const lotActions = (l: LotStockRow) => (
    <span className="inline-flex items-center gap-1">
      <IconButton label={`Điều chỉnh tồn lô ${l.lotCode}`} onClick={() => openAdjust(l)}><SlidersHorizontal size={14} /></IconButton>
      {l.available > 0 && <IconButton label={`Chuyển vị trí lô ${l.lotCode}`} onClick={() => openMove(l)}><ArrowRightLeft size={14} /></IconButton>}
    </span>
  );

  const lotColumns: Column<LotStockRow>[] = [
    { key: 'lot', header: 'Mã lô', cell: (l) => <span className="num">{l.lotCode}</span> },
    { key: 'product', header: 'Sản phẩm', cell: (l) => <div><div className="font-medium">{l.productName}</div><div className="num text-ink-faint">{l.sku}</div></div> },
    { key: 'loc', header: 'Vị trí', cell: (l) => <span className="num">{l.locationCode}</span> },
    { key: 'exp', header: 'HSD', cell: (l) => date(l.expDate) },
    { key: 'left', header: 'Thời hạn', cell: (l) => <ExpiryChip daysLeft={l.daysLeft} /> },
    { key: 'avail', header: 'Khả dụng', align: 'right', cell: (l) => <span className="num">{num(l.available)}</span> },
    { key: 'held', header: 'Đang giữ', align: 'right', cell: (l) => <span className="num">{num(l.held)}</span> },
    { key: 'q', header: 'Cách ly', align: 'right', hideOnMobile: true, cell: (l) => <span className="num">{num(l.quarantine)}</span> },
    { key: 'actions', header: '', align: 'right', cell: (l) => lotActions(l) },
  ];
  const variantColumns: Column<VariantStockRow>[] = [
    { key: 'sku', header: 'SKU', cell: (v) => <span className="num">{v.sku}</span> },
    { key: 'product', header: 'Sản phẩm', cell: (v) => <span className="font-medium">{v.productName}</span> },
    { key: 'pack', header: 'Quy cách', cell: (v) => v.packLabel },
    { key: 'avail', header: 'Khả dụng', align: 'right', cell: (v) => <span className="num font-medium">{num(v.available)}</span> },
    { key: 'held', header: 'Đang giữ', align: 'right', cell: (v) => <span className="num">{num(v.held)}</span> },
    { key: 'q', header: 'Cách ly', align: 'right', hideOnMobile: true, cell: (v) => <span className="num">{num(v.quarantine)}</span> },
    { key: 'status', header: 'Tình trạng', cell: (v) => <Chip tone={v.isLow ? 'red' : 'green'}>{v.isLow ? 'Tồn thấp' : 'Đủ hàng'}</Chip> },
  ];
  const ledgerColumns: Column<LedgerRow>[] = [
    { key: 'at', header: 'Thời gian', cell: (e) => <span className="whitespace-nowrap">{dateTime(e.createdAt)}</span> },
    { key: 'type', header: 'Loại', cell: (e) => <StatusChip meta={ledgerEntryMeta} value={e.entryType} /> },
    { key: 'product', header: 'Sản phẩm', cell: (e) => <div><div>{e.productName}</div><div className="num text-ink-faint">{e.sku}</div></div> },
    { key: 'lot', header: 'Lô', hideOnMobile: true, cell: (e) => <span className="num">{e.lotCode ?? '—'}</span> },
    { key: 'loc', header: 'Vị trí', hideOnMobile: true, cell: (e) => <span className="num whitespace-nowrap">{e.locationCode ?? '—'}{e.toLocationCode ? ` → ${e.toLocationCode}` : ''}</span> },
    { key: 'qty', header: 'Biến động', align: 'right', cell: (e) => <span className="num">{e.qtyDelta > 0 ? '+' : ''}{num(e.qtyDelta)}</span> },
    { key: 'bal', header: 'Số dư', align: 'right', hideOnMobile: true, cell: (e) => <span className="num">{num(e.balanceAfter)}</span> },
    { key: 'note', header: 'Ghi chú', hideOnMobile: true, cell: (e) => e.note ?? '—' },
    { key: 'by', header: 'Người ghi', hideOnMobile: true, cell: (e) => e.creatorName ?? '—' },
  ];

  const active = { variants, lots, expiry, ledger }[tab];

  return (
    <>
      <PageHeader title="Tồn kho" subtitle="Xuất theo hạn sử dụng sớm nhất (FEFO)" actions={<LinkButton to="/receipts?new=1" variant="primary" icon={<PackagePlus size={15} />}>Nhập kho</LinkButton>} />
      <StatGrid>
        <StatCard label="Khả dụng (khay)" value={num(summary?.available)} />
        <StatCard label="Đang giữ cho đơn" value={num(summary?.held)} />
        <StatCard
          label="Quy cách tồn thấp"
          value={num(summary?.lowStockVariants)}
          hint={summary ? `Dưới ${summary.lowStockThreshold} khay · bấm để lọc` : undefined}
          tone={summary?.lowStockVariants ? 'danger' : undefined}
          to="/inventory?tab=variants&lowOnly=1"
        />
        <StatCard
          label="Lô sắp hết hạn"
          value={num(summary?.nearExpiryLots)}
          hint={summary ? `Trong ${summary.expiryWarningDays} ngày tới · bấm để xem` : undefined}
          tone={summary?.nearExpiryLots ? 'warning' : undefined}
          to="/inventory?tab=expiry"
        />
      </StatGrid>
      <Card flush>
        <Tabs label="Góc nhìn tồn kho" value={tab} onChange={setTab} tabs={[{ key: 'variants', label: 'Theo quy cách' }, { key: 'lots', label: 'Theo lô' }, { key: 'expiry', label: `Sắp hết hạn${summary ? ` (${summary.nearExpiryLots})` : ''}` }, { key: 'ledger', label: 'Sổ kho' }]} />
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder={tab === 'variants' ? 'Tìm SKU, sản phẩm...' : 'Tìm mã lô, SKU, sản phẩm...'} />
          {(tab === 'variants' || tab === 'lots') && (
            <FilterSelect label="Vị trí" value={list.filters.locationId} onChange={(v) => list.setFilter('locationId', v)}>
              {locations.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
            </FilterSelect>
          )}
          {tab === 'variants' && (
            <FilterSelect
              label="Tồn"
              value={lowOnly ? 'low' : ''}
              onChange={(v) => {
                const next = new URLSearchParams(params);
                next.set('tab', 'variants');
                if (v === 'low') next.set('lowOnly', '1');
                else next.delete('lowOnly');
                next.delete('page');
                setParams(next);
              }}
            >
              <option value="low">Chỉ tồn thấp</option>
            </FilterSelect>
          )}
          {tab === 'ledger' && (
            <>
              <FilterSelect label="Loại" value={list.filters.entryType} onChange={(v) => list.setFilter('entryType', v)}>{enumOptions(ledgerEntryMeta)}</FilterSelect>
              <Input
                type="date"
                aria-label="Từ ngày"
                className="w-full sm:w-[160px]"
                value={list.filters.from}
                onChange={(e) => list.setFilter('from', e.target.value)}
              />
              <Input
                type="date"
                aria-label="Đến ngày"
                className="w-full sm:w-[160px]"
                value={list.filters.to}
                onChange={(e) => list.setFilter('to', e.target.value)}
              />
            </>
          )}
        </FilterBar>
        {tab === 'variants' && (
          <DataTable
            columns={variantColumns}
            rows={variants.data?.items}
            rowKey={(v) => v.variantId}
            loading={variants.isLoading}
            error={variants.error}
            onRetry={variants.refetch}
            emptyText="Không có quy cách phù hợp"
            expandable={(v) =>
              v.lots.length === 0 ? (
                <p className="text-ink-muted">Không còn lô nào có hàng.</p>
              ) : (
                <table className="w-full text-small">
                  <thead>
                    <tr className="text-left text-micro font-semibold uppercase text-ink-muted"><th className="py-1">Lô (theo FEFO)</th><th>Vị trí</th><th>Ngày SX</th><th>HSD</th><th>Thời hạn</th><th className="text-right">Khả dụng</th><th className="text-right">Giữ</th><th className="text-right">Cách ly</th><th /></tr>
                  </thead>
                  <tbody>
                    {v.lots.map((l) => (
                      <tr key={l.stockId} className="border-t border-line">
                        <td className="py-1.5"><span className="num">{l.lotCode}</span>{l.stockId === v.lots.find((x) => x.available > 0 && (x.daysLeft ?? 0) >= 0)?.stockId && <span className="ml-2"><Chip tone="blue">Xuất trước</Chip></span>}</td>
                        <td className="num">{l.locationCode}</td>
                        <td>{date(l.mfgDate)}</td>
                        <td>{date(l.expDate)}</td>
                        <td><ExpiryChip daysLeft={l.daysLeft} /></td>
                        <td className="num text-right">{num(l.available)}</td>
                        <td className="num text-right">{num(l.held)}</td>
                        <td className="num text-right">{num(l.quarantine)}</td>
                        <td className="text-right">{lotActions(l)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )
            }
          />
        )}
        {tab === 'lots' && <DataTable columns={lotColumns} rows={lots.data?.items} rowKey={(l) => l.stockId} loading={lots.isLoading} error={lots.error} onRetry={lots.refetch} emptyText="Không có lô phù hợp" />}
        {tab === 'expiry' && <DataTable columns={lotColumns} rows={expiry.data?.items} rowKey={(l) => l.stockId} loading={expiry.isLoading} error={expiry.error} onRetry={expiry.refetch} emptyText="Không có lô nào sắp hết hạn" />}
        {tab === 'ledger' && <DataTable columns={ledgerColumns} rows={ledger.data?.items} rowKey={(e) => e.id} loading={ledger.isLoading} error={ledger.error} onRetry={ledger.refetch} emptyText="Chưa có biến động" />}
        {active.data && <Pagination page={active.data.page} pageSize={active.data.pageSize} total={active.data.total} onChange={list.setPage} />}
      </Card>

      <Modal
        open={!!moving}
        title={`Chuyển vị trí · ${moving?.lotCode ?? ''}`}
        onClose={() => setMoving(null)}
        onSubmit={() => {
          setMoveTouched(true);
          if (moveValid) move.mutate();
        }}
        footer={<><Button onClick={() => setMoving(null)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={move.isPending}>Chuyển</Button></>}
      >
        {moving && (
          <div className="space-y-3">
            <p className="text-ink-muted">{moving.productName} ({moving.sku}), đang ở <span className="num">{moving.locationCode}</span>, khả dụng {num(moving.available)} khay.</p>
            <Field label="Vị trí đích" required error={moveTouched && !Number(moveForm.toLocationId) ? 'Vui lòng chọn vị trí' : undefined}>
              <Select value={moveForm.toLocationId} onChange={(e) => setMoveForm({ ...moveForm, toLocationId: e.target.value })}>
                <option value="">Chọn...</option>
                {locations.filter((l) => l.id !== moving.locationId).map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}
              </Select>
            </Field>
            <Field label="Số khay" required error={moveTouched && !(Number(moveForm.qty) > 0 && Number(moveForm.qty) <= moving.available && Number.isInteger(Number(moveForm.qty))) ? `Nhập số nguyên từ 1 đến ${moving.available}` : undefined}>
              <Input type="number" min={1} max={moving.available} value={moveForm.qty} onChange={(e) => setMoveForm({ ...moveForm, qty: e.target.value })} />
            </Field>
          </div>
        )}
      </Modal>

      <Modal
        open={!!adjusting}
        title={`Điều chỉnh tồn · ${adjusting?.lotCode ?? ''}`}
        onClose={() => setAdjusting(null)}
        onSubmit={() => {
          setAdjustTouched(true);
          if (adjustValid) adjust.mutate();
        }}
        footer={<><Button onClick={() => setAdjusting(null)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={adjust.isPending}>Điều chỉnh</Button></>}
      >
        {adjusting && (
          <div className="space-y-3">
            <p className="text-ink-muted">{adjusting.productName} ({adjusting.sku}), vị trí <span className="num">{adjusting.locationCode}</span>, khả dụng {num(adjusting.available)} khay. Nhập số âm để trừ, số dương để cộng.</p>
            <Field
              label="Số khay (+/−)"
              required
              error={adjustTouched && !isAdjustQtyValid(adjustForm.qtyDelta, adjusting.available)
                ? (Number(adjustForm.qtyDelta) === 0 || !Number.isInteger(Number(adjustForm.qtyDelta))
                  ? 'Nhập số nguyên khác 0'
                  : `Không trừ quá ${adjusting.available} khay`)
                : undefined}
            >
              <Input type="number" step={1} value={adjustForm.qtyDelta} onChange={(e) => setAdjustForm({ ...adjustForm, qtyDelta: e.target.value })} placeholder="-2 hoặc 3" />
            </Field>
            <Field label="Lý do" required error={adjustTouched && !adjustForm.note.trim() ? 'Vui lòng nhập lý do điều chỉnh' : undefined}>
              <Textarea value={adjustForm.note} onChange={(e) => setAdjustForm({ ...adjustForm, note: e.target.value })} placeholder="Ví dụ: Hao hụt kiểm kê tay" />
            </Field>
          </div>
        )}
      </Modal>
    </>
  );
}
