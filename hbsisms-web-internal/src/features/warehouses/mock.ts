import { fail, mock } from '@/shared/api/mockServer';
import { db, nextId } from '@/shared/mock/db';
import { audit } from '@/shared/mock/services';

function toDetail(w: (typeof db.warehouses)[number]) {
  const locations = db.locations
    .filter((l) => l.warehouseId === w.id)
    .sort((a, b) => a.code.localeCompare(b.code))
    .map((l) => {
      const rows = db.stock.filter((s) => s.locationId === l.id);
      return { ...l, usedQty: rows.reduce((sum, s) => sum + s.qtyAvailable + s.qtyHeld + s.qtyQuarantine + s.qtyExpired, 0), canDelete: rows.length === 0 };
    });
  return {
    ...w,
    locations,
    totalQty: locations.reduce((sum, l) => sum + l.usedQty, 0),
    allocatedCapacity: locations.reduce((sum, l) => sum + l.capacity, 0),
  };
}

function requirePositiveCapacity(value: unknown, label: string) {
  if (!(Number.isInteger(Number(value)) && Number(value) > 0)) fail(400, `Sức chứa ${label} phải là số nguyên dương`);
  return Number(value);
}

function checkWarehouse(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code || !String(body.name ?? '').trim()) fail(400, 'Vui lòng nhập mã và tên kho');
  if (db.warehouses.some((w) => w.code === code && w.id !== id)) fail(409, `Mã kho ${code} đã tồn tại`);
  requirePositiveCapacity(body.capacity, 'kho');
  return code;
}

function checkLocation(body: any, warehouseId: number, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã vị trí');
  if (db.locations.some((l) => l.warehouseId === warehouseId && l.code === code && l.id !== id)) fail(409, `Vị trí ${code} đã có trong kho này`);
  requirePositiveCapacity(body.capacity, 'vị trí');
  return code;
}

mock('GET', '/warehouses', () => db.warehouses.map(toDetail));

mock('POST', '/warehouses', ({ body, userId }) => {
  const capacity = requirePositiveCapacity(body.capacity, 'kho');
  const row = { ...body, code: checkWarehouse(body, null), capacity, id: nextId(db.warehouses) };
  db.warehouses.push(row);
  audit(userId, 'create', 'warehouse', row.id);
  return toDetail(row);
});

mock('PUT', '/warehouses/:id', ({ params, body, userId }) => {
  const row = db.warehouses.find((w) => w.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy kho');
  const code = checkWarehouse(body, row.id);
  const capacity = requirePositiveCapacity(body.capacity, 'kho');
  const detail = toDetail(row);
  if (!body.isActive && detail.totalQty > 0) fail(409, 'Kho còn hàng, hãy chuyển hết hàng trước khi ngừng dùng');
  if (capacity < detail.totalQty) fail(409, `Sức chứa kho phải ≥ hàng đang chứa (${detail.totalQty} khay), không thể đặt ${capacity}.`);
  if (capacity < detail.allocatedCapacity) fail(409, `Sức chứa kho phải ≥ tổng sức chứa các vị trí (${detail.allocatedCapacity} khay), không thể đặt ${capacity}.`);
  Object.assign(row, body, { code, capacity });
  audit(userId, 'update', 'warehouse', row.id);
  return toDetail(row);
});

mock('POST', '/warehouses/:id/locations', ({ params, body, userId }) => {
  const warehouseId = Number(params.id);
  const warehouse = db.warehouses.find((w) => w.id === warehouseId);
  if (!warehouse) fail(404, 'Không tìm thấy kho');
  const code = checkLocation(body, warehouseId, null);
  const capacity = requirePositiveCapacity(body.capacity, 'vị trí');
  const others = db.locations.filter((l) => l.warehouseId === warehouseId).reduce((sum, l) => sum + l.capacity, 0);
  if (others + capacity > warehouse.capacity) {
    fail(409, `Tổng sức chứa vị trí (${others + capacity}) vượt sức chứa kho ${warehouse.code} (${warehouse.capacity}). Còn phân bổ được tối đa ${Math.max(0, warehouse.capacity - others)} khay.`);
  }
  const row = { ...body, code, capacity, id: nextId(db.locations), warehouseId };
  db.locations.push(row);
  audit(userId, 'create', 'location', row.id);
});

mock('PUT', '/locations/:id', ({ params, body, userId }) => {
  const row = db.locations.find((l) => l.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy vị trí');
  const warehouse = db.warehouses.find((w) => w.id === row.warehouseId);
  if (!warehouse) fail(404, 'Không tìm thấy kho');
  const code = checkLocation(body, row.warehouseId, row.id);
  const capacity = requirePositiveCapacity(body.capacity, 'vị trí');
  const usedQty = db.stock.filter((s) => s.locationId === row.id).reduce((sum, s) => sum + s.qtyAvailable + s.qtyHeld + s.qtyQuarantine + s.qtyExpired, 0);
  if (capacity < usedQty) fail(409, `Sức chứa vị trí phải ≥ hàng đang chứa (${usedQty} khay), không thể đặt ${capacity}.`);
  const others = db.locations.filter((l) => l.warehouseId === row.warehouseId && l.id !== row.id).reduce((sum, l) => sum + l.capacity, 0);
  if (others + capacity > warehouse.capacity) {
    fail(409, `Tổng sức chứa vị trí (${others + capacity}) vượt sức chứa kho ${warehouse.code} (${warehouse.capacity}). Còn phân bổ được tối đa ${Math.max(0, warehouse.capacity - others)} khay.`);
  }
  Object.assign(row, body, { code, capacity });
  audit(userId, 'update', 'location', row.id);
});

mock('DELETE', '/locations/:id', ({ params, userId }) => {
  const id = Number(params.id);
  if (db.stock.some((s) => s.locationId === id)) fail(409, 'Vị trí đã có dữ liệu tồn kho, không thể xóa');
  db.locations = db.locations.filter((l) => l.id !== id);
  audit(userId, 'delete', 'location', id);
});
