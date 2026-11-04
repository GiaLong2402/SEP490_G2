import { useEffect, useState } from 'react';
import { Pencil, Plus, Trash2 } from 'lucide-react';
import { useSearchParams } from 'react-router-dom';
import { useLocations } from '@/features/inventory';
import { useVariantOptions } from '@/features/products';
import { useAction } from '@/shared/api/useAction';
import { receiptTypeLabels, type ReceiptType } from '@/shared/domain/enums';
import { dateTime, num, todayISO } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, Field, FilterBar, FilterSelect, IconButton, Input, Modal, PageHeader, Pagination, SearchBox, Select, Tabs, enumOptions, useListParams, type Column } from '@/shared/ui';
import { receiptsApi } from '../api';
import { RawMaterialFormModal } from '../components/RawMaterialFormModal';
import { useRawMaterials, useReceipts } from '../hooks';
import type { RawMaterial, ReceiptListItem } from '../types';

interface FinishedRow { key: number; variantId: string; lotCode: string; mfgDate: string; expDate: string; locationId: string; qty: string }
interface RawRow { key: number; rawMaterialId: string; qty: string }
let rowKey = 1;
const finishedRow = (): FinishedRow => ({ key: rowKey++, variantId: '', lotCode: '', mfgDate: todayISO(), expDate: todayISO(14), locationId: '', qty: '' });
const rawRow = (): RawRow => ({ key: rowKey++, rawMaterialId: '', qty: '' });

export default function ReceiptsPage() {
  const [params, setParams] = useSearchParams();
  const [tab, setTab] = useState<'receipts' | 'raw'>('receipts');
  const list = useListParams(['receiptType'] as const);
  const { data, isLoading, error, refetch } = useReceipts({ q: list.q, page: list.page, receiptType: list.filters.receiptType });
  const raw = useRawMaterials();
  const variants = useVariantOptions().data ?? [];
  const locations = useLocations().data ?? [];

  const [open, setOpen] = useState(false);
  const [type, setType] = useState<ReceiptType>('finished');
  const [source, setSource] = useState('');
  const [finished, setFinished] = useState<FinishedRow[]>([finishedRow()]);
  const [rawRows, setRawRows] = useState<RawRow[]>([rawRow()]);
  const [touched, setTouched] = useState(false);
  const [materialForm, setMaterialForm] = useState<{ material?: RawMaterial; fromReceipt?: boolean } | null>(null);

  const openForm = () => {
    setType('finished');
    setSource('');
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

  const finishedValid = finished.every((r) => Number(r.variantId) > 0 && Number(r.locationId) > 0 && r.expDate && Number.isInteger(Number(r.qty)) && Number(r.qty) > 0 && (!r.mfgDate || r.expDate >= r.mfgDate));
  const rawValid = rawRows.every((r) => Number(r.rawMaterialId) > 0 && Number(r.qty) > 0);
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
      }),
    { success: (r) => `Đã tạo phiếu nhập ${r.code}`, onDone: () => setOpen(false) },
  );

  const columns: Column<ReceiptListItem>[] = [
    { key: 'code', header: 'Mã phiếu', cell: (r) => <span className="num">{r.code}</span> },
    { key: 'type', header: 'Loại', cell: (r) => <Chip tone={r.receiptType === 'raw' ? 'amber' : 'blue'}>{receiptTypeLabels[r.receiptType]}</Chip> },
    { key: 'source', header: 'Nguồn', cell: (r) => <span className="font-medium">{r.source?.trim() || r.supplierName || '—'}</span> },
    { key: 'items', header: 'Hàng nhập', cell: (r) => <span className="line-clamp-2 min-w-[220px]">{r.items.map((i) => `${i.name}: ${num(i.qty)} ${i.unit}`).join('; ')}</span> },
    { key: 'at', header: 'Thời gian', hideOnMobile: true, cell: (r) => <span className="whitespace-nowrap">{dateTime(r.receivedAt)}</span> },
    { key: 'by', header: 'Người nhận', hideOnMobile: true, cell: (r) => r.receiverName ?? '—' },
  ];
  const rawColumns: Column<RawMaterial>[] = [
    { key: 'code', header: 'Mã', cell: (m) => <span className="num">{m.code}</span> },
    { key: 'name', header: 'Nguyên liệu', cell: (m) => <span className="font-medium">{m.name}</span> },
    { key: 'qty', header: 'Tồn', align: 'right', cell: (m) => <span className="num">{num(m.qtyOnHand)} {m.unit}</span> },
    { key: 'reorder', header: 'Mức đặt lại', align: 'right', cell: (m) => <span className="num">{num(m.reorderLevel)} {m.unit}</span> },
    { key: 'status', header: 'Tình trạng', cell: (m) => (!m.isActive ? <Chip>Ngừng dùng</Chip> : m.reorderLevel !== null && m.qtyOnHand < m.reorderLevel ? <Chip tone="red">Cần đặt thêm</Chip> : <Chip tone="green">Đủ dùng</Chip>) },
    { key: 'edit', header: '', align: 'right', cell: (m) => <IconButton label={`Sửa ${m.name}`} onClick={() => setMaterialForm({ material: m })}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader title="Nhập kho" subtitle="Phiếu nhập thành phẩm, nguyên liệu và tồn nguyên liệu" actions={<>{tab === 'raw' && <Button icon={<Plus size={15} />} onClick={() => setMaterialForm({})}>Thêm nguyên liệu</Button>}<Button variant="primary" icon={<Plus size={15} />} onClick={openForm}>Tạo phiếu nhập</Button></>} />
      <Card flush>
        <Tabs label="Nhập kho" value={tab} onChange={setTab} tabs={[{ key: 'receipts', label: 'Phiếu nhập' }, { key: 'raw', label: 'Tồn nguyên liệu' }]} />
        {tab === 'receipts' ? (
          <>
            <FilterBar>
              <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm mã phiếu, nguồn hàng..." />
              <FilterSelect label="Loại" value={list.filters.receiptType} onChange={(v) => list.setFilter('receiptType', v)}>{enumOptions(receiptTypeLabels)}</FilterSelect>
            </FilterBar>
            <DataTable columns={columns} rows={data?.items} rowKey={(r) => r.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Chưa có phiếu nhập" />
            {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
          </>
        ) : (
          <DataTable columns={rawColumns} rows={raw.data} rowKey={(m) => m.id} loading={raw.isLoading} error={raw.error} onRetry={raw.refetch} emptyText="Chưa có nguyên liệu" />
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
        footer={<><Button onClick={() => setOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={create.isPending}>Lưu phiếu</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Loại phiếu" required><Select value={type} onChange={(e) => setType(e.target.value as ReceiptType)}>{enumOptions(receiptTypeLabels)}</Select></Field>
          <Field
            label="Nguồn hàng"
            required
            error={touched && !sourceValid ? 'Vui lòng nhập nguồn hàng' : undefined}
            hint="Ghi chú nơi lấy hàng (chưa liên kết nhà cung cấp)"
          >
            <Input value={source} onChange={(e) => setSource(e.target.value)} placeholder="Ví dụ: Xưởng chế biến Hòa Bình" />
          </Field>
        </div>
        <div className="mt-4 overflow-x-auto">
          {type === 'finished' ? (
            <table className="w-full min-w-[780px] text-small">
              <thead><tr className="text-left text-micro font-semibold uppercase text-ink-muted"><th className="pb-1 pr-2">Quy cách</th><th className="pr-2">Mã lô</th><th className="pr-2">Ngày SX</th><th className="pr-2">HSD</th><th className="pr-2">Vị trí</th><th className="w-20 pr-2">Số khay</th><th className="w-9" /></tr></thead>
              <tbody>
                {finished.map((r, i) => {
                  const patch = (p: Partial<FinishedRow>) => setFinished(finished.map((x) => (x.key === r.key ? { ...x, ...p } : x)));
                  return (
                    <tr key={r.key}>
                      <td className="py-1 pr-2"><Select aria-label={`Quy cách dòng ${i + 1}`} value={r.variantId} onChange={(e) => patch({ variantId: e.target.value })}><option value="">Chọn...</option>{variants.map((v) => <option key={v.id} value={v.id}>{v.productName} · {v.packLabel}</option>)}</Select></td>
                      <td className="pr-2"><Input aria-label={`Mã lô dòng ${i + 1}`} placeholder="Tự sinh" value={r.lotCode} onChange={(e) => patch({ lotCode: e.target.value })} /></td>
                      <td className="pr-2"><Input aria-label={`Ngày sản xuất dòng ${i + 1}`} type="date" value={r.mfgDate} onChange={(e) => patch({ mfgDate: e.target.value })} /></td>
                      <td className="pr-2"><Input aria-label={`Hạn sử dụng dòng ${i + 1}`} type="date" value={r.expDate} onChange={(e) => patch({ expDate: e.target.value })} /></td>
                      <td className="pr-2"><Select aria-label={`Vị trí dòng ${i + 1}`} value={r.locationId} onChange={(e) => patch({ locationId: e.target.value })}><option value="">Chọn...</option>{locations.map((l) => <option key={l.id} value={l.id}>{l.label}</option>)}</Select></td>
                      <td className="pr-2"><Input aria-label={`Số khay dòng ${i + 1}`} type="number" min={1} className="text-right" value={r.qty} onChange={(e) => patch({ qty: e.target.value })} /></td>
                      <td><IconButton label={`Xóa dòng ${i + 1}`} disabled={finished.length === 1} onClick={() => setFinished(finished.filter((x) => x.key !== r.key))}><Trash2 size={14} /></IconButton></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          ) : (
            <table className="w-full text-small">
              <thead><tr className="text-left text-micro font-semibold uppercase text-ink-muted"><th className="pb-1 pr-2">Nguyên liệu</th><th className="w-32 pr-2">Số lượng</th><th className="w-9" /></tr></thead>
              <tbody>
                {rawRows.map((r, i) => {
                  const patch = (p: Partial<RawRow>) => setRawRows(rawRows.map((x) => (x.key === r.key ? { ...x, ...p } : x)));
                  return (
                    <tr key={r.key}>
                      <td className="py-1 pr-2"><Select aria-label={`Nguyên liệu dòng ${i + 1}`} value={r.rawMaterialId} onChange={(e) => patch({ rawMaterialId: e.target.value })}><option value="">Chọn...</option>{raw.data?.filter((m) => m.isActive || String(m.id) === r.rawMaterialId).map((m) => <option key={m.id} value={m.id}>{m.name} ({m.unit})</option>)}</Select></td>
                      <td className="pr-2"><Input aria-label={`Số lượng dòng ${i + 1}`} type="number" min={0} step="0.1" className="text-right" value={r.qty} onChange={(e) => patch({ qty: e.target.value })} /></td>
                      <td><IconButton label={`Xóa dòng ${i + 1}`} disabled={rawRows.length === 1} onClick={() => setRawRows(rawRows.filter((x) => x.key !== r.key))}><Trash2 size={14} /></IconButton></td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>
        <div className="mt-3 flex flex-wrap gap-2">
          <Button icon={<Plus size={14} />} onClick={() => (type === 'finished' ? setFinished([...finished, finishedRow()]) : setRawRows([...rawRows, rawRow()]))}>Thêm dòng</Button>
          {type === 'raw' && <Button variant="ghost" onClick={() => setMaterialForm({ fromReceipt: true })}>+ Nguyên liệu mới</Button>}
        </div>
        {touched && !linesValid && <p className="mt-2 text-small text-red-fg">Mỗi dòng cần đủ thông tin, số lượng lớn hơn 0 và HSD không trước ngày sản xuất.</p>}
      </Modal>

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
