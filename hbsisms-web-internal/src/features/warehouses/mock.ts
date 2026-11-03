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
  return { ...w, locations, totalQty: locations.reduce((sum, l) => sum + l.usedQty, 0) };
}

function checkWarehouse(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code || !String(body.name ?? '').trim()) fail(400, 'Vui lòng nhập mã và tên kho');
  if (db.warehouses.some((w) => w.code === code && w.id !== id)) fail(409, `Mã kho ${code} đã tồn tại`);
  return code;
}

function checkLocation(body: any, warehouseId: number, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã vị trí');
  // UNIQUE (warehouse_id, code): trùng mã chỉ tính trong cùng một kho.
  if (db.locations.some((l) => l.warehouseId === warehouseId && l.code === code && l.id !== id)) fail(409, `Vị trí ${code} đã có trong kho này`);
  if (body.capacity !== null && !(Number.isInteger(Number(body.capacity)) && Number(body.capacity) > 0)) fail(400, 'Sức chứa phải là số nguyên dương');
  return code;
}

mock('GET', '/warehouses', () => db.warehouses.map(toDetail));

mock('POST', '/warehouses', ({ body, userId }) => {
  const row = { ...body, code: checkWarehouse(body, null), id: nextId(db.warehouses) };
  db.warehouses.push(row);
  audit(userId, 'create', 'warehouse', row.id);
  return toDetail(row);
});

mock('PUT', '/warehouses/:id', ({ params, body, userId }) => {
  const row = db.warehouses.find((w) => w.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy kho');
  const code = checkWarehouse(body, row.id);
  if (!body.isActive && toDetail(row).totalQty > 0) fail(409, 'Kho còn hàng, hãy chuyển hết hàng trước khi ngừng dùng');
  Object.assign(row, body, { code });
  audit(userId, 'update', 'warehouse', row.id);
  return toDetail(row);
});

mock('POST', '/warehouses/:id/locations', ({ params, body, userId }) => {
  const warehouseId = Number(params.id);
  if (!db.warehouses.some((w) => w.id === warehouseId)) fail(404, 'Không tìm thấy kho');
  const row = { ...body, code: checkLocation(body, warehouseId, null), id: nextId(db.locations), warehouseId };
  db.locations.push(row);
  audit(userId, 'create', 'location', row.id);
});

mock('PUT', '/locations/:id', ({ params, body, userId }) => {
  const row = db.locations.find((l) => l.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy vị trí');
  Object.assign(row, body, { code: checkLocation(body, row.warehouseId, row.id) });
  audit(userId, 'update', 'location', row.id);
});

mock('DELETE', '/locations/:id', ({ params, userId }) => {
  const id = Number(params.id);
  if (db.stock.some((s) => s.locationId === id)) fail(409, 'Vị trí đã có dữ liệu tồn kho, không thể xóa');
  db.locations = db.locations.filter((l) => l.id !== id);
  audit(userId, 'delete', 'location', id);
});
