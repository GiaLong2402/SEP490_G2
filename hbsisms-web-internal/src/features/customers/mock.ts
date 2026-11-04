import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextCode, nextId } from '@/shared/mock/db';
import { audit, configInt, createApproval, variantLabel } from '@/shared/mock/services';

function toDetail(id: number) {
  const customer = db.customers.find((c) => c.id === id);
  if (!customer) fail(404, 'Không tìm thấy khách hàng');
  return {
    ...customer,
    addresses: db.addresses.filter((a) => a.customerId === id),
    prices: db.customerPrices
      .filter((p) => p.customerId === id)
      .map((p) => {
        const decided = [...db.approvals]
          .filter((a) => a.refType === 'customer_price' && a.refId === p.id && a.status !== 'pending' && a.decidedAt)
          .sort((a, b) => (b.decidedAt ?? '').localeCompare(a.decidedAt ?? ''))[0];
        return {
          ...p,
          ...variantLabel(p.variantId),
          listPrice: db.variants.find((v) => v.id === p.variantId)?.listPrice ?? 0,
          // Chờ duyệt: ẩn note cũ. Đã quyết định: hiện note lần gần nhất nếu có.
          decisionNote: p.status === 'pending' ? null : (decided?.note ?? null),
        };
      }),
    recentOrders: db.orders
      .filter((o) => o.customerId === id)
      .sort((a, b) => b.placedAt.localeCompare(a.placedAt))
      .slice(0, 10)
      .map((o) => ({ id: o.id, code: o.code, placedAt: o.placedAt, total: o.total, state: o.state })),
  };
}

// Giống CHECK chk_b2b_terms: khách B2C không có hạn mức và kỳ hạn.
const normalize = (body: any) => (body.customerType === 'b2b' ? body : { ...body, creditLimit: 0, netDays: 0, taxCode: null });

mock('GET', '/customers', ({ query }) => {
  const activeFilter =
    query.isActive === undefined || query.isActive === ''
      ? null
      : query.isActive === true || query.isActive === 'true';
  const rows = db.customers
    .filter((c) => matches(query.q, c.name, c.code, c.phone, c.taxCode))
    .filter((c) => !query.type || c.customerType === query.type)
    .filter((c) => activeFilter === null || c.isActive === activeFilter)
    .sort((a, b) => a.name.localeCompare(b.name, 'vi'))
    .map((c) => ({ ...c, orderCount: db.orders.filter((o) => o.customerId === c.id).length }));
  return paginate(rows, query);
});

mock('GET', '/customers/:id', ({ params }) => toDetail(Number(params.id)));

mock('POST', '/customers', ({ body, userId }) => {
  const now = new Date().toISOString();
  const prefix = body.customerType === 'b2b' ? 'KH-B' : 'KH';
  const code = body.customerType === 'b2b'
    ? `KH-B${String(db.customers.filter((c) => c.customerType === 'b2b').length + 1).padStart(3, '0')}`
    : nextCode(prefix, db.customers.filter((c) => c.customerType === 'b2c'));
  const customer = { ...normalize(body), id: nextId(db.customers), code, accountUserId: null, currentDebt: 0, createdAt: now, updatedAt: now };
  db.customers.push(customer);
  audit(userId, 'create', 'customer', customer.id);
  return toDetail(customer.id);
});

mock('PUT', '/customers/:id', ({ params, body, userId }) => {
  const customer = db.customers.find((c) => c.id === Number(params.id));
  if (!customer) fail(404, 'Không tìm thấy khách hàng');
  if (body.customerType === 'b2c' && customer.currentDebt > 0) fail(409, 'Khách đang có công nợ, không thể chuyển sang B2C');
  Object.assign(customer, normalize(body), { updatedAt: new Date().toISOString() });
  audit(userId, 'update', 'customer', customer.id);
  return toDetail(customer.id);
});

mock('PATCH', '/customers/:id/active', ({ params, body, userId }) => {
  const customer = db.customers.find((c) => c.id === Number(params.id));
  if (!customer) fail(404, 'Không tìm thấy khách hàng');
  customer.isActive = !!body.isActive;
  customer.updatedAt = new Date().toISOString();
  audit(userId, body.isActive ? 'unlock' : 'lock', 'customer', customer.id);
  return toDetail(customer.id);
});

mock('GET', '/customers/:id/addresses', ({ params }) => db.addresses.filter((a) => a.customerId === Number(params.id)));

mock('POST', '/customers/:id/addresses', ({ params, body }) => {
  const customerId = Number(params.id);
  if (body.isDefault) db.addresses.filter((a) => a.customerId === customerId).forEach((a) => (a.isDefault = false));
  const distanceKm = body.distanceKm === null ? null : Number(body.distanceKm);
  db.addresses.push({
    ...body, id: nextId(db.addresses), customerId, latitude: null, longitude: null, distanceKm,
    inDeliveryRadius: distanceKm !== null && distanceKm <= configInt('delivery_radius_km', 8), createdAt: new Date().toISOString(),
  });
});

mock('PUT', '/customers/:id/addresses/:addressId', ({ params, body }) => {
  const customerId = Number(params.id);
  const address = db.addresses.find((a) => a.id === Number(params.addressId) && a.customerId === customerId);
  if (!address) fail(404, 'Không tìm thấy địa chỉ');
  if (body.isDefault) db.addresses.filter((a) => a.customerId === customerId).forEach((a) => (a.isDefault = false));
  const distanceKm = body.distanceKm === null || body.distanceKm === undefined ? null : Number(body.distanceKm);
  Object.assign(address, {
    ...body,
    distanceKm,
    latitude: null,
    longitude: null,
    inDeliveryRadius: distanceKm !== null && distanceKm <= configInt('delivery_radius_km', 8),
  });
});

mock('DELETE', '/customers/:id/addresses/:addressId', ({ params }) => {
  const customerId = Number(params.id);
  const index = db.addresses.findIndex((a) => a.id === Number(params.addressId) && a.customerId === customerId);
  if (index < 0) fail(404, 'Không tìm thấy địa chỉ');
  db.addresses.splice(index, 1);
});

// Giữ giá cũ đến khi duyệt, tránh lỗi ghi đè do UNIQUE (customer_id, variant_id) — xem spec mục 5 #6.
mock('POST', '/customers/:id/prices', ({ params, body, userId }) => {
  const customerId = Number(params.id);
  const customer = db.customers.find((c) => c.id === customerId);
  if (customer?.customerType !== 'b2b') fail(400, 'Chỉ khách B2B có giá riêng');
  if (!(Number(body.unitPrice) > 0)) fail(400, 'Giá phải lớn hơn 0');
  const now = new Date().toISOString();
  const label = variantLabel(Number(body.variantId));
  let row = db.customerPrices.find((p) => p.customerId === customerId && p.variantId === Number(body.variantId));
  if (row) {
    if (row.status === 'pending') fail(409, 'Quy cách này đang có đề xuất chờ duyệt');
    Object.assign(row, { pendingPrice: Number(body.unitPrice), status: 'pending', effectiveFrom: body.effectiveFrom, effectiveTo: body.effectiveTo, updatedAt: now });
  } else {
    row = { id: nextId(db.customerPrices), customerId, variantId: Number(body.variantId), unitPrice: Number(body.unitPrice), pendingPrice: Number(body.unitPrice), status: 'pending', effectiveFrom: body.effectiveFrom, effectiveTo: body.effectiveTo, approvedBy: null, approvedAt: null, updatedAt: now };
    db.customerPrices.push(row);
  }
  createApproval('price_list', 'customer_price', row.id, `${customer.name}: đề xuất giá riêng ${label.sku} ${Number(body.unitPrice).toLocaleString('vi-VN')} ₫`, userId);
});
