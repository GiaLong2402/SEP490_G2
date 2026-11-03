import { mock } from '@/shared/api/mockServer';
import { orderTypeLabels, saleChannelLabels } from '@/shared/domain/enums';
import { db } from '@/shared/mock/db';
import { customerName, variantLabel } from '@/shared/mock/services';

const localDate = (d: Date) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;

const rank = (map: Map<string, number>, limit = 8) => [...map.entries()].map(([label, value]) => ({ label, value })).sort((a, b) => b.value - a.value).slice(0, limit);
const add = (map: Map<string, number>, key: string, value: number) => map.set(key, (map.get(key) ?? 0) + value);

mock('GET', '/reports/sales', ({ query }) => {
  const days = Math.min(90, Math.max(1, Number(query.days ?? 30)));
  const daily = Array.from({ length: days }, (_, i) => {
    const d = new Date();
    d.setDate(d.getDate() - (days - 1 - i));
    return { date: localDate(d), revenue: 0, orders: 0 };
  });
  const inRange = db.orders.filter((o) => localDate(new Date(o.placedAt)) >= daily[0].date);
  const sold = inRange.filter((o) => o.state !== 'cancelled');
  const channel = new Map<string, number>();
  const type = new Map<string, number>();
  const products = new Map<string, number>();
  const customers = new Map<string, number>();
  sold.forEach((o) => {
    const point = daily.find((p) => p.date === localDate(new Date(o.placedAt)));
    if (point) {
      point.revenue += o.total;
      point.orders += 1;
    }
    add(channel, o.channel ? saleChannelLabels[o.channel] : 'Khác', o.total);
    add(type, orderTypeLabels[o.orderType], o.total);
    add(customers, customerName(o.customerId) ?? 'Khách lẻ', o.total);
    db.orderLines.filter((l) => l.orderId === o.id).forEach((l) => {
      const label = variantLabel(l.variantId);
      add(products, `${label.productName} · ${label.packLabel}`, l.lineTotal);
    });
  });
  const revenue = sold.reduce((sum, o) => sum + o.total, 0);
  return {
    days, revenue, orderCount: sold.length, avgOrder: sold.length ? Math.round(revenue / sold.length) : 0, cancelledCount: inRange.length - sold.length, daily,
    byChannel: rank(channel), byOrderType: rank(type), topProducts: rank(products), topCustomers: rank(customers, 5),
  };
});
