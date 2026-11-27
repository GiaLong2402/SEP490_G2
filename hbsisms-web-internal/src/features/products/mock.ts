import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextId } from '@/shared/mock/db';
import { audit, availableQty } from '@/shared/mock/services';

const dayMs = 86_400_000;
const daysLeft = (exp: string | null) =>
  exp ? Math.round((new Date(`${exp}T00:00:00`).getTime() - new Date(new Date().toDateString()).getTime()) / dayMs) : null;

function lotRowsForProduct(productId: number) {
  const variantIds = new Set(db.variants.filter((v) => v.productId === productId).map((v) => v.id));
  const byLot = new Map<number, {
    lotId: number; lotCode: string; variantId: number; sku: string;
    mfgDate: string | null; expDate: string | null; daysLeft: number | null; available: number;
  }>();
  for (const row of db.stock) {
    if (!variantIds.has(row.variantId)) continue;
    const lot = db.lots.find((l) => l.id === row.lotId);
    if (!lot) continue;
    const variant = db.variants.find((v) => v.id === row.variantId);
    const existing = byLot.get(lot.id);
    const available = (existing?.available ?? 0) + row.qtyAvailable;
    byLot.set(lot.id, {
      lotId: lot.id,
      lotCode: lot.lotCode,
      variantId: row.variantId,
      sku: variant?.sku ?? '—',
      mfgDate: lot.mfgDate,
      expDate: lot.expDate,
      daysLeft: daysLeft(lot.expDate),
      available,
    });
  }
  return [...byLot.values()].sort((a, b) => (a.expDate ?? '9999-12-31').localeCompare(b.expDate ?? '9999-12-31') || a.lotId - b.lotId);
}

const toItem = (product: (typeof db.products)[number], { includeLots = false } = {}) => {
  const variants = db.variants
    .filter((v) => v.productId === product.id)
    .map((v) => ({ ...v, tiers: db.tiers.filter((t) => t.variantId === v.id).sort((a, b) => a.minQtyKhay - b.minQtyKhay), available: availableQty(v.id) }));
  const lots = includeLots ? lotRowsForProduct(product.id) : [];
  const totalStockQty = includeLots
    ? lots.reduce((sum, lot) => sum + lot.available, 0)
    : variants.reduce((sum, v) => sum + v.available, 0);
  return {
    ...product,
    categoryName: db.categories.find((c) => c.id === product.categoryId)?.name ?? null,
    unitCode: db.units.find((u) => u.id === product.unitId)?.code ?? null,
    unitName: db.units.find((u) => u.id === product.unitId)?.name ?? null,
    variants,
    totalStockQty,
    lots,
  };
};

function validateVariant(body: any, currentId: number | null) {
  if (!body.sku?.trim() || !body.packLabel?.trim()) fail(400, 'Thiếu SKU hoặc quy cách');
  if (db.variants.some((v) => v.sku === body.sku.trim() && v.id !== currentId)) fail(409, `SKU ${body.sku} đã tồn tại`);
  if (Number(body.listPrice) < 0) fail(400, 'Giá niêm yết không hợp lệ');
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

const isUnitInUse = (unitId: number) =>
  db.products.some((p) => p.unitId === unitId)
  || db.rawMaterials.some((m) => m.unitId === unitId)
  || db.productBomLines.some((line) => line.unitId === unitId);

function toBomResponse(productId: number) {
  const product = db.products.find((p) => p.id === productId);
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  const lines = db.productBomLines
    .filter((line) => line.productId === productId)
    .sort((a, b) => a.sortOrder - b.sortOrder || a.id - b.id)
    .map((line) => {
      const raw = line.rawMaterialId != null ? db.rawMaterials.find((m) => m.id === line.rawMaterialId) : undefined;
      const component = line.componentProductId != null ? db.products.find((p) => p.id === line.componentProductId) : undefined;
      const unit = db.units.find((u) => u.id === line.unitId)!;
      return {
        id: line.id,
        rawMaterialId: line.rawMaterialId,
        rawMaterialCode: raw?.code ?? null,
        rawMaterialName: raw?.name ?? null,
        componentProductId: line.componentProductId,
        componentProductCode: component?.code ?? null,
        componentProductName: component?.name ?? null,
        qty: line.qty,
        unitId: line.unitId,
        unitCode: unit.code,
        unitName: unit.name,
        wastePercent: line.wastePercent,
        sortOrder: line.sortOrder,
      };
    });
  return { productId: product.id, productCode: product.code, productName: product.name, lines };
}

function replaceBom(productId: number, body: any, userId: number | null | undefined) {
  const product = db.products.find((p) => p.id === productId);
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  const lines = Array.isArray(body?.lines) ? body.lines : fail(400, 'Danh sách định mức là bắt buộc');
  const rawIds = new Set<number>();
  const componentIds = new Set<number>();
  const next: (typeof db.productBomLines)[number][] = [];
  lines.forEach((line: any, index: number) => {
    const hasRaw = line.rawMaterialId != null;
    const hasComponent = line.componentProductId != null;
    if (hasRaw === hasComponent) fail(400, 'Mỗi dòng chọn đúng một: nguyên liệu hoặc sản phẩm cấu thành');
    const qty = Number(line.qty);
    if (!(qty > 0)) fail(400, 'Số lượng định mức phải lớn hơn 0');
    const unitId = Number(line.unitId);
    const unit = db.units.find((u) => u.id === unitId);
    if (!unit) fail(404, 'Không tìm thấy đơn vị tính');
    if (!unit.isActive) fail(400, 'Đơn vị tính đã ngừng sử dụng');
    let wastePercent: number | null = line.wastePercent == null || line.wastePercent === '' ? null : Number(line.wastePercent);
    if (wastePercent != null && (Number.isNaN(wastePercent) || wastePercent < 0 || wastePercent > 100)) {
      fail(400, '% hao hụt phải từ 0 đến 100');
    }
    if (hasRaw) {
      const rawId = Number(line.rawMaterialId);
      if (rawIds.has(rawId)) fail(400, 'Nguyên liệu bị trùng trong định mức');
      rawIds.add(rawId);
      const raw = db.rawMaterials.find((m) => m.id === rawId);
      if (!raw) fail(404, 'Không tìm thấy nguyên liệu');
      if (!raw.isActive) fail(400, 'Nguyên liệu đã ngừng sử dụng');
      if (raw.code.toLowerCase() === product.code.toLowerCase()) {
        fail(400, 'Không thể chọn chính sản phẩm này làm nguyên liệu của nó');
      }
      next.push({
        id: 0,
        productId,
        rawMaterialId: rawId,
        componentProductId: null,
        qty,
        unitId,
        wastePercent,
        sortOrder: index,
      });
    } else {
      const componentId = Number(line.componentProductId);
      if (componentId === productId) fail(400, 'Không thể chọn chính sản phẩm này làm nguyên liệu của nó');
      if (componentIds.has(componentId)) fail(400, 'Sản phẩm cấu thành bị trùng trong định mức');
      componentIds.add(componentId);
      const component = db.products.find((p) => p.id === componentId);
      if (!component) fail(404, 'Không tìm thấy sản phẩm cấu thành');
      if (!component.isActive) fail(400, 'Sản phẩm cấu thành đã ngừng kinh doanh');
      next.push({
        id: 0,
        productId,
        rawMaterialId: null,
        componentProductId: componentId,
        qty,
        unitId,
        wastePercent,
        sortOrder: index,
      });
    }
  });
  db.productBomLines = db.productBomLines.filter((line) => line.productId !== productId);
  next.forEach((line) => {
    line.id = nextId(db.productBomLines);
    db.productBomLines.push(line);
  });
  audit(userId, 'update', 'product_bom', productId);
  return toBomResponse(productId);
}

function validateUnit(body: any, id: number | null) {
  const code = String(body.code ?? '').trim();
  const name = String(body.name ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã đơn vị');
  if (!name) fail(400, 'Vui lòng nhập tên đơn vị');
  if (db.units.some((u) => u.code.toLowerCase() === code.toLowerCase() && u.id !== id)) fail(409, `Mã ${code} đã tồn tại`);
  return { code, name };
}

mock('GET', '/units', ({ query }) =>
  db.units
    .filter((u) => query.activeOnly === undefined || query.activeOnly === '' || u.isActive === (query.activeOnly === 'true'))
    .sort((a, b) => a.code.localeCompare(b.code, 'vi')),
);

mock('POST', '/units', ({ body, userId }) => {
  const { code, name } = validateUnit(body, null);
  const row = { id: nextId(db.units), code, name, isActive: body.isActive !== false };
  db.units.push(row);
  audit(userId, 'create', 'unit_of_measure', row.id);
  return row;
});

mock('PUT', '/units/:id', ({ params, body, userId }) => {
  const row = db.units.find((u) => u.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy đơn vị tính');
  const { code, name } = validateUnit(body, row.id);
  const isActive = body.isActive !== false;
  if (row.isActive && !isActive && isUnitInUse(row.id)) fail(409, 'Không thể ngừng sử dụng đơn vị đang được dùng bởi sản phẩm hoặc nguyên liệu');
  Object.assign(row, { code, name, isActive });
  audit(userId, 'update', 'unit_of_measure', row.id);
  return row;
});

/** Mentor tạm bỏ vừa NVL vừa bán: SP = hàng bán; NVL quản lý riêng. Cột DB vẫn giữ. */
function resolveCatalogFlags(body: any, current?: (typeof db.products)[number]) {
  if (body.isRawMaterial === true) fail(400, 'Tạm thời nguyên liệu quản lý riêng, không đánh dấu trên sản phẩm');
  return {
    isRawMaterial: false,
    isSellable: body.isSellable ?? current?.isSellable ?? true,
    isProducible: body.isProducible ?? current?.isProducible ?? false,
    unitId: null as number | null,
  };
}

mock('GET', '/categories', () => db.categories.map(toCategoryItem).sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name, 'vi')));

mock('POST', '/categories', ({ body, userId }) => {
  const row = {
    ...body,
    code: validateCategory(body, null),
    name: body.name.trim(),
    id: nextId(db.categories),
    imageUrl: typeof body.imageUrl === 'string' && body.imageUrl.trim() ? body.imageUrl.trim() : null,
  };
  db.categories.push(row);
  audit(userId, 'create', 'category', row.id);
  return toCategoryItem(row);
});

mock('PUT', '/categories/:id', ({ params, body, userId }) => {
  const row = db.categories.find((c) => c.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy loại sản phẩm');
  Object.assign(row, body, {
    code: validateCategory(body, row.id),
    name: body.name.trim(),
    imageUrl: typeof body.imageUrl === 'string' && body.imageUrl.trim() ? body.imageUrl.trim() : null,
  });
  audit(userId, 'update', 'category', row.id);
  return toCategoryItem(row);
});

mock('GET', '/variants', () =>
  db.variants
    .filter((v) => v.isActive)
    .map((v) => ({
      id: v.id,
      sku: v.sku,
      packLabel: v.packLabel,
      productName: db.products.find((p) => p.id === v.productId)?.name ?? '',
      listPrice: v.listPrice,
      packPerCase: v.packPerCase,
      available: availableQty(v.id),
    })),
);

function categoryIdsIncludingDescendants(categoryId: number) {
  const ids = new Set<number>([categoryId]);
  const queue = [categoryId];
  while (queue.length) {
    const id = queue.shift()!;
    for (const c of db.categories) {
      if (c.parentId === id && !ids.has(c.id)) {
        ids.add(c.id);
        queue.push(c.id);
      }
    }
  }
  return ids;
}

mock('GET', '/products', ({ query }) => {
  const categoryIds = query.categoryId ? categoryIdsIncludingDescendants(Number(query.categoryId)) : null;
  const rows = db.products
    .filter((p) => matches(query.q, p.name, p.code) || db.variants.some((v) => v.productId === p.id && matches(query.q, v.sku)))
    .filter((p) => !categoryIds || (p.categoryId != null && categoryIds.has(p.categoryId)));
  return paginate(rows.map(toItem), query);
});

mock('GET', '/products/:id', ({ params }) => {
  const product = db.products.find((p) => p.id === Number(params.id));
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  return toItem(product, { includeLots: true });
});

mock('POST', '/products/images', ({ body }) => {
  if (!(typeof FormData !== 'undefined' && body instanceof FormData)) fail(400, 'Cần gửi multipart form-data');
  const files = body.getAll('files').filter((entry) => entry instanceof File || (typeof Blob !== 'undefined' && entry instanceof Blob));
  if (!files.length) fail(400, 'Chọn ít nhất một ảnh để tải lên');
  return {
    urls: files.map((_, index) => `https://res.cloudinary.com/demo/image/upload/v1/products/mock-${Date.now()}-${index}.jpg`),
  };
});

mock('POST', '/products', ({ body, userId }) => {
  const code = String(body.code ?? '').trim();
  const name = String(body.name ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã sản phẩm');
  if (!name) fail(400, 'Vui lòng nhập tên sản phẩm');
  if (db.products.some((p) => p.code.toLowerCase() === code.toLowerCase())) fail(409, `Mã ${code} đã tồn tại`);
  // Khớp API: tạo mới chưa có variant active → không publish B2C.
  if (body.isPublished) fail(400, 'Không thể hiển thị cổng B2C khi chưa có quy cách đang bán');
  const flags = resolveCatalogFlags(body);
  const now = new Date().toISOString();
  const gallery = Array.isArray(body.gallery)
    ? body.gallery.map((url: string) => String(url).trim()).filter(Boolean)
    : [];
  const product = {
    ...body,
    code,
    name,
    ...flags,
    id: nextId(db.products),
    slug: name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-+|-+$/g, '') || code.toLowerCase(),
    imageUrl: typeof body.imageUrl === 'string' && body.imageUrl.trim() ? body.imageUrl.trim() : null,
    gallery,
    createdAt: now,
    updatedAt: now,
  };
  db.products.push(product);
  audit(userId, 'create', 'product', product.id);
  return toItem(product, { includeLots: true });
});

mock('PUT', '/products/:id', ({ params, body, userId }) => {
  const product = db.products.find((p) => p.id === Number(params.id));
  if (!product) fail(404, 'Không tìm thấy sản phẩm');
  const code = String(body.code ?? '').trim();
  const name = String(body.name ?? '').trim();
  if (!code) fail(400, 'Vui lòng nhập mã sản phẩm');
  if (!name) fail(400, 'Vui lòng nhập tên sản phẩm');
  if (db.products.some((p) => p.code.toLowerCase() === code.toLowerCase() && p.id !== product.id)) {
    fail(409, `Mã ${code} đã tồn tại`);
  }
  if (body.isPublished) {
    const hasActiveVariant = db.variants.some((v) => v.productId === product.id && v.isActive);
    if (!hasActiveVariant) fail(400, 'Không thể hiển thị cổng B2C khi chưa có quy cách đang bán');
  }
  const flags = resolveCatalogFlags(body, product);
  // gallery null = giữ (API); [] hoặc mảng = ghi đè. FE form luôn gửi string[].
  const nextGallery =
    body.gallery === null || body.gallery === undefined
      ? product.gallery
      : Array.isArray(body.gallery)
        ? body.gallery.map((url: string) => String(url).trim()).filter(Boolean)
        : product.gallery;
  Object.assign(product, body, {
    code,
    name,
    imageUrl: typeof body.imageUrl === 'string' && body.imageUrl.trim() ? body.imageUrl.trim() : null,
    gallery: nextGallery,
    ...flags,
    updatedAt: new Date().toISOString(),
  });
  audit(userId, 'update', 'product', product.id);
  return toItem(product, { includeLots: true });
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
  const { tiers, listPrice: _ignored, ...rest } = body;
  // Giá niêm yết chỉ đổi qua Task 59 Áp dụng — không sửa tay qua form.
  Object.assign(variant, rest, { updatedAt: new Date().toISOString() });
  saveTiers(variant.id, tiers);
  audit(userId, 'update', 'product_variant', variant.id);
});

type MockSellPriceHistory = {
  id: number;
  variantId: number;
  sku: string;
  packLabel: string;
  listPrice: number;
  previousListPrice: number | null;
  unitCost: number | null;
  costSource: 'WAC' | 'PRODUCTION';
  markupPercent: number;
  markupSource: string | null;
  effectiveFrom: string;
  effectiveTo: string | null;
  current: boolean;
  createdByUserId: number | null;
  createdByName: string | null;
};

const sellPriceHistory: MockSellPriceHistory[] = [];
let sellPriceHistorySeq = 1;

function roundToThousand(n: number) {
  return Math.round(n / 1000) * 1000;
}

function mockSuggest(variantId: number, costSource: string) {
  const variant = db.variants.find((v) => v.id === variantId);
  if (!variant) fail(404, 'Không tìm thấy quy cách');
  const markupPercent = 30;
  // Mock: WAC ≈ 70% giá niêm yết; PRODUCTION ≈ 65%.
  const unitCost = Math.max(1000, Math.round((variant.listPrice || 100_000) * (costSource === 'PRODUCTION' ? 0.65 : 0.7)));
  const suggestedListPrice = roundToThousand(unitCost * (1 + markupPercent / 100));
  return {
    variantId: variant.id,
    sku: variant.sku,
    packLabel: variant.packLabel,
    currentListPrice: variant.listPrice,
    costSource: costSource === 'PRODUCTION' ? 'PRODUCTION' : 'WAC',
    unitCost,
    costSourceLabel: costSource === 'PRODUCTION' ? 'Giá thành lệnh LSX-MOCK' : 'ĐG bình quân kỳ WAC hiện hành',
    productionOrderId: costSource === 'PRODUCTION' ? 1 : null,
    productionOrderCode: costSource === 'PRODUCTION' ? 'LSX-MOCK' : null,
    markupPercent,
    markupSource: 'cấu hình chung',
    suggestedListPrice,
  };
}

mock('GET', '/variants/:id/sell-price-suggestion', ({ params, query }) =>
  mockSuggest(Number(params.id), String(query.costSource ?? 'WAC')));

mock('POST', '/variants/:id/sell-price-suggestion/apply', ({ params, body, userId }) => {
  const costSource = String((body as { costSource?: string })?.costSource ?? 'WAC');
  const requested = db.variants.find((v) => v.id === Number(params.id));
  if (!requested) fail(404, 'Không tìm thấy quy cách');
  const isHalf = (v: typeof requested) =>
    v.unitWeightG === 500 || /0[,.]5|500g|1\/2/i.test(v.packLabel ?? '');
  const isOne = (v: typeof requested) =>
    v.unitWeightG === 1000 || (/1\s*kg|1000g/i.test(v.packLabel ?? '') && !isHalf(v));
  const siblings = db.variants.filter((v) => v.productId === requested.productId && v.isActive);
  const oneKg = isOne(requested) ? requested : siblings.find(isOne) ?? requested;
  const suggestion = mockSuggest(oneKg.id, costSource);
  const now = new Date().toISOString();
  const writePrice = (variant: typeof requested, price: number, unitCost: number) => {
    const old = variant.listPrice;
    for (const row of sellPriceHistory) {
      if (row.variantId === variant.id && row.current) {
        row.current = false;
        row.effectiveTo = now;
      }
    }
    sellPriceHistory.unshift({
      id: sellPriceHistorySeq++,
      variantId: variant.id,
      sku: variant.sku,
      packLabel: variant.packLabel,
      listPrice: price,
      previousListPrice: old,
      unitCost,
      costSource: suggestion.costSource as 'WAC' | 'PRODUCTION',
      markupPercent: suggestion.markupPercent,
      markupSource: suggestion.markupSource,
      effectiveFrom: now,
      effectiveTo: null,
      current: true,
      createdByUserId: userId ?? null,
      createdByName: userId ? 'Mock user' : null,
    });
    variant.listPrice = price;
    variant.updatedAt = now;
    audit(userId, 'apply_sell_price', 'product_variant', variant.id, {
      oldListPrice: old,
      newListPrice: price,
      costSource: suggestion.costSource,
    });
  };
  writePrice(oneKg, suggestion.suggestedListPrice, suggestion.unitCost);
  const half = isHalf(requested) ? requested : siblings.find(isHalf);
  if (half && half.id !== oneKg.id) {
    const halfPrice = Math.round(suggestion.suggestedListPrice / 2 / 1000) * 1000;
    writePrice(half, halfPrice, Math.round(suggestion.unitCost / 2));
  }
  return { ...suggestion, variantId: oneKg.id, sku: oneKg.sku, currentListPrice: suggestion.suggestedListPrice };
});

mock('GET', '/variants/:id/sell-price-history', ({ params }) => {
  const variantId = Number(params.id);
  if (!db.variants.some((v) => v.id === variantId)) fail(404, 'Không tìm thấy quy cách');
  return sellPriceHistory.filter((r) => r.variantId === variantId);
});

mock('GET', '/products/:id/sell-price-history', ({ params }) => {
  const productId = Number(params.id);
  if (!db.products.some((p) => p.id === productId)) fail(404, 'Không tìm thấy sản phẩm');
  const variantIds = new Set(db.variants.filter((v) => v.productId === productId).map((v) => v.id));
  return sellPriceHistory.filter((r) => variantIds.has(r.variantId));
});

mock('GET', '/products/:id/bom', ({ params }) => toBomResponse(Number(params.id)));

mock('PUT', '/products/:id/bom', ({ params, body, userId }) => replaceBom(Number(params.id), body, userId));
