import { ArrowRightCircle, Send } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useAction } from '@/shared/api/useAction';
import { priceBasisMeta, quoteStatusMeta, sellUnitLabels } from '@/shared/domain/enums';
import { date, money, num } from '@/shared/lib/format';
import { Button, Card, DataTable, ErrorState, InfoList, PageHeader, PageLoading, StatusChip, type Column } from '@/shared/ui';
import { quotesApi } from '../api';
import { useQuote } from '../hooks';
import type { QuoteLineView, QuoteStatus } from '../types';

export default function QuoteDetailPage() {
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const { data: quote, isLoading, error, refetch } = useQuote(id);
  const setStatus = useAction((status: QuoteStatus) => quotesApi.setStatus(id, status), { success: 'Đã cập nhật báo giá' });
  const convert = useAction(() => quotesApi.convert(id), { success: (r) => `Đã tạo đơn ${r.orderCode}`, onDone: (r) => navigate(`/orders/${r.orderId}`) });

  if (isLoading) return <PageLoading />;
  if (error || !quote) return <ErrorState error={error} onRetry={refetch} />;

  const columns: Column<QuoteLineView>[] = [
    { key: 'product', header: 'Sản phẩm', cell: (l) => <div><div className="font-medium">{l.productName}</div><div className="num text-ink-faint">{l.sku} · {l.packLabel}</div></div> },
    { key: 'qty', header: 'SL', align: 'right', cell: (l) => <span className="num whitespace-nowrap">{num(l.qty)} {sellUnitLabels[l.unit]}</span> },
    { key: 'khay', header: 'Khay', align: 'right', cell: (l) => <span className="num">{num(l.qtyKhay)}</span> },
    { key: 'price', header: 'Đơn giá', align: 'right', cell: (l) => <span className="num">{money(l.unitPrice)}</span> },
    { key: 'basis', header: 'Nguồn giá', cell: (l) => <StatusChip meta={priceBasisMeta} value={l.priceBasis} /> },
    { key: 'total', header: 'Thành tiền', align: 'right', cell: (l) => <span className="num font-medium">{money(l.lineTotal)}</span> },
  ];

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/quotes" className="hover:underline">Báo giá</Link> / {quote.code}</>}
        title={<span className="flex items-center gap-2"><span className="font-mono">{quote.code}</span><StatusChip meta={quoteStatusMeta} value={quote.status} /></span>}
        actions={
          <>
            {quote.status === 'draft' && <Button variant="primary" icon={<Send size={14} />} loading={setStatus.isPending} onClick={() => setStatus.mutate('sent')}>Đánh dấu đã gửi</Button>}
            {quote.status === 'sent' && (
              <>
                <Button variant="danger" loading={setStatus.isPending} onClick={() => setStatus.mutate('rejected')}>Khách từ chối</Button>
                <Button variant="primary" loading={setStatus.isPending} onClick={() => setStatus.mutate('approved')}>Khách đồng ý</Button>
              </>
            )}
            {quote.status === 'approved' && !quote.convertedOrderId && <Button variant="primary" icon={<ArrowRightCircle size={14} />} loading={convert.isPending} onClick={() => convert.mutate()}>Chuyển thành đơn</Button>}
          </>
        }
      />
      <div className="grid items-start gap-4 lg:grid-cols-[1fr_300px]">
        <Card title="Dòng hàng" flush>
          <DataTable columns={columns} rows={quote.lines} rowKey={(l) => l.id} footer={<tr className="border-t border-line text-h2"><td colSpan={6} className="px-3 py-2 text-right"><span className="mr-3">Tổng cộng</span><span className="tabular-nums">{money(quote.total)}</span></td></tr>} />
        </Card>
        <Card title="Thông tin">
          <InfoList
            items={[
              ['Khách hàng', <Link to={`/customers/${quote.customerId}`} className="text-primary hover:underline">{quote.customerName}</Link>],
              ['Ngày lập', date(quote.createdAt)],
              ['Hiệu lực đến', date(quote.validUntil)],
              ['Người lập', quote.creatorName],
              ['Yêu cầu', quote.requestCode],
              ['Đơn hàng', quote.convertedOrderId ? <Link to={`/orders/${quote.convertedOrderId}`} className="text-primary hover:underline">{quote.convertedOrderCode}</Link> : null],
            ]}
          />
          {quote.note && <p className="mt-3 border-t border-line pt-3 text-ink-muted">{quote.note}</p>}
        </Card>
      </div>
    </>
  );
}
