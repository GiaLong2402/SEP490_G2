import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { stocktakeStatusMeta } from '@/shared/domain/enums';
import { dateTime, num } from '@/shared/lib/format';
import { Alert, Button, Card, Chip, ConfirmDialog, DataTable, ErrorState, Input, PageHeader, PageLoading, StatCard, StatGrid, StatusChip, type Column } from '@/shared/ui';
import { stocktakesApi } from '../api';
import { useStocktake } from '../hooks';
import type { StocktakeLineView } from '../types';

export default function StocktakeDetailPage() {
  const id = Number(useParams().id);
  const { data: stocktake, isLoading, error, refetch } = useStocktake(id);
  const [counts, setCounts] = useState<Record<number, string>>({});
  const [cancelling, setCancelling] = useState(false);

  useEffect(() => {
    if (stocktake) setCounts(Object.fromEntries(stocktake.lines.map((l) => [l.id, l.countedQty === null ? '' : String(l.countedQty)])));
  }, [stocktake]);

  const toPayload = () => stocktake!.lines.map((l) => ({ lineId: l.id, countedQty: counts[l.id] === '' || counts[l.id] === undefined ? null : Number(counts[l.id]) }));
  const save = useAction(() => stocktakesApi.saveCounts(id, toPayload()), { success: 'Đã lưu số đếm' });
  const complete = useAction(
    async () => {
      await stocktakesApi.saveCounts(id, toPayload());
      return stocktakesApi.complete(id);
    },
    { success: (r) => (r.needsApproval ? 'Có chênh lệch, đã gửi Chủ cửa hàng duyệt' : 'Đã hoàn tất kiểm kê, không có chênh lệch') },
  );
  const cancel = useAction(() => stocktakesApi.cancel(id), { success: 'Đã hủy phiếu', onDone: () => setCancelling(false) });

  if (isLoading) return <PageLoading />;
  if (error || !stocktake) return <ErrorState error={error} onRetry={refetch} />;

  const editable = stocktake.status === 'counting' && !stocktake.pendingApproval;
  const invalid = Object.values(counts).some((v) => v !== '' && (!Number.isInteger(Number(v)) || Number(v) < 0));
  const allCounted = stocktake.lines.every((l) => counts[l.id] !== '' && counts[l.id] !== undefined);
  const liveDiff = (l: StocktakeLineView) => (counts[l.id] === '' || counts[l.id] === undefined ? null : Number(counts[l.id]) - l.systemQty);
  const totalDiff = stocktake.lines.reduce((sum, l) => sum + (liveDiff(l) ?? 0), 0);
  const counted = stocktake.lines.filter((l) => liveDiff(l) !== null).length;

  const columns: Column<StocktakeLineView>[] = [
    { key: 'loc', header: 'Vị trí', cell: (l) => <span className="num">{l.locationCode}</span> },
    { key: 'product', header: 'Sản phẩm', cell: (l) => <div><div className="font-medium">{l.productName}</div><div className="num text-ink-faint">{l.sku}</div></div> },
    { key: 'lot', header: 'Lô', cell: (l) => <span className="num">{l.lotCode ?? '—'}</span> },
    { key: 'system', header: 'Hệ thống', align: 'right', cell: (l) => <span className="num">{num(l.systemQty)}</span> },
    {
      key: 'counted', header: 'Thực đếm', align: 'right', width: '120px',
      cell: (l) => (editable ? <Input aria-label={`Số đếm ${l.sku} lô ${l.lotCode}`} type="number" min={0} className="!h-7 text-right" value={counts[l.id] ?? ''} onChange={(e) => setCounts({ ...counts, [l.id]: e.target.value })} /> : <span className="num">{num(l.countedQty)}</span>),
    },
    {
      key: 'diff', header: 'Chênh lệch', align: 'right',
      cell: (l) => {
        const diff = liveDiff(l);
        if (diff === null) return <span className="text-ink-faint">Chưa đếm</span>;
        if (diff === 0) return <Chip tone="green">Khớp</Chip>;
        return <Chip tone="red">{diff > 0 ? '+' : ''}{num(diff)}</Chip>;
      },
    },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/stocktakes" className="hover:underline">Kiểm kê</Link> / {stocktake.code}</>}
        title={<span className="flex items-center gap-2"><span className="font-mono">{stocktake.code}</span>{stocktake.pendingApproval ? <Chip tone="amber">Chờ duyệt chênh lệch</Chip> : <StatusChip meta={stocktakeStatusMeta} value={stocktake.status} />}</span>}
        subtitle={`${stocktake.scope} · Tạo bởi ${stocktake.creatorName ?? '—'} · Đếm bởi ${stocktake.counterName ?? '—'} · Bắt đầu ${dateTime(stocktake.startedAt)}${stocktake.closedAt ? ` · Kết thúc ${dateTime(stocktake.closedAt)}` : ''}`}
        actions={
          editable && (
            <>
              <Button variant="danger" onClick={() => setCancelling(true)}>Hủy phiếu</Button>
              <Button loading={save.isPending} disabled={invalid} onClick={() => save.mutate()}>Lưu tạm</Button>
              <Button variant="primary" loading={complete.isPending} disabled={invalid || !allCounted} onClick={() => complete.mutate()}>Hoàn tất đếm</Button>
            </>
          )
        }
      />
      <StatGrid cols={3}>
        <StatCard label="Đã đếm" value={`${num(counted)} / ${num(stocktake.lines.length)}`} />
        <StatCard label="Dòng chênh lệch" value={num(stocktake.lines.filter((l) => (liveDiff(l) ?? 0) !== 0).length)} />
        <StatCard label="Tổng chênh lệch (khay)" value={`${totalDiff > 0 ? '+' : ''}${num(totalDiff)}`} tone={totalDiff !== 0 ? 'danger' : undefined} />
      </StatGrid>
      {stocktake.note?.trim() && (
        <div className="mb-4"><Alert tone="info">Ghi chú: {stocktake.note}</Alert></div>
      )}
      {stocktake.pendingApproval && <div className="mb-4"><Alert tone="warning">Phiếu đang chờ Chủ cửa hàng duyệt. Tồn kho chỉ được điều chỉnh sau khi duyệt.</Alert></div>}
      {invalid && <div className="mb-4"><Alert tone="danger">Số đếm phải là số nguyên không âm.</Alert></div>}
      <Card flush><DataTable columns={columns} rows={stocktake.lines} rowKey={(l) => l.id} emptyText="Phiếu không có dòng nào" /></Card>
      <ConfirmDialog open={cancelling} danger title={`Hủy phiếu ${stocktake.code}?`} message="Số đếm đã nhập sẽ không được áp dụng vào tồn kho." confirmLabel="Hủy phiếu" loading={cancel.isPending} onConfirm={() => cancel.mutate()} onClose={() => setCancelling(false)} />
    </>
  );
}
