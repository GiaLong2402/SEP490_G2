import { ApiError } from '@/shared/api/client';
import { mock } from '@/shared/api/mockServer';
import type { CostItemBalance, CostPeriod, CostRunningBalance } from './types';

const periods: CostPeriod[] = [];
const balances = new Map<number, CostItemBalance[]>();
let nextPeriodId = 1;
let nextBalanceId = 1;

const runningBalances: CostRunningBalance[] = [
  {
    id: 1,
    itemType: 'RAW_MATERIAL',
    itemId: 1,
    itemCode: 'NVL-DEMO',
    itemName: 'Nguyên liệu demo',
    unit: 'kg',
    qtyOnHand: 100,
    inventoryValue: 5_000_000,
    avgUnitCost: 50_000,
    updatedAt: new Date().toISOString(),
  },
  {
    id: 2,
    itemType: 'FINISHED',
    itemId: 1,
    itemCode: 'SKU-DEMO',
    itemName: 'Thành phẩm demo · Khay 1kg',
    unit: 'khay',
    qtyOnHand: 40,
    inventoryValue: 4_000_000,
    avgUnitCost: 100_000,
    updatedAt: new Date().toISOString(),
  },
];

mock('GET', '/cost-periods', () => [...periods].sort((a, b) => b.yearMonth.localeCompare(a.yearMonth)));

mock('GET', '/cost-periods/open', () => {
  const open = periods.find((p) => p.status === 'OPEN');
  if (!open) throw new ApiError(404, 'Chưa có kỳ giá vốn đang mở');
  return open;
});

mock('GET', '/cost-periods/running-balances', ({ query }) => {
  if (!query.itemType) return [...runningBalances];
  return runningBalances.filter((r) => r.itemType === query.itemType);
});

mock('GET', '/cost-periods/:id', ({ params }) => {
  const period = periods.find((p) => p.id === Number(params.id));
  if (!period) throw new ApiError(404, 'Không tìm thấy kỳ giá vốn');
  return period;
});

mock('GET', '/cost-periods/:id/balances', ({ params, query }) => {
  const rows = balances.get(Number(params.id)) ?? [];
  if (!query.itemType) return rows;
  return rows.filter((r) => r.itemType === query.itemType);
});

mock('POST', '/cost-periods', ({ body }) => {
  const yearMonth = String((body as { yearMonth?: string })?.yearMonth ?? '');
  if (!/^\d{4}-(0[1-9]|1[0-2])$/.test(yearMonth)) {
    throw new ApiError(400, 'yearMonth phải dạng yyyy-MM');
  }
  if (periods.some((p) => p.status === 'OPEN')) {
    throw new ApiError(409, 'Đang có kỳ giá vốn mở — khóa sổ trước khi mở kỳ mới');
  }
  if (periods.some((p) => p.yearMonth === yearMonth)) {
    throw new ApiError(409, `Kỳ ${yearMonth} đã tồn tại`);
  }
  const id = nextPeriodId++;
  const period: CostPeriod = {
    id,
    yearMonth,
    periodStart: `${yearMonth}-01`,
    status: 'OPEN',
    openedAt: new Date().toISOString(),
    openedByUserId: 1,
    closedAt: null,
    closedByUserId: null,
    reopenReason: null,
    balanceCount: 2,
  };
  periods.push(period);
  balances.set(id, [
    {
      id: nextBalanceId++,
      periodId: id,
      itemType: 'RAW_MATERIAL',
      itemId: 1,
      itemCode: 'NVL-DEMO',
      itemName: 'Nguyên liệu demo',
      openingQty: 100,
      openingValue: 5_000_000,
      inboundQty: 0,
      inboundValue: 0,
      outboundQty: 0,
      outboundValue: 0,
      closingQty: 100,
      closingValue: 5_000_000,
      avgUnitCost: 50_000,
    },
    {
      id: nextBalanceId++,
      periodId: id,
      itemType: 'FINISHED',
      itemId: 1,
      itemCode: 'SKU-DEMO',
      itemName: 'Thành phẩm demo · Khay 1kg',
      openingQty: 40,
      openingValue: 4_000_000,
      inboundQty: 0,
      inboundValue: 0,
      outboundQty: 0,
      outboundValue: 0,
      closingQty: 40,
      closingValue: 4_000_000,
      avgUnitCost: 100_000,
    },
  ]);
  return period;
});

mock('POST', '/cost-periods/:id/close', ({ params }) => {
  const period = periods.find((p) => p.id === Number(params.id));
  if (!period) throw new ApiError(404, 'Không tìm thấy kỳ giá vốn');
  if (period.status !== 'OPEN') throw new ApiError(409, 'Kỳ đã khóa — không khóa lại được');
  period.status = 'CLOSED';
  period.closedAt = new Date().toISOString();
  period.closedByUserId = 1;
  const rows = balances.get(period.id) ?? [];
  for (const row of rows) {
    const qtyBase = row.openingQty + row.inboundQty;
    if (qtyBase > 0) {
      row.avgUnitCost = Math.round(((row.openingValue + row.inboundValue) / qtyBase) * 100) / 100;
    }
    row.closingQty = Math.round((row.openingQty + row.inboundQty - row.outboundQty) * 1000) / 1000;
    if (row.avgUnitCost != null && row.closingQty > 0) {
      row.closingValue = Math.round(row.closingQty * row.avgUnitCost * 100) / 100;
      row.outboundValue = Math.round((row.openingValue + row.inboundValue - row.closingValue) * 100) / 100;
    } else if (row.closingQty === 0) {
      row.closingValue = 0;
      row.outboundValue = Math.round((row.openingValue + row.inboundValue) * 100) / 100;
    }
  }
  return period;
});

mock('POST', '/cost-periods/:id/reopen', ({ params, body }) => {
  const period = periods.find((p) => p.id === Number(params.id));
  if (!period) throw new ApiError(404, 'Không tìm thấy kỳ giá vốn');
  if (period.status !== 'CLOSED') throw new ApiError(409, 'Chỉ mở lại được kỳ đã khóa');
  if (periods.some((p) => p.status === 'OPEN')) {
    throw new ApiError(409, 'Đang có kỳ giá vốn mở — khóa hoặc hủy kỳ đó trước khi mở lại');
  }
  const latest = [...periods].sort((a, b) => b.yearMonth.localeCompare(a.yearMonth))[0];
  if (latest && latest.id !== period.id) {
    throw new ApiError(409, `Có kỳ sau (${latest.yearMonth}) — hủy kỳ sau trước khi mở lại`);
  }
  const reason = String((body as { reason?: string })?.reason ?? '').trim();
  if (reason.length < 1) throw new ApiError(400, 'Lý do mở lại là bắt buộc');
  period.status = 'OPEN';
  period.closedAt = null;
  period.closedByUserId = null;
  period.reopenReason = reason;
  period.openedAt = new Date().toISOString();
  period.openedByUserId = 1;
  return period;
});

mock('POST', '/cost-periods/:id/discard', ({ params, body }) => {
  const idx = periods.findIndex((p) => p.id === Number(params.id));
  if (idx < 0) throw new ApiError(404, 'Không tìm thấy kỳ giá vốn');
  const period = periods[idx];
  if (period.status !== 'OPEN') throw new ApiError(409, 'Chỉ hủy được kỳ đang mở');
  const latest = [...periods].sort((a, b) => b.yearMonth.localeCompare(a.yearMonth))[0];
  if (!latest || latest.id !== period.id) {
    throw new ApiError(409, 'Chỉ hủy được kỳ tháng mới nhất đang mở');
  }
  const reason = String((body as { reason?: string })?.reason ?? '').trim();
  if (reason.length < 1) throw new ApiError(400, 'Lý do hủy là bắt buộc');
  periods.splice(idx, 1);
  balances.delete(period.id);
});
