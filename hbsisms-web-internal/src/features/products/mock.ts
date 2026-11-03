import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextId } from '@/shared/mock/db';
import { audit, availableQty } from '@/shared/mock/services';

const toItem = (product: (typeof db.products)[number]) => ({
  ...product,
  categoryName: db.categories.find((c) => c.id === product.categoryId)?.name ?? null,
  variants: db.variants
    .filter((v) => v.productId === product.id)
    .map((v) => ({ ...v, tiers: db.tiers.filter((t) => t.variantId === v.id).sort((a, b) => a.minQtyKhay - b.minQtyKhay), available: availableQty(v.id) })),
});

function validateVariant(body: any, currentId: number | null) {
  if (!body.sku?.trim() || !body.packLabel?.trim()) fail(400, 'Thiếu SKU hoặc quy cách');
  if (db.variants.some((v) => v.sku === body.sku.trim() && v.id !== currentId)) fail(409, `SKU ${body.sku} đã tồn tại`);
  if (!(Number(body.listPrice) > 0)) fail(400, 'Giá niêm yết phải lớn hơn 0');
  if (!(Number(body.packPerCase) >= 1)) fail(400, 'Số khay / thùng phải từ 1 trở lên');
  const mins = body.tiers.map((t: any) => Number(t.minQtyKhay));
  if (new Set(mins).size !== mins.length) fail(400, 'Các bậc số lượng không được trùng nhau');
}

function saveTiers(variantId: number, tiers: { minQtyKhay: number; unitPrice: number }[]) {
  db.tiers = db.tiers.filter((t) => t.variantId !== variantId);
  tiers.forEach((t) => db.tiers.push({ id: nextId(db.tiers), variantId, minQtyKhay: Number(t.minQtyKhay), unitPrice: Number(t.unitPrice) }));
}

const toCategoryItem = (c: (typeof db.categories)[number]) => ({
  ...c,
  parentName: db.categories.find((x) => x.id === c.parentId)?.name ?? null,
  productCount: db.products.filter((p) => p.categoryId === c.id).length,
});

function validateCategory(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã loại');
  if (!String(body.name ?? '').trim()) fail(400, 'Vui lòng nhập tên loại');
  if (db.categories.some((c) => c.code === code && c.id !== id)) fail(409, `Mã ${code} đã tồn tại`);
  // Đi ngược theo loại cha để chặn vòng lặp, ví dụ A thuộc B trong khi B thuộc A.
  for (let parentId: number | null = body.parentId ?? null; parentId !== null; parentId = db.categories.find((c) => c.id === parentId)?.parentId ?? null) {
    if (parentId === id) fail(409, 'Loại cha không được là chính nó hoặc loại con của nó');
  }
  return code;
}

mock('GET', '/categories', () => db.categories.map(toCategoryItem).sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi')));

mock('POST', '/categories', ({ body, userId }) => {
  const row = { ...body, code: validateCategory(body, null), name: body.name.trim(), id: nextId(db.categories), imageUrl: null };
  db.categories.push(row);
  audit(userId, 'create', 'category', row.id);
  return toCategoryItem(row);
});

mock('PUT', '/categories/:id', ({ params, body, userId }) => {
  const row = db.categories.find((c) => c.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy loại sản phẩm');
  Object.assign(row, body, { code: validateCategory(body, row.id), name: body.name.trim() });
  audit(userId, 'update', 'category', row.id);
  return toCategoryItem(row);
});

mock('GET', '/variants', () =>
  db.variants
    .filter((v) => v.isActive)
    .map((v) => ({ id: v.id, sku: v.sku, packLabel: v.packLabel, productName: db.products.find((p) => p.id === v.productId)?.name ?? '', listPrice: v.listPrice, packPerCase: v.packPerCase })),
);

mock('GET', '/products', ({ query }) => {
  const rows = db.products
    .filter((p) => matches(query.q, p.name, p.code) || db.variants.some((v) => v.productId === p.id && matches(query.q, v.sku)))
    .filter((p) => !query.categoryId || p.categoryId === Number(query.categoryId));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/products/:id', ({ params }) => {
  const product = db.products.find((p) => p.id === Number(params.id));
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  return toItem(product);
});

mock('POST', '/products', ({ body, userId }) => {
  if (db.products.some((p) => p.code === body.code)) fail(409, `Mã ${body.code} đã tồn tại`);
  const now = new Date().toISOString();
  const product = { ...body, id: nextId(db.products), slug: body.code, imageUrl: null, gallery: null, createdAt: now, updatedAt: now };
  db.products.push(product);
  audit(userId, 'create', 'product', product.id);
  return toItem(product);
});

mock('PUT', '/products/:id', ({ params, body, userId }) => {
  const product = db.products.find((p) => p.id === Number(params.id));
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  if (db.products.some((p) => p.code === body.code && p.id !== product.id)) fail(409, `Mã ${body.code} đã tồn tại`);
  Object.assign(product, body, { updatedAt: new Date().toISOString() });
  audit(userId, 'update', 'product', product.id);
  return toItem(product);
});

mock('POST', '/products/:id/variants', ({ params, body, userId }) => {
  validateVariant(body, null);
  const now = new Date().toISOString();
  const { tiers, ...rest } = body;
  const variant = { ...rest, id: nextId(db.variants), productId: Number(params.id), createdAt: now, updatedAt: now };
  db.variants.push(variant);
  saveTiers(variant.id, tiers);
  audit(userId, 'create', 'product_variant', variant.id);
});

mock('PUT', '/variants/:id', ({ params, body, userId }) => {
  const variant = db.variants.find((v) => v.id === Number(params.id));
  if (!variant) fail(404, 'Không tìm thấy quy cách');
  validateVariant(body, variant.id);
  const { tiers, ...rest } = body;
  Object.assign(variant, rest, { updatedAt: new Date().toISOString() });
  saveTiers(variant.id, tiers);
  audit(userId, 'update', 'product_variant', variant.id);
});
