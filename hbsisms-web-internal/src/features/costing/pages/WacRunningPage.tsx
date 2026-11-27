import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { dateTime, money, num } from '@/shared/lib/format';
import {
  Card,
  Chip,
  DataTable,
  FilterBar,
  FilterSelect,
  PageHeader,
  Pagination,
  SearchBox,
  StatCard,
  type Column,
} from '@/shared/ui';
import { costingApi } from '../api';
import type { CostItemType, CostRunningBalance } from '../types';

const PAGE_SIZE = 20;

const itemTypeLabel: Record<CostItemType, string> = {
  RAW_MATERIAL: 'Nguyên liệu',
  FINISHED: 'Thành phẩm',
};

function unitOf(r: CostRunningBalance) {
  const u = (r.unit ?? '').trim();
  if (u) return u;
  return r.itemType === 'FINISHED' ? 'khay' : '';
}

export default function WacRunningPage() {
  const [itemType, setItemType] = useState<'' | CostItemType>('');
  const [qText, setQText] = useState('');
  const [page, setPage] = useState(1);

  const q = useQuery({
    queryKey: ['cost-running-balances', itemType || null],
    queryFn: () => costingApi.runningBalances(itemType || undefined),
  });

  const rows = q.data ?? [];

  const filtered = useMemo(() => {
    const needle = qText.trim().toLowerCase();
    if (!needle) return rows;
    return rows.filter(
      (r) =>
        r.itemCode.toLowerCase().includes(needle) ||
        r.itemName.toLowerCase().includes(needle),
    );
  }, [rows, qText]);

  useEffect(() => {
    setPage(1);
  }, [itemType, qText]);

  useEffect(() => {
    const pages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
    if (page > pages) setPage(pages);
  }, [filtered.length, page]);

  const pageRows = useMemo(() => {
    const start = (page - 1) * PAGE_SIZE;
    return filtered.slice(start, start + PAGE_SIZE);
  }, [filtered, page]);

  const inventoryValue = useMemo(
    () => filtered.reduce((s, r) => s + Number(r.inventoryValue), 0),
    [filtered],
  );

  const columns: Column<CostRunningBalance>[] = [
    {
      key: 'type',
      header: 'Loại',
      cell: (r) => (
        <Chip tone={r.itemType === 'RAW_MATERIAL' ? 'amber' : 'green'}>{itemTypeLabel[r.itemType]}</Chip>
      ),
    },
    {
      key: 'item',
      header: 'Mặt hàng',
      cell: (r) => (
        <div>
          <div className="font-medium text-ink">{r.itemName}</div>
          <div className="text-micro text-ink-faint">{r.itemCode}</div>
        </div>
      ),
    },
    {
      key: 'wac',
      header: 'Đơn giá vốn',
      align: 'right',
      cell: (r) => (
        <span className="num text-body font-semibold text-ink">
          {r.avgUnitCost == null ? '—' : money(r.avgUnitCost)}
        </span>
      ),
    },
    {
      key: 'qty',
      header: 'SL tồn',
      align: 'right',
      cell: (r) => {
        const u = unitOf(r);
        return (
          <span className="num text-ink-muted">
            {num(r.qtyOnHand)}
            {u ? <span className="ml-1 font-sans text-micro text-ink-faint">{u}</span> : null}
          </span>
        );
      },
    },
    {
      key: 'gt',
      header: 'GT tồn',
      align: 'right',
      cell: (r) => <span className="num text-ink-muted">{money(r.inventoryValue)}</span>,
    },
    {
      key: 'updated',
      header: 'Cập nhật',
      hideOnMobile: true,
      cell: (r) => <span className="text-small text-ink-faint">{dateTime(r.updatedAt)}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        title="Giá vốn tồn kho"
        subtitle="Đơn giá vốn bình quân hiện hành (NVL & thành phẩm) — cập nhật mỗi lần nhập/xuất, không cần mở kỳ"
      />

      <div className="mb-4 max-w-sm">
        <StatCard label="Tổng GT tồn (theo bộ lọc)" value={money(inventoryValue)} />
      </div>

      <Card flush>
        <FilterBar>
          <SearchBox value={qText} onChange={setQText} placeholder="Tìm mã, tên mặt hàng..." />
          <FilterSelect label="Loại" value={itemType} onChange={(v) => setItemType((v as CostItemType) || '')}>
            <option value="RAW_MATERIAL">Nguyên liệu</option>
            <option value="FINISHED">Thành phẩm</option>
          </FilterSelect>
        </FilterBar>
        <DataTable
          columns={columns}
          rows={pageRows}
          rowKey={(r) => r.id}
          loading={q.isLoading}
          error={q.error}
          onRetry={q.refetch}
          emptyText="Chưa có giá vốn tồn — nhập NVL hoặc nhập thành phẩm sau SX để tạo đơn giá"
        />
        <Pagination page={page} pageSize={PAGE_SIZE} total={filtered.length} onChange={setPage} />
      </Card>
    </>
  );
}
