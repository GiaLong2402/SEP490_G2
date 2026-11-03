import { matches, mock } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';
import { debtAging } from '@/shared/mock/services';

mock('GET', '/debt/aging', ({ query }) => {
  const rows = db.customers
    .filter((c) => c.customerType === 'b2b' && matches(query.q, c.name, c.code, c.contactName))
    .map((c) => {
      const b = debtAging(c.id);
      return { customerId: c.id, customer: c.name, code: c.code, contactName: c.contactName, phone: c.contactPhone ?? c.phone, creditLimit: c.creditLimit, netDays: c.netDays, currentDebt: c.currentDebt, ...b, overdue: b.d1_30 + b.d31_60 + b.d60 };
    })
    .sort((a, b) => b.overdue - a.overdue || b.currentDebt - a.currentDebt);
  const sum = (key: 'currentDebt' | 'd0' | 'd1_30' | 'd31_60' | 'd60') => rows.reduce((s, r) => s + r[key], 0);
  return { rows, totals: { currentDebt: sum('currentDebt'), d0: sum('d0'), d1_30: sum('d1_30'), d31_60: sum('d31_60'), d60: sum('d60') } };
});

mock('GET', '/debt/:customerId/ledger', ({ params }) =>
  db.debtLedger
    .filter((d) => d.customerId === Number(params.customerId))
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id)
    .map((d) => ({ ...d, orderCode: db.orders.find((o) => o.id === d.orderId)?.code ?? null })),
);
