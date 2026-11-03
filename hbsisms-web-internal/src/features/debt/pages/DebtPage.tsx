import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { HandCoins } from 'lucide-react';
import { Link } from 'react-router-dom';
import { PaymentFormModal } from '@/features/payments';
import { debtEntryLabels } from '@/shared/domain/enums';
import { date, dateTime, money } from '@/shared/lib/format';
import { Button, Card, Chip, DataTable, Drawer, FilterBar, InfoList, Meter, PageHeader, SearchBox, StatCard, StatGrid, useListParams, type Column } from '@/shared/ui';
import { debtApi } from '../api';
import type { DebtAgingRow, DebtLedgerRow } from '../types';

export default function DebtPage() {
  const list = useListParams();
  const aging = useQuery({ queryKey: ['debt-aging', list.q], queryFn: () => debtApi.aging(list.q) });
  const [selected, setSelected] = useState<DebtAgingRow | null>(null);
  const [paying, setPaying] = useState<DebtAgingRow | null>(null);
  const ledger = useQuery({ queryKey: ['debt-ledger', selected?.customerId], queryFn: () => debtApi.ledger(selected!.customerId), enabled: !!selected });
  const totals = aging.data?.totals;

  const bucket = (value: number, danger?: boolean) => <span className={`num ${value > 0 && danger ? 'font-semibold text-red-fg' : value === 0 ? 'text-ink-faint' : ''}`}>{value === 0 ? '—' : money(value)}</span>;

  const columns: Column<DebtAgingRow>[] = [
    { key: 'customer', header: 'Khách hàng', cell: (r) => <button type="button" className="text-left font-medium text-primary hover:underline" onClick={() => setSelected(r)}>{r.customer}</button> },
    { key: 'limit', header: 'Sử dụng hạn mức', width: '150px', hideOnMobile: true, cell: (r) => <Meter value={r.currentDebt} max={r.creditLimit} label={`Hạn mức ${r.customer}`} /> },
    { key: 'debt', header: 'Tổng nợ', align: 'right', cell: (r) => <span className="num font-medium">{money(r.currentDebt)}</span> },
    { key: 'd0', header: 'Trong hạn', align: 'right', hideOnMobile: true, cell: (r) => bucket(r.d0) },
    { key: 'd1', header: '1–30 ngày', align: 'right', cell: (r) => bucket(r.d1_30, true) },
    { key: 'd31', header: '31–60 ngày', align: 'right', hideOnMobile: true, cell: (r) => bucket(r.d31_60, true) },
    { key: 'd60', header: 'Trên 60', align: 'right', hideOnMobile: true, cell: (r) => bucket(r.d60, true) },
    { key: 'pay', header: '', align: 'right', cell: (r) => r.currentDebt > 0 && <Button icon={<HandCoins size={14} />} onClick={() => setPaying(r)}>Thu tiền</Button> },
  ];
  const ledgerColumns: Column<DebtLedgerRow>[] = [
    { key: 'at', header: 'Ngày', cell: (d) => <span className="whitespace-nowrap">{dateTime(d.createdAt)}</span> },
    { key: 'type', header: 'Loại', cell: (d) => <Chip tone={d.amount > 0 ? 'amber' : 'green'}>{debtEntryLabels[d.entryType]}</Chip> },
    { key: 'order', header: 'Đơn', cell: (d) => (d.orderId ? <Link to={`/orders/${d.orderId}`} className="num whitespace-nowrap text-primary hover:underline">{d.orderCode}</Link> : '—') },
    { key: 'amount', header: 'Phát sinh', align: 'right', cell: (d) => <span className="num">{d.amount > 0 ? '+' : ''}{money(d.amount)}</span> },
    { key: 'balance', header: 'Số dư', align: 'right', cell: (d) => <span className="num">{money(d.balanceAfter)}</span> },
    { key: 'due', header: 'Hạn trả', cell: (d) => date(d.dueDate) },
  ];

  return (
    <>
      <PageHeader title="Công nợ" subtitle="Khách B2B, xếp theo số tiền quá hạn" />
      <StatGrid cols={5}>
        <StatCard label="Tổng công nợ" value={money(totals?.currentDebt ?? 0)} />
        <StatCard label="Trong hạn" value={money(totals?.d0 ?? 0)} />
        <StatCard label="Quá hạn 1–30 ngày" value={money(totals?.d1_30 ?? 0)} tone={totals?.d1_30 ? 'warning' : undefined} />
        <StatCard label="Quá hạn 31–60 ngày" value={money(totals?.d31_60 ?? 0)} tone={totals?.d31_60 ? 'danger' : undefined} />
        <StatCard label="Quá hạn trên 60 ngày" value={money(totals?.d60 ?? 0)} tone={totals?.d60 ? 'danger' : undefined} />
      </StatGrid>
      <Card flush>
        <FilterBar><SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm khách hàng..." /></FilterBar>
        <DataTable columns={columns} rows={aging.data?.rows} rowKey={(r) => r.customerId} loading={aging.isLoading} error={aging.error} onRetry={aging.refetch} emptyText="Không có khách B2B phù hợp" />
      </Card>

      <Drawer open={!!selected} title={selected?.customer ?? ''} onClose={() => setSelected(null)} width={640}>
        {selected && (
          <div className="space-y-4">
            <InfoList items={[['Người liên hệ', selected.contactName], ['Điện thoại', selected.phone], ['Kỳ hạn', `NET ${selected.netDays} ngày`], ['Hạn mức', <span className="num">{money(selected.creditLimit)}</span>], ['Nợ hiện tại', <span className="num font-semibold">{money(selected.currentDebt)}</span>]]} />
            <div>
              <h3 className="mb-2 text-h2">Sổ công nợ</h3>
              <p className="mb-2 text-small text-ink-faint">Sổ chỉ đọc; mọi thay đổi được ghi bằng bút toán mới.</p>
              <div className="rounded-md border border-line"><DataTable columns={ledgerColumns} rows={ledger.data} rowKey={(d) => d.id} loading={ledger.isLoading} error={ledger.error} onRetry={ledger.refetch} emptyText="Chưa có bút toán" /></div>
            </div>
          </div>
        )}
      </Drawer>
      <PaymentFormModal open={!!paying} onClose={() => setPaying(null)} customerId={paying?.customerId ?? null} customerName={paying?.customer ?? null} maxAmount={paying?.currentDebt ?? 0} />
    </>
  );
}
