import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Printer } from 'lucide-react';
import { date, num, todayISO } from '@/shared/lib/format';
import { Button, Card, DataTable, ErrorState, FilterBar, Input, PageHeader, PageLoading, type Column } from '@/shared/ui';
import { useDailyPickingSlip } from '../hooks';
import type { DailyPickingGroup, DailyPickingLine } from '../types';

type FlatRow = DailyPickingLine & {
  locationCode: string | null;
  warehouseCode: string | null;
  zone: string | null;
  groupKey: string;
};

export default function PickingPage() {
  const [dateFilter, setDateFilter] = useState(todayISO());
  const { data, isLoading, error, refetch } = useDailyPickingSlip(dateFilter || null);

  const rows = useMemo<FlatRow[]>(() => {
    if (!data?.groups) return [];
    return data.groups.flatMap((g: DailyPickingGroup) =>
      g.lines.map((l) => ({
        ...l,
        locationCode: g.locationCode,
        warehouseCode: g.warehouseCode,
        zone: g.zone,
        groupKey: `${g.warehouseCode ?? ''}:${g.locationCode ?? g.locationId}`,
      })),
    );
  }, [data]);

  const totalKhay = data?.groups.reduce((s, g) => s + g.totalKhay, 0) ?? 0;

  const columns: Column<FlatRow>[] = [
    {
      key: 'loc',
      header: 'Vị trí',
      cell: (r) => (
        <div>
          <div className="num font-medium">{r.locationCode ?? '—'}</div>
          <div className="text-small text-ink-faint">{r.warehouseCode ?? ''}{r.zone ? ` · ${r.zone}` : ''}</div>
        </div>
      ),
    },
    {
      key: 'order',
      header: 'Đơn',
      cell: (r) => (
        <Link to={`/orders/${r.orderId}`} className="num whitespace-nowrap text-primary hover:underline">
          {r.orderCode}
        </Link>
      ),
    },
    {
      key: 'product',
      header: 'Sản phẩm',
      cell: (r) => (
        <div>
          <div className="font-medium">{r.productName ?? '—'}</div>
          <div className="num text-ink-faint">{r.sku}{r.packLabel ? ` · ${r.packLabel}` : ''}</div>
        </div>
      ),
    },
    {
      key: 'lot',
      header: 'Lô · HSD',
      hideOnMobile: true,
      cell: (r) => (
        <div>
          <div className="num">{r.lotCode ?? '—'}</div>
          <div className="text-small text-ink-muted">{date(r.expDate)}</div>
        </div>
      ),
    },
    { key: 'qty', header: 'Khay', align: 'right', cell: (r) => <span className="num font-medium">{num(r.qtyKhay)}</span> },
  ];

  return (
    <>
      <PageHeader
        title="Soạn hàng"
        subtitle="Bản gộp theo vị trí từ HOLD còn hiệu lực — chọn ngày giao để in phiếu soạn."
        actions={
          <Button
            variant="primary"
            icon={<Printer size={14} />}
            disabled={!data?.groups.length}
            onClick={() => window.print()}
          >
            In bản gộp
          </Button>
        }
      />

      <FilterBar>
        <Input
          type="date"
          value={dateFilter}
          onChange={(e) => setDateFilter(e.target.value)}
          aria-label="Ngày giao / soạn"
        />
        {data && (
          <span className="text-small text-ink-muted">
            {data.groups.length} vị trí · {num(totalKhay)} khay
          </span>
        )}
      </FilterBar>

      {isLoading && <PageLoading />}
      {error && <ErrorState error={error} onRetry={refetch} />}
      {!isLoading && !error && data && (
        <>
          <Card flush>
            <DataTable
              columns={columns}
              rows={rows}
              rowKey={(r) => `${r.groupKey}-${r.orderId}-${r.variantId}-${r.lotId}-${r.qtyKhay}`}
              emptyText="Không có hàng cần soạn cho ngày này (cần đơn paid + giữ đủ, lịch giao khớp ngày)."
            />
          </Card>

          <div className="print-receipt print-only">
            <div className="text-center">
              <div className="text-h2">Hải sản Hòa Bình</div>
              <div className="text-small font-medium text-ink-muted">Phiếu soạn hàng — bản gộp ngày</div>
              <div className="mt-1 num">{date(data.date)}</div>
            </div>
            {data.groups.length === 0 ? (
              <p className="mt-4 text-center">Không có dòng soạn.</p>
            ) : (
              data.groups.map((g) => (
                <section key={`${g.warehouseCode}-${g.locationId}`} className="mt-4 break-inside-avoid">
                  <h3 className="border-b border-line pb-1 text-h2">
                    {g.locationCode ?? '—'}
                    {g.warehouseCode ? ` · ${g.warehouseCode}` : ''}
                    {g.zone ? ` · ${g.zone}` : ''}
                    <span className="ml-2 text-small font-normal text-ink-muted">{num(g.totalKhay)} khay</span>
                  </h3>
                  <table className="mt-1 w-full text-left text-small">
                    <thead>
                      <tr className="text-ink-muted">
                        <th className="py-1 pr-2 font-medium">Đơn</th>
                        <th className="py-1 pr-2 font-medium">SKU / SP</th>
                        <th className="py-1 pr-2 font-medium">Lô · HSD</th>
                        <th className="py-1 text-right font-medium">Khay</th>
                      </tr>
                    </thead>
                    <tbody>
                      {g.lines.map((l, i) => (
                        <tr key={`${l.orderId}-${l.lotId}-${i}`} className="border-t border-line/50 align-top">
                          <td className="py-1 pr-2 num whitespace-nowrap">{l.orderCode}</td>
                          <td className="py-1 pr-2">
                            <div className="font-medium">{l.productName ?? '—'}</div>
                            <div className="num text-ink-faint">{l.sku}</div>
                          </td>
                          <td className="py-1 pr-2">
                            <div className="num">{l.lotCode ?? '—'}</div>
                            <div>{date(l.expDate)}</div>
                          </td>
                          <td className="py-1 text-right num font-medium">{num(l.qtyKhay)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </section>
              ))
            )}
            <p className="mt-4 text-center text-micro text-ink-faint">In từ HOLD — sắp FEFO theo vị trí</p>
          </div>
        </>
      )}
    </>
  );
}
