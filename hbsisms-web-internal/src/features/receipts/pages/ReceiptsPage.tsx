import { useEffect, useState } from 'react';
import { ArrowDown, ArrowUp, ChevronDown, ChevronUp, Layers, PackageCheck, Pencil, Plus, Trash2, XCircle } from 'lucide-react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useRawZones } from '@/features/warehouses';
import { useAction } from '@/shared/api/useAction';
import { receiptStatusMeta, receiptTypeLabels } from '@/shared/domain/enums';
import { date, dateTimeFull, money, num, todayISO } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, DataTable, DateInput, Field, FilterBar, FilterSelect, IconButton, InfoTip, Input, Modal, PageHeader, Pagination, SearchBox, Select, StatusChip, Tabs, enumOptions, useListParams, type Column } from '@/shared/ui';
import { receiptsApi } from '../api';
import { RawMaterialFormModal } from '../components/RawMaterialFormModal';
import { RawMaterialLotsModal } from '../components/RawMaterialLotsModal';
import { FinishedReceiptCompleteModal } from '../components/FinishedReceiptCompleteModal';
import { RawReceiptCompleteModal } from '../components/RawReceiptCompleteModal';
import { useRawMaterials, useReceipts, useUnitLabel } from '../hooks';
import type { RawMaterial, ReceiptListItem } from '../types';

interface RawRow { key: number; rawMaterialId: string; qty: string }
let rowKey = 1;
const rawRow = (): RawRow => ({ key: rowKey++, rawMaterialId: '', qty: '' });
/** Hàng thành phẩm có dạng "SKU · mã lô" → tách để hiện mã lô ở dòng dưới, thẳng với SKU. */
const splitItem = (name: string) => {
  const [main, ...rest] = name.split(' · ');
  return { main, lot: rest.join(' · ') || null };
};

export default function ReceiptsPage() {
  const [params, setParams] = useSearchParams();
  const navigate = useNavigate();
  /** Mở từ lệnh sản xuất: lưu phiếu xong quay lại lệnh. */
  const [returnTo, setReturnTo] = useState<string | null>(null);
  const [tab, setTab] = useState<'receipts' | 'raw'>('receipts');
  const list = useListParams(['receiptType', 'status', 'sort'] as const);
  const codeAsc = list.filters.sort === 'asc';
  const [rawQ, setRawQ] = useState('');
  const [rawStatus, setRawStatus] = useState('');
  /** Lọc theo khu cất (Khu lạnh / Khu khô) — mở từ màn Kho và vị trí: /receipts?tab=raw&zone=<id>. */
  const [rawZone, setRawZone] = useState('');
  const rawZones = useRawZones().data ?? [];
  const { data, isLoading, error, refetch } = useReceipts({ q: list.q, page: list.page, receiptType: list.filters.receiptType, status: list.filters.status, sort: list.filters.sort });
  const raw = useRawMaterials(
    {
      q: rawQ,
      activeOnly: rawStatus === 'active' ? true : rawStatus === 'inactive' ? false : undefined,
      lowStock: rawStatus === 'low' ? true : undefined,
      storageLocationId: rawZone ? Number(rawZone) : undefined,
    },
    tab === 'raw',
  );
  /** Full active catalog for receipt lines (not affected by raw-tab filters). */
  const rawCatalog = useRawMaterials({ activeOnly: true });
  const unitLabel = useUnitLabel();

  const [open, setOpen] = useState(false);
  const [source, setSource] = useState('');
  const [expectedDate, setExpectedDate] = useState('');
  const [rawRows, setRawRows] = useState<RawRow[]>([rawRow()]);
  const [touched, setTouched] = useState(false);
  const [materialForm, setMaterialForm] = useState<{ material?: RawMaterial; fromReceipt?: boolean } | null>(null);
  const [completing, setCompleting] = useState<ReceiptListItem | null>(null);
  const [completingFinished, setCompletingFinished] = useState<ReceiptListItem | null>(null);
  const [cancelling, setCancelling] = useState<ReceiptListItem | null>(null);
  const [lotsOf, setLotsOf] = useState<RawMaterial | null>(null);
  /** Highlight phiếu khi nhảy từ Nhật ký (?id= / ?q= mã PN). */
  const [focusCode, setFocusCode] = useState<string | null>(params.get('q') || null);

  const openForm = () => {
    setReturnTo(null);
    setSource('');
    setExpectedDate('');
    setRawRows([rawRow()]);
    setTouched(false);
    setOpen(true);
  };
  useEffect(() => {
    if (params.get('tab') === 'raw' && !params.get('new')) {
      setTab('raw');
      setRawZone(params.get('zone') ?? '');
      setParams({}, { replace: true });
      return;
    }
    if (params.get('new')) {
      openForm();
      // Điền sẵn từ lệnh sản xuất thiếu nguyên liệu: ?new=raw&lines=<rawMaterialId>:<qty>,...&source=...&returnTo=...
      if (params.get('new') === 'raw') {
        setSource(params.get('source') ?? '');
        const lines = (params.get('lines') ?? '')
          .split(',')
          .map((x) => x.split(':'))
          .filter(([id, qty]) => Number(id) > 0 && Number(qty) > 0)
          .map(([id, qty]) => ({ ...rawRow(), rawMaterialId: id, qty: String(Number(qty)) }));
        if (lines.length) setRawRows(lines);
        const back = params.get('returnTo');
        setReturnTo(back && back.startsWith('/') ? back : null);
      }
      setParams({}, { replace: true });
      return;
    }

    // Từ Nhật ký: /receipts?id=62 → resolve mã PN-… rồi lọc đúng phiếu.
    const focusId = Number(params.get('id'));
    if (!Number.isFinite(focusId) || focusId <= 0) return;
    let cancelled = false;
    receiptsApi
      .get(focusId)
      .then((r) => {
        if (cancelled) return;
        setFocusCode(r.code);
        setTab('receipts');
        list.setQ(r.code);
        setParams(
          (prev) => {
            const next = new URLSearchParams(prev);
            next.delete('id');
            next.set('q', r.code);
            return next;
          },
          { replace: true },
        );
      })
      .catch(() => {
        if (cancelled) return;
        setParams(
          (prev) => {
            const next = new URLSearchParams(prev);
            next.delete('id');
            return next;
          },
          { replace: true },
        );
      });
    return () => {
      cancelled = true;
    };
  }, [params]); // eslint-disable-line react-hooks/exhaustive-deps

  const rawValid = rawRows.every((r) => Number(r.rawMaterialId) > 0 && Number(r.qty) > 0) && new Set(rawRows.map((r) => r.rawMaterialId)).size === rawRows.length;
  const linesValid = rawValid;
  const rawRowErrors = (r: RawRow, duplicate: boolean) => ({
    material: !(Number(r.rawMaterialId) > 0) ? 'Chọn nguyên liệu' : duplicate ? 'Nguyên liệu này đã chọn ở trên - gộp số lượng vào một dòng.' : undefined,
    qty: Number(r.qty) > 0 ? undefined : 'Số lượng lớn hơn 0',
  });
  const sourceValid = source.trim().length > 0;

  const create = useAction(
    () =>
      receiptsApi.create({
        receiptType: 'raw',
        source: source.trim(),
        note: null,
        finishedLines: undefined,
        rawLines: rawRows.map((r) => ({ rawMaterialId: Number(r.rawMaterialId), qty: Number(r.qty) })),
        expectedDate: expectedDate || null,
      }),
    {
      success: (r) => (r.status === 'pending' ? `Đã tạo phiếu ${r.code} - chờ nhập kho (chưa cộng tồn)` : `Đã tạo phiếu nhập ${r.code}`),
      onDone: () => {
        setOpen(false);
        if (returnTo) navigate(returnTo);
      },
    },
  );
  const cancel = useAction((note: string) => receiptsApi.cancel(cancelling!.id, note.trim() || null), {
    success: (r) => `Đã hủy phiếu ${r.code}`,
    onDone: () => setCancelling(null),
  });

  /** Phiếu nhiều mặt hàng: mặc định hiện 4 dòng, bấm "Xem thêm" để mở hết, "Thu gọn" để ẩn lại. */
  const ITEMS_PREVIEW = 4;
  const [expandedReceipts, setExpandedReceipts] = useState<Set<string>>(new Set());
  const isExpanded = (r: ReceiptListItem) => expandedReceipts.has(String(r.id));
  const toggleItems = (r: ReceiptListItem) =>
    setExpandedReceipts((prev) => {
      const next = new Set(prev);
      if (!next.delete(String(r.id))) next.add(String(r.id));
      return next;
    });
  const shownItems = (r: ReceiptListItem) => (isExpanded(r) ? r.items : r.items.slice(0, ITEMS_PREVIEW));
  const moreItems = (r: ReceiptListItem) => Math.max(0, r.items.length - ITEMS_PREVIEW);
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
      cell: (r) => (
        <span
          className={`num text-body text-primary ${
            focusCode && r.code === focusCode ? 'rounded bg-primary-soft px-1.5 py-0.5 ring-1 ring-primary/30' : ''
          }`}
        >
          {r.code}
        </span>
      ),
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
          {moreItems(r) > 0 && (
            <button
              type="button"
              aria-expanded={isExpanded(r)}
              onClick={(e) => {
                e.stopPropagation();
                toggleItems(r);
              }}
              className="inline-flex items-center gap-1 text-small leading-5 font-medium text-primary hover:underline"
            >
              {isExpanded(r) ? <><ChevronUp size={13} aria-hidden />Thu gọn</> : <><ChevronDown size={13} aria-hidden />Xem thêm {moreItems(r)} mặt hàng</>}
            </button>
          )}
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
            <Button className="!h-7 !px-2.5 text-small" variant="primary" icon={<PackageCheck size={14} />} onClick={() => (r.receiptType === 'raw' ? setCompleting(r) : setCompletingFinished(r))}>Nhập kho</Button>
            <IconButton label={`Hủy phiếu ${r.code}`} onClick={() => setCancelling(r)}><XCircle size={14} /></IconButton>
          </div>
        ) : null,
    },
  ];
  const rawColumns: Column<RawMaterial>[] = [
    { key: 'code', header: 'Mã', cell: (m) => <span className="num">{m.code}</span> },
    { key: 'name', header: 'Nguyên liệu', cell: (m) => <span className="font-medium">{m.name}</span> },
    { key: 'zone', header: 'Khu', cell: (m) => (m.storageLocationId ? <Chip tone={m.storageLocationCode?.toUpperCase().startsWith('LANH') ? 'blue' : undefined}>{m.storageLocationName ?? m.storageLocationCode}</Chip> : <span className="text-ink-faint">Chưa gắn khu</span>) },
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
        subtitle="Nhập nguyên liệu từ ngoài; thành phẩm chỉ nhập qua lệnh sản xuất"
        actions={
          <>
            {tab === 'raw' && <Button icon={<Plus size={15} />} onClick={() => setMaterialForm({})}>Thêm nguyên liệu</Button>}
            <Button variant="primary" icon={<Plus size={15} />} onClick={openForm}>Tạo phiếu nhập nguyên liệu</Button>
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
              <FilterSelect label="Khu" value={rawZone} onChange={setRawZone}>
                {rawZones.map((z) => <option key={z.id} value={z.id}>{z.zone ?? z.code}</option>)}
              </FilterSelect>
            </FilterBar>
            <DataTable columns={rawColumns} rows={raw.data} rowKey={(m) => m.id} loading={raw.isLoading} error={raw.error} onRetry={raw.refetch} emptyText="Chưa có nguyên liệu" />
          </>
        )}
      </Card>

      <Modal
        open={open}
        title="Tạo phiếu nhập nguyên liệu"
        width="xl"
        onClose={() => setOpen(false)}
        onSubmit={() => {
          setTouched(true);
          if (sourceValid && linesValid) create.mutate();
        }}
        footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Lưu phiếu chờ nhập</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field
            label="Nguồn hàng"
            required
            error={touched && !sourceValid ? 'Vui lòng nhập nguồn hàng' : undefined}
            tip="Ghi nơi cung cấp nguyên liệu, ví dụ tên tàu, nhà cung cấp hoặc người bán."
          >
            <Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="Ví dụ: Chợ đầu mối / Nhà cung cấp đá" />
          </Field>
          <Field label="Ngày dự kiến về" tip="Phiếu này chỉ là phiếu hẹn, chưa cộng vào kho. Khi hàng về, bấm nút “Nhập kho” ở phiếu để ghi số hàng thật và giá mua.">
            <DateInput min={todayISO()} className="sm:w-[200px]" value={expectedDate} onChange={setExpectedDate} />
          </Field>
        </div>
        <div className="mt-4 overflow-x-auto overflow-y-hidden">
          <ul className="space-y-2.5">
            {rawRows.map((r, i) => {
              const patch = (p: Partial<RawRow>) => setRawRows(rawRows.map((x) => (x.key === r.key ? { ...x, ...p } : x)));
              const material = rawCatalog.data?.find((m) => String(m.id) === r.rawMaterialId);
              const unit = material ? unitLabel(material.unit) : '';
              const duplicate = !!r.rawMaterialId && rawRows.some((x, j) => j < i && x.rawMaterialId === r.rawMaterialId);
              const err = touched ? rawRowErrors(r, duplicate) : null;
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
                      error={err?.material}
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
                      error={err?.qty}
                      tip="Số hàng bạn hẹn nhận. Khi hàng về sẽ ghi số thật, khác số này cũng được."
                    >
                      <Input type="number" min={0} step="0.1" className="text-right" placeholder="0" value={r.qty} onChange={(e) => patch({ qty: e.target.value })} />
                    </Field>
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button icon={<Plus size={14} />} onClick={() => setRawRows([...rawRows, rawRow()])}>Thêm nguyên liệu</Button>
          <span className="inline-flex items-center gap-1">
            <Button variant="ghost" onClick={() => setMaterialForm({ fromReceipt: true })}>+ Tạo nguyên liệu mới</Button>
            <InfoTip label="Ghi chú tạo nguyên liệu mới">Dùng khi nguyên liệu chưa có trong danh sách chọn. Tạo xong sẽ tự điền vào phiếu.</InfoTip>
          </span>
        </div>
        {touched && !linesValid && <p className="mt-2 text-small text-red-fg">Mỗi nguyên liệu cần được chọn (không trùng nhau) và số lượng dự kiến lớn hơn 0.</p>}
      </Modal>

      <RawReceiptCompleteModal receipt={completing} onClose={() => setCompleting(null)} />
      <FinishedReceiptCompleteModal receipt={completingFinished} context={null} onClose={() => setCompletingFinished(null)} />
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
