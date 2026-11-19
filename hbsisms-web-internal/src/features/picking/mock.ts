import { fail, mock } from '@/shared/api/mockServer';
import { API_PATHS } from '@/shared/api/httpPaths';
import { db } from '@/shared/mock/db';
import { customerName } from '@/shared/mock/services';
import type { DailyPickingGroup, DailyPickingLine, OrderPickingSlip, PickingSlipLine } from './types';

type HoldKey = { variantId: number; lotId: number; locationId: number };

function productOf(variantId: number) {
  const variant = db.variants.find((v) => v.id === variantId);
  const product = variant ? db.products.find((p) => p.id === variant.productId) : null;
  return { productName: product?.name ?? null, sku: variant?.sku ?? '—', packLabel: variant?.packLabel ?? null };
}

function locationMeta(locationId: number | null) {
  const location = locationId == null ? null : db.locations.find((l) => l.id === locationId);
  const warehouse = location ? db.warehouses.find((w) => w.id === location.warehouseId) : null;
  return {
    warehouseId: warehouse?.id ?? null,
    warehouseCode: warehouse?.code ?? null,
    locationId: location?.id ?? null,
    locationCode: location?.code ?? null,
    zone: location?.zone ?? null,
  };
}

/** Net HOLD còn hiệu lực theo lô/vị trí từ ledger (hold − release). */
function netHoldsFromLedger(orderId: number): Map<string, HoldKey & { qty: number }> {
  const lineIds = new Set(db.orderLines.filter((l) => l.orderId === orderId).map((l) => l.id));
  const map = new Map<string, HoldKey & { qty: number }>();
  for (const e of db.ledger) {
    if (e.refType !== 'sales_order_line' || e.refId == null || !lineIds.has(e.refId)) continue;
    if (e.lotId == null || e.locationId == null) continue;
    if (e.entryType !== 'hold' && e.entryType !== 'release') continue;
    const key = `${e.variantId}:${e.lotId}:${e.locationId}`;
    const row = map.get(key) ?? { variantId: e.variantId, lotId: e.lotId, locationId: e.locationId, qty: 0 };
    // hold: qtyDelta âm; release: dương — cả hai cộng -qtyDelta vào net held.
    row.qty += -e.qtyDelta;
    map.set(key, row);
  }
  for (const [k, v] of [...map.entries()]) {
    if (v.qty <= 0) map.delete(k);
  }
  return map;
}

/** Seed mock thường không ghi HOLD theo dòng đơn — suy ra FEFO từ tồn đang giữ/khả dụng. */
function synthesizeHolds(orderId: number): Map<string, HoldKey & { qty: number }> {
  const map = new Map<string, HoldKey & { qty: number }>();
  for (const line of db.orderLines.filter((l) => l.orderId === orderId)) {
    let remaining = line.qtyKhay;
    const rows = db.stock
      .filter((s) => s.variantId === line.variantId && s.qtyHeld + s.qtyAvailable > 0)
      .sort((a, b) => {
        const ea = db.lots.find((l) => l.id === a.lotId)?.expDate ?? '9999';
        const eb = db.lots.find((l) => l.id === b.lotId)?.expDate ?? '9999';
        return ea.localeCompare(eb);
      });
    for (const s of rows) {
      if (remaining <= 0) break;
      const pool = s.qtyHeld > 0 ? s.qtyHeld : s.qtyAvailable;
      const take = Math.min(remaining, pool);
      if (take <= 0) continue;
      const key = `${s.variantId}:${s.lotId}:${s.locationId}`;
      const prev = map.get(key);
      map.set(key, { variantId: s.variantId, lotId: s.lotId, locationId: s.locationId, qty: (prev?.qty ?? 0) + take });
      remaining -= take;
    }
  }
  return map;
}

function holdsForOrder(orderId: number) {
  const fromLedger = netHoldsFromLedger(orderId);
  if (fromLedger.size > 0) return fromLedger;
  const order = db.orders.find((o) => o.id === orderId);
  if (order && (order.allocationStatus === 'full' || order.allocationStatus === 'held')) {
    return synthesizeHolds(orderId);
  }
  return fromLedger;
}

function toSlipLines(holds: Map<string, HoldKey & { qty: number }>): PickingSlipLine[] {
  const lines: PickingSlipLine[] = [];
  for (const h of holds.values()) {
    if (h.qty <= 0) continue;
    const { productName, sku, packLabel } = productOf(h.variantId);
    const lot = db.lots.find((l) => l.id === h.lotId);
    lines.push({
      variantId: h.variantId,
      productName,
      sku,
      packLabel,
      lotId: h.lotId,
      lotCode: lot?.lotCode ?? null,
      expDate: lot?.expDate ?? null,
      ...locationMeta(h.locationId),
      qtyKhay: h.qty,
    });
  }
  return lines.sort((a, b) =>
    (a.expDate ?? '').localeCompare(b.expDate ?? '')
    || (a.warehouseCode ?? '').localeCompare(b.warehouseCode ?? '')
    || (a.locationCode ?? '').localeCompare(b.locationCode ?? ''),
  );
}

function matchesShipDate(orderId: number, date: string) {
  const deliveries = db.deliveries.filter((d) => d.orderId === orderId);
  if (deliveries.length === 0) return true;
  return deliveries.some((d) => d.scheduledDate === date);
}

/** Đăng ký trước `/orders/:id` để không bị nuốt path `picking-slip-daily`. */
mock('GET', API_PATHS.orders.pickingSlipDaily, ({ query }) => {
  const date = String(query.date ?? '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) fail(400, 'Thiếu hoặc sai tham số date (yyyy-MM-dd)');

  const candidates = db.orders.filter(
    (o) =>
      ['confirmed', 'preparing', 'to_ship'].includes(o.state)
      && o.paymentStatus === 'paid'
      && o.allocationStatus === 'full'
      && matchesShipDate(o.id, date),
  );

  const byLocation = new Map<number, DailyPickingGroup>();
  for (const order of candidates) {
    for (const line of toSlipLines(holdsForOrder(order.id))) {
      if (line.locationId == null || line.qtyKhay <= 0) continue;
      const group = byLocation.get(line.locationId) ?? {
        warehouseId: line.warehouseId,
        warehouseCode: line.warehouseCode,
        locationId: line.locationId,
        locationCode: line.locationCode,
        zone: line.zone,
        totalKhay: 0,
        lines: [] as DailyPickingLine[],
      };
      group.lines.push({
        orderId: order.id,
        orderCode: order.code,
        variantId: line.variantId,
        productName: line.productName,
        sku: line.sku,
        packLabel: line.packLabel,
        lotId: line.lotId,
        lotCode: line.lotCode,
        expDate: line.expDate,
        qtyKhay: line.qtyKhay,
      });
      group.totalKhay += line.qtyKhay;
      byLocation.set(line.locationId, group);
    }
  }

  const groups = [...byLocation.values()]
    .map((g) => {
      g.lines.sort((a, b) => (a.expDate ?? '').localeCompare(b.expDate ?? '') || a.orderCode.localeCompare(b.orderCode));
      return g;
    })
    .sort((a, b) =>
      (a.warehouseCode ?? '').localeCompare(b.warehouseCode ?? '')
      || (a.locationCode ?? '').localeCompare(b.locationCode ?? ''),
    );

  return { date, generatedAt: new Date().toISOString(), groups };
});

mock('GET', '/orders/:id/picking-slip', ({ params }) => {
  const order = db.orders.find((o) => o.id === Number(params.id));
  if (!order) fail(404, 'Không tìm thấy đơn hàng');
  const slip: OrderPickingSlip = {
    orderId: order.id,
    orderCode: order.code,
    customerName: customerName(order.customerId) ?? (order.customerId == null ? 'Khách vãng lai' : null),
    generatedAt: new Date().toISOString(),
    lines: toSlipLines(holdsForOrder(order.id)),
  };
  return slip;
});
