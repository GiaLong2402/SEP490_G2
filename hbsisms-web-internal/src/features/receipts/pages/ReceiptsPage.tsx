import { useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, Layers, PackageCheck, Pencil, Plus, Trash2, XCircle } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useVariantOptions } from '@/features/products';
import { useWarehouses } from '@/features/warehouses';
import { useAction } from '@/shared/api/useAction';
import { receiptStatusMeta, receiptTypeLabels, type ReceiptType } from '@/shared/domain/enums';
import { date, dateTimeFull, money, num, todayISO } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, DataTable, DateInput, Field, FilterBar, FilterSelect, IconButton, InfoTip, Input, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, Tabs, enumOptions, useListParams, type Column } from '@/shared/ui';
import { receiptsApi } from '../api';
import { LocationPicker } from '../components/LocationPicker';
import { RawMaterialFormModal } from '../components/RawMaterialFormModal';
import { RawMaterialLotsModal } from '../components/RawMaterialLotsModal';
import { RawReceiptCompleteModal } from '../components/RawReceiptCompleteModal';
import { useRawMaterials, useReceipts, useUnitLabel } from '../hooks';
import type { RawMaterial, ReceiptListItem } from '../types';

interface FinishedRow { key: number; variantId: string; lotCode: string; mfgDate: string; expDate: string; locationId: string; qty: string }
interface RawRow { key: number; rawMaterialId: string; qty: string }
let rowKey = 1;
const finishedRow = (): FinishedRow => ({ key: rowKey++, variantId: '', lotCode: '', mfgDate: todayISO(), expDate: todayISO(14), locationId: '', qty: '' });
const rawRow = (): RawRow => ({ key: rowKey++, rawMaterialId: '', qty: '' });
/** Hàng thành phẩm có dạng "SKU · mã lô" → tách để hiện mã lô ở dòng dưới, thẳng với SKU. */
const splitItem = (name: string) => {
  const [main, ...rest] = name.split(' · ');
  return { main, lot: rest.join(' · ') || null };
};

export default function ReceiptsPage() {
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState<'receipts' | 'raw'>('receipts');
  const list = useListParams(['receiptType', 'status', 'sort'] as const);
  const codeAsc = list.filters.sort === 'asc';
  const [rawQ, setRawQ] = useState('');
  const [rawStatus, setRawStatus] = useState('');
  const { data, isLoading, error, refetch } = useReceipts({ q: list.q, page: list.page, receiptType: list.filters.receiptType, status: list.filters.status, sort: list.filters.sort });
  const raw = useRawMaterials(
    {
      q: rawQ,
      activeOnly: rawStatus === 'active' ? true : rawStatus === 'inactive' ? false : undefined,
      lowStock: rawStatus === 'low' ? true : undefined,
    },
    tab === 'raw',
  );
  /** Full active catalog for receipt lines (not affected by raw-tab filters). */
  const rawCatalog = useRawMaterials({ activeOnly: true });
  const variants = useVariantOptions().data ?? [];
  const unitLabel = useUnitLabel();
  const warehouses = useWarehouses().data ?? [];
  /** Số khay còn để được ở mỗi vị trí = min(chỗ trống của vị trí, chỗ trống của cả kho) — giống kiểm tra ở backend. */
  const roomOf = new Map<number, number>();
  for (const w of warehouses) {
    const whRoom = w.capacity - w.totalQty;
    for (const l of w.locations) roomOf.set(Number(l.id), Math.max(0, Math.min(l.capacity - l.usedQty, whRoom)));
  }
  const locationLabel = new Map(warehouses.flatMap((w) => w.locations.map((l) => [Number(l.id), `${l.code} (${w.code})`] as const)));

  const [open, setOpen] = useState(false);
  const [type, setType] = useState<ReceiptType>('finished');
  const [source, setSource] = useState('');
  const [expectedDate, setExpectedDate] = useState('');
  const [finished, setFinished] = useState<FinishedRow[]>([finishedRow()]);
  const [rawRows, setRawRows] = useState<RawRow[]>([rawRow()]);
  const [touched, setTouched] = useState(false);
  const [materialForm, setMaterialForm] = useState<{ material?: RawMaterial; fromReceipt?: boolean } | null>(null);
  const [completing, setCompleting] = useState<ReceiptListItem | null>(null);
  const [cancelling, setCancelling] = useState<ReceiptListItem | null>(null);
  const [lotsOf, setLotsOf] = useState<RawMaterial | null>(null);

  const openForm = () => {
    setType('finished');
    setSource('');
    setExpectedDate('');
    setFinished([finishedRow()]);
    setRawRows([rawRow()]);
    setTouched(false);
    setOpen(true);
  };
  useEffect(() => {
    if (params.get('new')) {
      openForm();
      setParams({}, { replace: true });
    }
  }, [params]); // eslint-disable-line react-hooks/exhaustive-deps

  /** Tổng khay định nhập vào từng vị trí (nhiều dòng có thể chung 1 vị trí). */
  const plannedByLocation = new Map<number, number>();
  for (const r of finished) if (Number(r.locationId) > 0) plannedByLocation.set(Number(r.locationId), (plannedByLocation.get(Number(r.locationId)) ?? 0) + (Number(r.qty) || 0));
  const overCapacity = [...plannedByLocation].filter(([id, qty]) => qty > (roomOf.get(id) ?? Infinity));
  const finishedValid = overCapacity.length === 0 && finished.every((r) => Number(r.variantId) > 0 && Number(r.locationId) > 0 && r.expDate && Number.isInteger(Number(r.qty)) && Number(r.qty) > 0 && (!r.mfgDate || r.expDate >= r.mfgDate));
  const rawValid = rawRows.every((r) => Number(r.rawMaterialId) > 0 && Number(r.qty) > 0) && new Set(rawRows.map((r) => r.rawMaterialId)).size === rawRows.length;
  const linesValid = type === 'finished' ? finishedValid : rawValid;
  const sourceValid = source.trim().length > 0;

  const create = useAction(
    () =>
      receiptsApi.create({
        receiptType: type,
        source: source.trim(),
        note: null,
        finishedLines: type === 'finished' ? finished.map((r) => ({ variantId: Number(r.variantId), lotCode: r.lotCode.trim() || null, mfgDate: r.mfgDate || null, expDate: r.expDate, locationId: Number(r.locationId), qty: Number(r.qty) })) : undefined,
        rawLines: type === 'raw' ? rawRows.map((r) => ({ rawMaterialId: Number(r.rawMaterialId), qty: Number(r.qty) })) : undefined,
        expectedDate: type === 'raw' ? expectedDate || null : null,
      }),
    {
      success: (r) => (r.status === 'pending' ? `Đã tạo phiếu ${r.code} - chờ nhập kho (chưa cộng tồn)` : `Đã tạo phiếu nhập ${r.code}`),
      onDone: () => setOpen(false),
    },
  );
  const cancel = useAction((note: string) => receiptsApi.cancel(cancelling!.id, note.trim() || null), {
    success: (r) => `Đã hủy phiếu ${r.code}`,
    onDone: () => setCancelling(null),
  });

  const shownItems = (r: ReceiptListItem) => r.items.slice(0, 4);
  const moreItems = (r: ReceiptListItem) => Math.max(0, r.items.length - 4);
  const columns: Column<ReceiptListItem>[] = [
    {
      key: 'code',
      header: (
        <button
          type="button"
          className="inline-flex items-center gap-1 uppercase tracking-wide hover:text-ink"
          onClick={() => list.setFilter('sort', codeAsc ? '' : 'asc')}
          title={codeAsc ? 'Đang xếp mã tăng dần - bấm để xếp giảm dần' : 'Đang xếp mã giảm dần - bấm để xếp tăng dần'}
          aria-label={codeAsc ? 'Mã phiếu, tăng dần. Bấm để xếp giảm dần' : 'Mã phiếu, giảm dần. Bấm để xếp tăng dần'}
        >
          Mã phiếu {codeAsc ? <ArrowUp size={13} /> : <ArrowDown size={13} />}
        </button>
      ),
      // Không in đậm; nổi bằng màu chủ đạo + phông số.
      cell: (r) => <span className="num text-body text-primary">{r.code}</span>,
    },
    { key: 'type', header: 'Loại', cell: (r) => <Chip tone={r.receiptType === 'raw' ? 'amber' : 'blue'}>{receiptTypeLabels[r.receiptType]}</Chip> },
    { key: 'source', header: 'Nguồn', cell: (r) => <div className="min-w-[120px] max-w-[200px] text-ink">{r.source?.trim() || r.supplierName || '-'}</div> },
    {
      key: 'items', header: 'Hàng nhập',
      cell: (r) => (
        <div className="min-w-[200px] space-y-1.5 py-1.5">
          {shownItems(r).map((i, idx) => {
            const { main, lot } = splitItem(i.name);
            return (
              <div key={idx} title={i.name}>
                <div className="truncate leading-5 text-ink">{main}</div>
                {lot && <div className="truncate text-small leading-5 text-ink-faint">{lot}</div>}
              </div>
            );
          })}
          {moreItems(r) > 0 && <div className="text-small leading-5 text-ink-faint">+ {moreItems(r)} mặt hàng khác</div>}
        </div>
      ),
    },
    {
      // Cột riêng; mỗi khối cao bằng khối tên hàng bên trái → số lượng thẳng hàng với tên và thẳng cột giữa các phiếu.
      key: 'qty', header: 'Số lượng', align: 'right',
      cell: (r) => (
        <div className="space-y-1.5 py-1.5">
          {shownItems(r).map((i, idx) => (
            <div key={idx}>
              <div className="whitespace-nowrap leading-5 tabular-nums text-ink">
                {num(i.qty)} <span className="text-ink-muted">{unitLabel(i.unit)}</span>
              </div>
              {splitItem(i.name).lot && <div className="text-small leading-5" aria-hidden>&nbsp;</div>}
            </div>
          ))}
          {moreItems(r) > 0 && <div className="text-small leading-5" aria-hidden>&nbsp;</div>}
        </div>
      ),
    },
    {
      key: 'amount', header: 'Tiền nhập', align: 'right', hideOnMobile: true,
      cell: (r) => (r.totalAmount !== null ? <span className="whitespace-nowrap font-medium tabular-nums text-ink">{money(r.totalAmount)}</span> : <span className="text-ink-faint">-</span>),
    },
    {
      key: 'status', header: 'Trạng thái',
      cell: (r) => (
        <div className="whitespace-nowrap">
          <StatusChip
            meta={receiptStatusMeta}
            value={r.status && r.status in receiptStatusMeta ? r.status : 'completed'}
          />
          {r.status === 'pending' && (
            <div className="mt-1 flex items-center gap-1 text-small text-ink-muted">
              {r.expectedDate ? `Về ${date(r.expectedDate)}` : 'Chưa hẹn ngày về'}
              <InfoTip label={`Ghi chú phiếu ${r.code}`}>Đây mới là số dự kiến, chưa cộng vào kho. Khi hàng về, bấm nút “Nhập kho” để ghi số hàng thật.</InfoTip>
            </div>
          )}
        </div>
      ),
    },
    {
      key: 'at', header: 'Cập nhật', hideOnMobile: true,
      cell: (r) => {
        const who = (r.status === 'pending' ? r.receiverName : r.completerName ?? r.receiverName) ?? '-';
        const verb = r.status === 'pending' ? 'Tạo bởi' : r.status === 'cancelled' ? 'Hủy bởi' : 'Nhập bởi';
        return (
          <div className="whitespace-nowrap">
            <div className="leading-5 text-ink"><span className="text-ink-muted">{verb}</span> {who}</div>
            <div className="text-small leading-5 tabular-nums text-ink-faint">{dateTimeFull(r.completedAt ?? r.receivedAt)}</div>
          </div>
        );
      },
    },
    {
      key: 'actions', header: '', align: 'right',
      cell: (r) =>
        r.status === 'pending' ? (
          <div className="flex justify-end gap-1.5">
            {r.receiptType === 'raw' && <Button className="!h-7 !px-2.5 text-small" variant="primary" icon={<PackageCheck size={14} />} onClick={() => setCompleting(r)}>Nhập kho</Button>}
            <IconButton label={`Hủy phiếu ${r.code}`} onClick={() => setCancelling(r)}><XCircle size={14} /></IconButton>
          </div>
        ) : null,
    },
  ];
  const rawColumns: Column<RawMaterial>[] = [
    { key: 'code', header: 'Mã', cell: (m) => <span className="num">{m.code}</span> },
    { key: 'name', header: 'Nguyên liệu', cell: (m) => <span className="font-medium">{m.name}</span> },
    { key: 'qty', header: 'Tồn', align: 'right', cell: (m) => <span className="num">{num(m.qtyOnHand)} {unitLabel(m.unit)}</span> },
    { key: 'reorder', header: 'Mức đặt lại', align: 'right', cell: (m) => <span className="num">{num(m.reorderLevel)} {unitLabel(m.unit)}</span> },
    { key: 'status', header: 'Tình trạng', cell: (m) => (!m.isActive ? <Chip>Ngừng dùng</Chip> : m.reorderLevel !== null && m.qtyOnHand < m.reorderLevel ? <Chip tone="red">Cần đặt thêm</Chip> : <Chip tone="green">Đủ dùng</Chip>) },
    {
      key: 'edit', header: '', align: 'right',
      cell: (m) => (
        <div className="flex justify-end gap-1">
          <IconButton label={`Lô của ${m.name}`} onClick={() => setLotsOf(m)}><Layers size={14} /></IconButton>
          <IconButton label={`Sửa ${m.name}`} onClick={() => setMaterialForm({ material: m })}><Pencil size={14} /></IconButton>
        </div>
      ),
    },
  ];

  const receiptTabs = [
    { key: 'receipts' as const, label: 'Phiếu nhập' },
    { key: 'raw' as const, label: 'Tồn nguyên liệu' },
  ];

  return (
    <>
      <PageHeader
        title="Nhập kho"
        subtitle="Phiếu nhập thành phẩm, nguyên liệu và tồn nguyên liệu"
        actions={
          <>
            {tab === 'raw' && <Button icon={<Plus size={15} />} onClick={() => setMaterialForm({})}>Thêm nguyên liệu</Button>}
            <Button variant="primary" icon={<Plus size={15} />} onClick={openForm}>Tạo phiếu nhập</Button>
          </>
        }
      />
      <Card flush>
        <Tabs label="Nhập kho" value={tab} onChange={setTab} tabs={receiptTabs} />
        {tab === 'receipts' ? (
          <>
            <FilterBar>
              <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã phiếu, nguồn hàng..." />
              <FilterSelect label="Loại" value={list.filters.receiptType} onChange={(v) => list.setFilter('receiptType', v)}>{enumOptions(receiptTypeLabels)}</FilterSelect>
              <FilterSelect label="Trạng thái" value={list.filters.status} onChange={(v) => list.setFilter('status', v)}>{enumOptions(receiptStatusMeta)}</FilterSelect>
            </FilterBar>
            <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu nhập" />
            {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
          </>
        ) : (
          <>
            <FilterBar>
              <SearchBox value={rawQ} onChange={setRawQ} placeholder="Tìm mã, tên nguyên liệu..." />
              <FilterSelect label="Tình trạng" value={rawStatus} onChange={setRawStatus}>
                <option value="active">Đang dùng</option>
                <option value="inactive">Ngừng dùng</option>
                <option value="low">Cần đặt thêm</option>
              </FilterSelect>
            </FilterBar>
            <DataTable columns={rawColumns} rows={raw.data} rowKey={(m) => m.id} loading={raw.isLoading} error={raw.error} onRetry={raw.refetch} emptyText="Chưa có nguyên liệu" />
          </>
        )}
      </Card>

      <Modal
        open={open}
        title="Tạo phiếu nhập kho"
        width="xl"
        onClose={() => setOpen(false)}
        onSubmit={() => {
          setTouched(true);
          if (sourceValid && linesValid) create.mutate();
        }}
        footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>{type === 'raw' ? 'Lưu phiếu chờ nhập' : 'Lưu phiếu'}</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Loại phiếu" required><Select value={type} onChange={(e) => setType(e.target.value as ReceiptType)}>{enumOptions(receiptTypeLabels)}</Select></Field>
          <Field
            label="Nguồn hàng"
            required
            error={touched && !sourceValid ? 'Vui lòng nhập nguồn hàng' : undefined}
            tip="Ghi nơi giao hàng cho cửa hàng, ví dụ tên tàu, tên xưởng hoặc người bán."
          >
            <Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="Ví dụ: Xưởng chế biến Hòa Bình" />
          </Field>
          {type === 'raw' && (
            <Field label="Ngày dự kiến về" tip="Phiếu này chỉ là phiếu hẹn, chưa cộng vào kho. Khi hàng về, bấm nút “Nhập kho” ở phiếu để ghi số hàng thật và giá mua." className="sm:col-span-2">
              <DateInput min={todayISO()} className="sm:w-[200px]" value={expectedDate} onChange={setExpectedDate} />
            </Field>
          )}
        </div>
        <div className="mt-4 overflow-x-auto">
          {type === 'finished' ? (
            <ul className="space-y-2.5">
              {finished.map((r, i) => {
                const patch = (p: Partial<FinishedRow>) => setFinished(finished.map((x) => (x.key === r.key ? { ...x, ...p } : x)));
                /** Chỗ còn ở vị trí cho dòng này = chỗ trống − số khay các dòng khác đã định để vào cùng vị trí. */
                const roomFor = (locationId: number) =>
                  (roomOf.get(locationId) ?? 0) - finished.filter((x) => x.key !== r.key && Number(x.locationId) === locationId).reduce((sum, x) => sum + (Number(x.qty) || 0), 0);
                return (
                  <li key={r.key} className="rounded-md border border-line">
                    <div className="flex items-center gap-2 rounded-t-md border-b border-line bg-rowalt/60 px-3 py-1.5">
                      <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-micro font-semibold text-card">{i + 1}</span>
                      <span className="text-small font-medium text-ink-muted">Mặt hàng {i + 1}</span>
                      <span className="ml-auto">
                        <IconButton label={`Xóa mặt hàng ${i + 1}`} disabled={finished.length === 1} onClick={() => setFinished(finished.filter((x) => x.key !== r.key))}><Trash2 size={14} /></IconButton>
                      </span>
                    </div>
                    <div className="grid gap-3 p-3 sm:grid-cols-2 lg:grid-cols-12">
                      <Field label="Quy cách" required className="lg:col-span-7">
                        <Select value={r.variantId} onChange={(e) => patch({ variantId: e.target.value })}>
                          <option value="">Chọn...</option>
                          {variants.map((v) => <option key={v.id} value={v.id}>{v.productName} · {v.packLabel}</option>)}
                        </Select>
                      </Field>
                      <Field
                        label="Mã lô"
                        className="lg:col-span-5"
                        tip={<><span className="block">Không có mã lô thì để trống, máy tự đặt.</span><span className="block">Ví dụ: <b>LOT-261123-01</b> = lô thứ 1 của ngày 23/11/2026.</span></>}
                      >
                        <Input placeholder="Tự sinh" value={r.lotCode} onChange={(e) => patch({ lotCode: e.target.value })} />
                      </Field>
                      <Field label="Ngày sản xuất" className="lg:col-span-3">
                        <DateInput max={todayISO()} value={r.mfgDate} onChange={(v) => patch({ mfgDate: v })} />
                      </Field>
                      <Field label="Hạn sử dụng" required className="lg:col-span-3">
                        <DateInput value={r.expDate} onChange={(v) => patch({ expDate: v })} />
                      </Field>
                      <Field label="Số khay" required className="lg:col-span-2">
                        <Input type="number" min={1} className="text-right" value={r.qty} onChange={(e) => patch({ qty: e.target.value })} />
                      </Field>
                      <Field
                        label="Vị trí"
                        required
                        className="lg:col-span-4"
                        tip={<><span className="block">Bấm để xem các kho còn chỗ, rê chuột vào kho để xem các chỗ trong kho.</span><span className="block">Chỉ hiện chỗ còn đủ cho số khay bạn đã nhập.</span></>}
                      >
                        <LocationPicker warehouses={warehouses} value={r.locationId} need={Number(r.qty) || 0} roomFor={roomFor} onChange={(id) => patch({ locationId: id })} />
                      </Field>
                    </div>
                  </li>
                );
              })}
            </ul>
          ) : (
            <ul className="space-y-2.5">
              {rawRows.map((r, i) => {
                const patch = (p: Partial<RawRow>) => setRawRows(rawRows.map((x) => (x.key === r.key ? { ...x, ...p } : x)));
                const material = rawCatalog.data?.find((m) => String(m.id) === r.rawMaterialId);
                const unit = material ? unitLabel(material.unit) : '';
                const duplicate = !!r.rawMaterialId && rawRows.some((x, j) => j < i && x.rawMaterialId === r.rawMaterialId);
                return (
                  <li key={r.key} className="rounded-md border border-line">
                    <div className="flex items-center gap-2 rounded-t-md border-b border-line bg-rowalt/60 px-3 py-1.5">
                      <span className="inline-flex h-6 w-6 items-center justify-center rounded-full bg-primary text-micro font-semibold text-card">{i + 1}</span>
                      <span className="text-small font-medium text-ink-muted">Nguyên liệu {i + 1}</span>
                      <span className="ml-auto">
                        <IconButton label={`Xóa nguyên liệu ${i + 1}`} disabled={rawRows.length === 1} onClick={() => setRawRows(rawRows.filter((x) => x.key !== r.key))}><Trash2 size={14} /></IconButton>
                      </span>
                    </div>
                    <div className="grid gap-3 p-3 sm:grid-cols-12">
                      <Field
                        label="Nguyên liệu"
                        required
                        className="sm:col-span-8"
                        error={touched && duplicate ? 'Nguyên liệu này đã chọn ở trên - gộp số lượng vào một dòng.' : undefined}
                        hint={material ? <>Đang tồn <span className="font-medium tabular-nums text-ink-muted">{num(material.qtyOnHand)} {unit}</span></> : undefined}
                      >
                        <Select value={r.rawMaterialId} onChange={(e) => patch({ rawMaterialId: e.target.value })}>
                          <option value="">Chọn...</option>
                          {rawCatalog.data?.filter((m) => m.isActive || String(m.id) === r.rawMaterialId).map((m) => <option key={m.id} value={m.id}>{m.name} ({unitLabel(m.unit)})</option>)}
                        </Select>
                      </Field>
                      <Field
                        label={unit ? `Số lượng dự kiến (${unit})` : 'Số lượng dự kiến'}
                        required
                        className="sm:col-span-4"
                        tip="Số hàng bạn hẹn nhận. Khi hàng về sẽ ghi số thật, khác số này cũng được."
                      >
                        <Input type="number" min={0} step="0.1" className="text-right" placeholder="0" value={r.qty} onChange={(e) => patch({ qty: e.target.value })} />
                      </Field>
                    </div>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button icon={<Plus size={14} />} onClick={() => (type === 'finished' ? setFinished([...finished, finishedRow()]) : setRawRows([...rawRows, rawRow()]))}>{type === 'finished' ? 'Thêm mặt hàng' : 'Thêm nguyên liệu'}</Button>
          {type === 'raw' && (
            <span className="inline-flex items-center gap-1">
              <Button variant="ghost" onClick={() => setMaterialForm({ fromReceipt: true })}>+ Tạo nguyên liệu mới</Button>
              <InfoTip label="Ghi chú tạo nguyên liệu mới">Dùng khi nguyên liệu chưa có trong danh sách chọn. Tạo xong sẽ tự điền vào phiếu.</InfoTip>
            </span>
          )}
        </div>
        {type === 'finished' && overCapacity.map(([id, qty]) => (
          <p key={id} className="mt-2 text-small text-red-fg">Vị trí {locationLabel.get(id) ?? id} chỉ còn {num(roomOf.get(id) ?? 0)} khay, đang định nhập {num(qty)} khay.</p>
        ))}
        {touched && !linesValid && overCapacity.length === 0 && <p className="mt-2 text-small text-red-fg">{type === 'raw' ? 'Mỗi nguyên liệu cần được chọn (không trùng nhau) và số lượng dự kiến lớn hơn 0.' : 'Mỗi mặt hàng cần đủ thông tin, số khay lớn hơn 0 và hạn sử dụng không trước ngày sản xuất.'}</p>}
      </Modal>

      <RawReceiptCompleteModal receipt={completing} onClose={() => setCompleting(null)} />
      <RawMaterialLotsModal material={lotsOf} onClose={() => setLotsOf(null)} />
      <ConfirmDialog
        open={!!cancelling}
        danger
        title={cancelling ? `Hủy phiếu ${cancelling.code}` : ''}
        message="Phiếu đang chờ nhập nên chưa cộng tồn - hủy không làm thay đổi tồn kho."
        withNote
        noteLabel="Lý do hủy"
        confirmLabel="Hủy phiếu"
        loading={cancel.isPending}
        onConfirm={(note) => cancel.mutate(note ?? '')}
        onClose={() => setCancelling(null)}
      />

      <RawMaterialFormModal
        open={!!materialForm}
        material={materialForm?.material}
        onClose={() => setMaterialForm(null)}
        onSaved={(id) => {
          if (!materialForm?.fromReceipt) return;
          const empty = rawRows.find((r) => !r.rawMaterialId);
          setRawRows(empty ? rawRows.map((r) => (r.key === empty.key ? { ...r, rawMaterialId: String(id) } : r)) : [...rawRows, { ...rawRow(), rawMaterialId: String(id) }]);
        }}
      />
    </>
  );
}
