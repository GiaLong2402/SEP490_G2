// CSDL demo trong trình duyệt. Danh mục lấy đúng từ database/seed.sql; giao dịch là dữ liệu minh họa sinh ổn định.
// Xóa cùng features/*/mock.ts khi có backend.
import type * as T from '@/shared/domain/entities';
import type { OrderState, PaymentMethod, SaleChannel } from '@/shared/domain/enums';

// Tăng phiên bản khi đổi cấu trúc để không đọc nhầm dữ liệu cũ đã lưu trong trình duyệt.
const STORAGE_KEY = 'hbsisms.mockdb.v2';
const pad = (n: number, width = 2) => String(n).padStart(width, '0');

function isoDate(offsetDays: number) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}
function isoTime(offsetDays: number, hour = 9, minute = 0) {
  const d = new Date();
  d.setDate(d.getDate() + offsetDays);
  d.setHours(hour, minute, 0, 0);
  return d.toISOString();
}

export interface MockDb {
  roles: T.AppRole[];
  users: (T.AppUser & { password: string })[];
  customers: T.Customer[];
  addresses: T.CustomerAddress[];
  categories: T.Category[];
  products: T.Product[];
  variants: T.ProductVariant[];
  tiers: T.VolumeTier[];
  customerPrices: (T.CustomerPrice & { pendingPrice: number | null })[];
  warehouses: T.Warehouse[];
  locations: T.Location[];
  rawMaterials: T.RawMaterial[];
  suppliers: T.Supplier[];
  lots: T.Lot[];
  stock: T.Stock[];
  ledger: T.StockLedger[];
  receipts: (T.GoodsReceipt & { items: { name: string; qty: number; unit: string }[] })[];
  stocktakes: T.Stocktake[];
  stocktakeLines: T.StocktakeLine[];
  damageReports: T.DamageReport[];
  requests: T.SalesRequest[];
  quotes: T.Quote[];
  quoteLines: T.QuoteLine[];
  orders: T.SalesOrder[];
  orderLines: T.SalesOrderLine[];
  deliveries: T.Delivery[];
  returns: T.SalesReturn[];
  orderEvents: T.OrderEvent[];
  payments: T.Payment[];
  debtLedger: T.CustomerDebtLedger[];
  reviews: T.ProductReview[];
  vouchers: T.Voucher[];
  approvals: T.ApprovalTask[];
  notifications: T.Notification[];
  config: T.AppConfig[];
  auditLog: T.AuditLog[];
}

function seed(): MockDb {
  const now = new Date().toISOString();
  // Bộ sinh số giả ổn định: mỗi lần khôi phục đều ra cùng dữ liệu.
  let s = 380;
  const rnd = () => ((s = (s * 16807) % 2147483647) - 1) / 2147483646;
  const pick = <V,>(list: readonly V[]) => list[Math.floor(rnd() * list.length)];
  const int = (min: number, max: number) => min + Math.floor(rnd() * (max - min + 1));

  const roles: T.AppRole[] = [
    { id: 1, code: 'admin', name: 'Quản trị viên', permissions: ['orders', 'inventory', 'customers', 'products', 'debt', 'reports', 'approve', 'admin'] },
    { id: 2, code: 'owner', name: 'Chủ cửa hàng', permissions: ['orders', 'inventory', 'customers', 'products', 'debt', 'reports', 'approve'] },
    { id: 3, code: 'sales', name: 'Nhân viên bán hàng', permissions: ['orders', 'customers', 'products', 'debt'] },
    { id: 4, code: 'warehouse', name: 'Nhân viên kho', permissions: ['inventory', 'products'] },
    { id: 5, code: 'customer', name: 'Khách hàng (B2C)', permissions: [] },
  ];

  const user = (id: number, username: string, fullName: string, email: string, roleId: number, position: string): MockDb['users'][number] => ({
    id, username, email, phone: null, fullName, avatarUrl: null, userType: 'staff', roleId, employeeCode: `NV-${pad(id, 3)}`, position,
    hiredAt: isoDate(-400 - id * 30), isActive: true, lastLoginAt: isoTime(-1, 8, id), createdAt: now, updatedAt: now, password: 'demo123',
  });
  const users = [
    user(1, 'admin', 'Lê Minh Quân', 'admin@hbsisms.vn', 1, 'Quản trị'),
    user(2, 'owner', 'Phạm Thị Hòa', 'owner@hbsisms.vn', 2, 'Chủ cửa hàng'),
    user(3, 'sales1', 'Nguyễn Thị Mai', 'mai@hbsisms.vn', 3, 'Kinh doanh'),
    user(4, 'kho1', 'Vũ Văn Khoa', 'khoa@hbsisms.vn', 4, 'Thủ kho'),
    user(5, 'giao1', 'Đỗ Quang Sơn', 'son@hbsisms.vn', 4, 'Giao hàng'),
  ];

  const b2b = (id: number, code: string, name: string, phone: string, tax: string, contact: string, limit: number, net: number): T.Customer => ({
    id, code, customerType: 'b2b', name, email: null, phone, taxCode: tax, contactName: contact, contactPhone: phone, accountUserId: null,
    creditLimit: limit, netDays: net, currentDebt: 0, isActive: true, note: null, createdAt: isoTime(-300), updatedAt: now,
  });
  const b2c = (id: number, code: string, name: string, phone: string, email: string | null): T.Customer => ({
    id, code, customerType: 'b2c', name, email, phone, taxCode: null, contactName: null, contactPhone: null, accountUserId: null,
    creditLimit: 0, netDays: 0, currentDebt: 0, isActive: true, note: null, createdAt: isoTime(-120), updatedAt: now,
  });
  const customers: T.Customer[] = [
    b2c(1, 'KH-0001', 'Nguyễn Văn An', '0912 345 678', 'an@gmail.com'),
    b2b(2, 'KH-B001', 'Nhà hàng Biển Xanh', '0988 777 666', '0301234567', 'Chị Thu', 50_000_000, 15),
    b2b(3, 'KH-B002', 'Khách sạn Vịnh Ngọc', '0903 221 118', '5700123456', 'Anh Tuấn', 80_000_000, 30),
    b2b(4, 'KH-B003', 'Quán Hải Đăng', '0978 110 225', '5700987654', 'Chị Lan', 20_000_000, 15),
    b2b(5, 'KH-B004', 'Siêu thị Mini Bãi Cháy', '0936 554 870', '5700456123', 'Anh Dũng', 60_000_000, 30),
    b2c(6, 'KH-0002', 'Trần Thị Bình', '0905 667 123', 'binh.tran@gmail.com'),
    b2c(7, 'KH-0003', 'Hoàng Văn Cường', '0917 888 909', null),
    b2c(8, 'KH-0004', 'Đặng Thu Hà', '0983 332 441', 'ha.dang@gmail.com'),
  ];

  const addr = (id: number, customerId: number, line1: string, ward: string, km: number): T.CustomerAddress => ({
    id, customerId, kind: 'shipping', recipientName: customers[customerId - 1].contactName ?? customers[customerId - 1].name, phone: customers[customerId - 1].phone, line1, ward,
    district: 'TP Hạ Long', province: 'Quảng Ninh', latitude: null, longitude: null, inDeliveryRadius: km <= 8, distanceKm: km, isDefault: true, createdAt: isoTime(-100),
  });
  const addresses = [
    addr(1, 1, '12 Lê Thánh Tông', 'Bạch Đằng', 2.4), addr(2, 2, '45 Hạ Long', 'Bãi Cháy', 6.8), addr(3, 3, '88 Hoàng Quốc Việt', 'Hùng Thắng', 9.5),
    addr(4, 4, '7 Cao Thắng', 'Cao Xanh', 3.1), addr(5, 5, '101 Anh Đào', 'Bãi Cháy', 7.2), addr(6, 6, '23 Nguyễn Văn Cừ', 'Hồng Hải', 4.0),
    addr(7, 7, '5 Giếng Đáy', 'Giếng Đáy', 11.3), addr(8, 8, '66 Trần Hưng Đạo', 'Trần Hưng Đạo', 1.8),
  ];

  const categories: T.Category[] = [
    { id: 1, code: 'cha-hai-san', name: 'Chả hải sản', description: 'Chả mực, chả tôm đóng khay', imageUrl: null, parentId: null, sortOrder: 1, isActive: true },
    { id: 2, code: 'hai-san-lam-sach', name: 'Hải sản làm sạch', description: 'Mực nguyên con làm sạch đóng khay', imageUrl: null, parentId: null, sortOrder: 2, isActive: true },
  ];

  const product = (id: number, code: string, name: string, categoryId: number, description: string, storageNote: string): T.Product => ({
    id, code, name, slug: code, categoryId, description, storageNote, origin: 'Quảng Ninh', imageUrl: `/images/${code}.png`, gallery: null, isPublished: true, isActive: true, createdAt: isoTime(-300), updatedAt: now,
  });
  const products = [
    product(1, 'cha-muc', 'Chả mực', 1, 'Chả mực giã tay, đóng khay cố định theo quy cách.', 'Bảo quản lạnh. Hạn dùng 14 ngày kể từ ngày sản xuất.'),
    product(2, 'cha-tom', 'Chả tôm', 1, 'Chả tôm biển giã tay, đóng khay cố định theo quy cách.', 'Bảo quản lạnh. Hạn dùng 14 ngày kể từ ngày sản xuất.'),
    product(3, 'muc-nguyen-con', 'Mực nguyên con làm sạch', 2, 'Mực nguyên con làm sạch, đóng khay cố định.', 'Bảo quản lạnh. Hạn dùng 7 ngày kể từ ngày đóng khay.'),
  ];

  const variant = (id: number, productId: number, sku: string, packLabel: string, g: number, listPrice: number, packPerCase: number): T.ProductVariant => ({
    id, productId, sku, packLabel, unitWeightG: g, listPrice, packPerCase, barcode: null, isActive: true, createdAt: isoTime(-300), updatedAt: now,
  });
  const variants = [
    variant(1, 1, 'cha-muc-05', 'Khay 0,5kg', 500, 85000, 20), variant(2, 1, 'cha-muc-1', 'Khay 1kg', 1000, 165000, 10),
    variant(3, 2, 'cha-tom-05', 'Khay 0,5kg', 500, 95000, 20), variant(4, 3, 'muc-05', 'Khay 0,5kg', 500, 120000, 20),
  ];
  const tiers: T.VolumeTier[] = [
    { id: 1, variantId: 1, minQtyKhay: 5, unitPrice: 82000 }, { id: 2, variantId: 1, minQtyKhay: 20, unitPrice: 80000 }, { id: 3, variantId: 2, minQtyKhay: 10, unitPrice: 160000 },
    { id: 4, variantId: 3, minQtyKhay: 5, unitPrice: 92000 }, { id: 5, variantId: 3, minQtyKhay: 20, unitPrice: 90000 },
  ];
  const customerPrices: MockDb['customerPrices'] = [
    { id: 1, customerId: 2, variantId: 1, unitPrice: 78000, pendingPrice: null, status: 'approved', effectiveFrom: null, effectiveTo: null, approvedBy: 2, approvedAt: isoTime(-60), updatedAt: isoTime(-60) },
    { id: 2, customerId: 3, variantId: 2, unitPrice: 155000, pendingPrice: null, status: 'approved', effectiveFrom: isoDate(-90), effectiveTo: null, approvedBy: 2, approvedAt: isoTime(-90), updatedAt: isoTime(-90) },
    { id: 3, customerId: 3, variantId: 4, unitPrice: 112000, pendingPrice: 110000, status: 'pending', effectiveFrom: isoDate(-30), effectiveTo: null, approvedBy: 2, approvedAt: isoTime(-30), updatedAt: isoTime(-1) },
  ];

  // Capacity kho >= tổng capacity vị trí (120*5 + 160 + 500 = 1260).
  const warehouses: T.Warehouse[] = [{ id: 1, code: 'WH1', name: 'Kho Hải sản Hòa Bình', address: 'TP Hạ Long, Quảng Ninh', capacity: 1260, isActive: true }];
  const locations: T.Location[] = ([['A-01', 'Khu A', 120], ['A-02', 'Khu A', 120], ['A-05', 'Khu A', 120], ['B-02', 'Khu B', 120], ['B-04', 'Khu B', 120], ['B-06', 'Khu B', 160], ['C-01', 'Kho lạnh C', 500]] as const)
    .map(([code, zone, capacity], i) => ({ id: i + 1, warehouseId: 1, code, zone, capacity, note: null }));

  const rawMaterials: T.RawMaterial[] = [
    { id: 1, code: 'NL-MUC', name: 'Mực tươi nguyên liệu', unit: 'kg', qtyOnHand: 185.5, reorderLevel: 100, isActive: true },
    { id: 2, code: 'NL-TOM', name: 'Tôm biển nguyên liệu', unit: 'kg', qtyOnHand: 62, reorderLevel: 80, isActive: true },
    { id: 3, code: 'NL-KHAY', name: 'Khay xốp đóng gói', unit: 'cái', qtyOnHand: 2400, reorderLevel: 500, isActive: true },
    { id: 4, code: 'NL-MANG', name: 'Màng bọc thực phẩm', unit: 'cuộn', qtyOnHand: 14, reorderLevel: 20, isActive: true },
  ];

  const supplier = (id: number, name: string, taxCode: string | null, contactName: string, phone: string, address: string, isActive = true): T.Supplier => ({
    id, code: `NCC-${pad(id, 4)}`, name, taxCode, contactName, phone, email: null, address, note: null, isActive, createdAt: isoTime(-300), updatedAt: now,
  });
  const suppliers = [
    supplier(1, 'Xưởng chế biến Hòa Bình', null, 'Anh Hải', '0912 000 111', 'TP Hạ Long, Quảng Ninh'),
    supplier(2, 'Cảng cá Cái Rồng', '5700111222', 'Chị Nga', '0934 222 333', 'Cái Rồng, Vân Đồn, Quảng Ninh'),
    supplier(3, 'Công ty Bao bì Hạ Long', '5700333444', 'Anh Bình', '0966 444 555', 'KCN Cái Lân, Hạ Long'),
    supplier(4, 'Vựa hải sản Cẩm Phả', null, 'Chị Hoa', '0987 666 777', 'Cẩm Phả, Quảng Ninh', false),
  ];

  // Lô & tồn: tạo đủ tình huống FEFO, sắp hết hạn, đã hết hạn, tồn thấp.
  const lots: T.Lot[] = [];
  const stock: T.Stock[] = [];
  const ledger: T.StockLedger[] = [];
  const lotSpec: [number, number, number, number, number, number][] = [
    // variantId, ngày đến HSD, locationId, khả dụng, giữ, cách ly
    [1, 2, 1, 18, 6, 0], [1, 9, 2, 60, 12, 0], [1, 13, 7, 140, 0, 0],
    [2, 4, 3, 9, 3, 0], [2, 11, 7, 3, 0, 0],
    [3, 6, 4, 22, 4, 2], [3, 12, 7, 95, 10, 0],
    [4, 1, 5, 6, 2, 0], [4, 5, 6, 5, 0, 0], [4, -1, 6, 0, 0, 4],
  ];
  lotSpec.forEach(([variantId, expIn, locationId, available, held, quarantine], i) => {
    const id = i + 1;
    const shelf = variantId === 4 ? 7 : 14;
    const receivedAt = isoTime(expIn - shelf, 7, 30);
    lots.push({ id, lotCode: `LOT-${isoDate(expIn - shelf).replace(/-/g, '').slice(2)}-${pad(id)}`, variantId, mfgDate: isoDate(expIn - shelf), expDate: isoDate(expIn), receivedAt, note: null });
    stock.push({ id, variantId, lotId: id, locationId, qtyAvailable: available, qtyHeld: held, qtyQuarantine: quarantine, qtyExpired: expIn < 0 ? 3 : 0, updatedAt: now });
    const total = available + held + quarantine + (expIn < 0 ? 3 : 0);
    ledger.push({ id: ledger.length + 1, variantId, lotId: id, locationId, toLocationId: null, entryType: 'in', qtyDelta: total, balanceAfter: total, refType: 'goods_receipt', refId: id, note: 'Nhập thành phẩm', createdBy: 4, createdAt: receivedAt });
    if (held > 0) ledger.push({ id: ledger.length + 1, variantId, lotId: id, locationId, toLocationId: null, entryType: 'hold', qtyDelta: -held, balanceAfter: available, refType: 'sales_order_line', refId: null, note: 'Giữ hàng theo FEFO', createdBy: 3, createdAt: isoTime(-1, 10) });
  });

  const receipts: MockDb['receipts'] = lots.slice(0, 6).map((lot, i) => ({
    id: i + 1, code: `PN-${pad(i + 1, 4)}`, receiptType: 'finished', source: 'Xưởng chế biến Hòa Bình', receivedBy: 4, receivedAt: lot.receivedAt ?? now, note: null,
    items: [{ name: `${variants[lot.variantId - 1].sku} · ${lot.lotCode}`, qty: stock[i].qtyAvailable + stock[i].qtyHeld, unit: 'khay' }],
  }));
  receipts.push({ id: 7, code: 'PN-0007', receiptType: 'raw', source: 'Cảng cá Cái Rồng', receivedBy: 4, receivedAt: isoTime(-2, 6, 30), note: 'Mực tươi nguyên liệu: 120 kg; Tôm biển nguyên liệu: 45 kg', items: [{ name: 'Mực tươi nguyên liệu', qty: 120, unit: 'kg' }, { name: 'Tôm biển nguyên liệu', qty: 45, unit: 'kg' }] });

  const stocktakes: T.Stocktake[] = [
    { id: 1, code: 'KK-0001', scope: 'Khu A', status: 'done', createdBy: 2, countedBy: 4, startedAt: isoTime(-20, 7), closedAt: isoTime(-20, 11), note: null },
    { id: 2, code: 'KK-0002', scope: 'Kho lạnh C', status: 'counting', createdBy: 4, countedBy: 4, startedAt: isoTime(0, 7), closedAt: null, note: 'Kiểm kê định kỳ tháng' },
  ];
  const stocktakeLines: T.StocktakeLine[] = [];
  const addCount = (stocktakeId: number, stockIndex: number, counted: number | null) => {
    const row = stock[stockIndex];
    const systemQty = row.qtyAvailable + row.qtyHeld;
    stocktakeLines.push({ id: stocktakeLines.length + 1, stocktakeId, variantId: row.variantId, lotId: row.lotId, locationId: row.locationId, systemQty, countedQty: counted, diff: (counted ?? 0) - systemQty });
  };
  addCount(1, 0, 24); addCount(1, 1, 71); addCount(1, 3, 12);
  addCount(2, 2, 140); addCount(2, 4, null); addCount(2, 6, null);

  const damageReports: MockDb['damageReports'] = [
    { id: 1, code: 'BH-0001', variantId: 4, lotId: 10, locationId: 6, qty: 4, reason: 'Hỏng do mất lạnh khi vận chuyển', status: 'pending', reportedBy: 4, reviewedBy: null, reportedAt: isoTime(-1, 15), reviewedAt: null, note: null },
    { id: 2, code: 'BH-0002', variantId: 3, lotId: 6, locationId: 4, qty: 2, reason: 'Khay rách, nghi nhiễm bẩn', status: 'pending', reportedBy: 4, reviewedBy: null, reportedAt: isoTime(0, 8), reviewedAt: null, note: null },
    { id: 3, code: 'BH-0003', variantId: 1, lotId: 1, locationId: 1, qty: 3, reason: 'Rơi vỡ khi xếp hàng', status: 'approved', reportedBy: 4, reviewedBy: 2, reportedAt: isoTime(-12, 10), reviewedAt: isoTime(-12, 16), note: null },
  ];

  const requests: T.SalesRequest[] = ([
    [2, 'zalo', 'Hỏi giá 200 khay chả mực cho tháng 12', 'quoting', -1], [3, 'phone', 'Đặt định kỳ cho tiệc cuối năm, 150 khay mực', 'new', 0],
    [null, 'web', 'Khách lẻ hỏi giao hàng về Cẩm Phả', 'new', 0], [5, 'zalo', 'Bổ sung chả tôm cho quầy đông lạnh', 'ordered', -3],
    [4, 'phone', 'Báo giá chả tôm khay 0,5kg', 'closed', -9], [1, 'counter', 'Hỏi trước quà Tết cho công ty', 'new', -1],
    [3, 'phone', 'Xin 500 khay mực giao gấp trong ngày — Sales từ chối', 'rejected', -2],
    [2, 'facebook', 'Inbox fanpage hỏi giá chả mực 50 khay', 'new', -1],
  ] as const).map(([customerId, channel, summary, status, day], i) => ({
    id: i + 1, code: `YC-${pad(i + 1, 4)}`, customerId, customerName: customerId ? customers[customerId - 1].name : 'Khách vãng lai',
    contactPhone: customerId ? null : '0900000000', contactAddress: customerId ? null : null,
    channel, summary, status, receivedAt: isoTime(day, 9 + i), handledBy: status === 'new' ? null : 3, note: null,
    feasible: status === 'rejected' ? false : null,
    fulfillableQty: null,
    shortageQty: status === 'rejected' ? 500 : null,
    expectedDate: null,
    rejectReason: status === 'rejected' ? 'Không đủ hàng và không kịp nhập trong ngày' : null,
  }));

  const quotes: T.Quote[] = [];
  const quoteLines: T.QuoteLine[] = [];
  ([[2, 1, 'sent', 7], [4, 5, 'rejected', -5], [5, 4, 'approved', 3], [3, null, 'draft', 14], [2, null, 'expired', -12]] as const).forEach(([customerId, requestId, status, validIn], i) => {
    const id = i + 1;
    const lines = [{ variantId: 1, qty: 200 }, { variantId: 3, qty: 60 }].slice(0, i % 2 === 0 ? 2 : 1);
    let subtotal = 0;
    lines.forEach((line) => {
      const unitPrice = customerId === 2 && line.variantId === 1 ? 78000 : variants[line.variantId - 1].listPrice - 5000;
      const lineTotal = unitPrice * line.qty;
      subtotal += lineTotal;
      quoteLines.push({ id: quoteLines.length + 1, quoteId: id, variantId: line.variantId, unit: 'pack', qty: line.qty, qtyKhay: line.qty, unitPrice, lineTotal, priceBasis: customerId === 2 && line.variantId === 1 ? 'customer' : 'quote' });
    });
    quotes.push({ id, code: `BG-${pad(id, 4)}`, customerId, requestId, status, subtotal, total: subtotal, validUntil: isoDate(validIn), createdBy: 3, approvedBy: status === 'approved' ? 2 : null, note: null, createdAt: isoTime(validIn - 14, 10), updatedAt: isoTime(validIn - 14, 10) });
  });

  const orders: T.SalesOrder[] = [];
  const orderLines: T.SalesOrderLine[] = [];
  const orderEvents: T.OrderEvent[] = [];
  const deliveries: T.Delivery[] = [];
  const payments: T.Payment[] = [];
  const debtLedger: T.CustomerDebtLedger[] = [];
  const debtBalance = new Map<number, number>();

  const pushEvent = (orderId: number, eventType: string, description: string, at: string, by: number | null = 3) =>
    orderEvents.push({ id: orderEvents.length + 1, orderId, eventType, description, createdBy: by, createdAt: at });
  const pushPayment = (orderId: number, customerId: number, method: PaymentMethod, amount: number, at: string) => {
    const id = payments.length + 1;
    payments.push({
      id, code: `TT-${pad(id, 4)}`, orderId, customerId, method, direction: 'in', amount, status: 'success', reference: method === 'bank_transfer' ? `CK${100000 + id}` : null,
      gateway: method === 'payos' ? 'payos' : method === 'cash' ? 'cash' : 'bank', payosOrderCode: method === 'payos' ? 900000 + id : null, checkoutUrl: null, paidAt: at, receivedBy: 3, note: null,
      reconcileFlag: 'none', needsRefund: false,
    });
    return id;
  };
  const postDebt = (customerId: number, orderId: number | null, paymentId: number | null, entryType: 'charge' | 'payment', amount: number, dueDate: string | null, at: string, note: string) => {
    const balance = (debtBalance.get(customerId) ?? 0) + amount;
    debtBalance.set(customerId, balance);
    debtLedger.push({ id: debtLedger.length + 1, customerId, orderId, paymentId, entryType, amount, balanceAfter: balance, dueDate, createdAt: at, note });
  };

  const makeOrder = (id: number, customerId: number, state: OrderState, day: number, channel: SaleChannel, lineSpec: [number, number][]) => {
    const c = customers[customerId - 1];
    const isB2b = c.customerType === 'b2b';
    const placedAt = isoTime(day, int(8, 18), int(0, 59));
    let subtotal = 0;
    lineSpec.forEach(([variantId, qty]) => {
      const v = variants[variantId - 1];
      const special = customerPrices.find((p) => p.customerId === customerId && p.variantId === variantId && p.approvedAt);
      const tier = tiers.filter((t) => t.variantId === variantId && t.minQtyKhay <= qty).sort((a, b) => b.minQtyKhay - a.minQtyKhay)[0];
      const unitPrice = special?.unitPrice ?? tier?.unitPrice ?? v.listPrice;
      const lineTotal = unitPrice * qty;
      subtotal += lineTotal;
      orderLines.push({ id: orderLines.length + 1, orderId: id, variantId, unit: 'pack', qty, qtyKhay: qty, unitPrice, discountPct: 0, lineTotal, priceBasis: special ? 'customer' : tier ? 'tier' : 'list', outstandingQty: 0, note: null });
    });
    const addressId = addresses.find((a) => a.customerId === customerId)?.id ?? null;
    const distance = addresses.find((a) => a.id === addressId)?.distanceKm ?? 0;
    const shippingFee = isB2b || distance <= 8 ? 0 : 30000;
    const total = subtotal + shippingFee;
    const method: PaymentMethod = isB2b ? 'credit_net' : channel === 'web' ? 'payos' : channel === 'counter' ? 'cash' : pick(['bank_transfer', 'cod'] as const);
    const done = state === 'completed' || state === 'delivered';
    const paymentStatus: T.SalesOrder['paymentStatus'] = state === 'cancelled' || (isB2b && state === 'pending') ? 'unpaid' : isB2b ? 'debt' : done || method === 'payos' ? 'paid' : method === 'bank_transfer' ? 'waiting' : 'unpaid';
    const allocation: T.SalesOrder['allocationStatus'] = state === 'pending' ? 'none' : state === 'cancelled' ? 'none' : 'full';
    orders.push({
      id, code: `SO-2026-${pad(id, 4)}`, customerId, orderType: isB2b ? 'b2b' : channel === 'web' ? 'online_b2c' : 'retail', channel, sourceQuoteId: null, paymentMethod: method,
      paymentTermDays: c.netDays, paymentStatus, allocationStatus: allocation, fulfillmentMethod: channel === 'counter' ? 'pickup' : distance <= 8 ? 'shop_delivery' : 'partner_delivery',
      shipAddressId: addressId, state, subtotal, discountTotal: 0, shippingFee, total, voucherCode: null, isOverLimit: false, overLimitAmount: 0, approvedBy: null,
      placedBy: channel === 'web' ? null : 3, placedAt, holdExpiresAt: null, note: null, createdAt: placedAt, updatedAt: placedAt,
    });
    pushEvent(id, 'created', channel === 'web' ? 'Khách đặt đơn trên website' : 'Tạo đơn', placedAt, channel === 'web' ? null : 3);
    if (state !== 'pending' && state !== 'cancelled') pushEvent(id, 'confirmed', 'Xác nhận đơn, giữ hàng theo FEFO', placedAt);
    if (state === 'cancelled') pushEvent(id, 'cancelled', 'Khách đổi ý', placedAt);
    if (paymentStatus === 'paid') pushPayment(id, customerId, method, total, placedAt);
    if (isB2b && state !== 'pending' && state !== 'cancelled') postDebt(customerId, id, null, 'charge', total, isoDate(day + c.netDays), placedAt, `Đơn SO-2026-${pad(id, 4)}`);

    if (['to_ship', 'partial', 'delivered', 'completed'].includes(state) && channel !== 'counter') {
      const line = orderLines.filter((l) => l.orderId === id);
      const isPartial = state === 'partial';
      const shipped = state === 'delivered' || state === 'completed';
      const base = {
        orderId: id, fulfillmentMethod: orders[id - 1].fulfillmentMethod, addressId, inRadius: distance <= 8, distanceKm: distance,
        assignedTo: distance <= 8 ? 5 : null, carrier: distance <= 8 ? null : 'Giao hàng tiết kiệm', note: null, timeWindow: '08:00 – 11:00',
      };
      const code = (n: number) => `GH-${pad(deliveries.length + n, 4)}`;
      if (isPartial) {
        deliveries.push({ ...base, id: deliveries.length + 1, code: code(1), legLabel: 'Đợt 1/2', scheduledDate: isoDate(day + 1), items: line.map((l) => ({ orderLineId: l.id, qtyKhay: Math.ceil(l.qtyKhay / 2) })), state: 'delivered', deliveredAt: isoTime(day + 1, 10) });
        deliveries.push({ ...base, id: deliveries.length + 1, code: code(1), legLabel: 'Đợt 2/2', scheduledDate: isoDate(1), items: line.map((l) => ({ orderLineId: l.id, qtyKhay: Math.floor(l.qtyKhay / 2) })), state: 'ready', deliveredAt: null });
        pushEvent(id, 'delivery', 'Đã giao đợt 1/2', isoTime(day + 1, 10), 5);
      } else {
        deliveries.push({ ...base, id: deliveries.length + 1, code: code(1), legLabel: 'Trọn đơn', scheduledDate: shipped ? isoDate(day + 1) : isoDate(0), items: line.map((l) => ({ orderLineId: l.id, qtyKhay: l.qtyKhay })), state: shipped ? 'delivered' : pick(['ready', 'going'] as const), deliveredAt: shipped ? isoTime(day + 1, 10) : null });
        if (shipped) pushEvent(id, 'delivery', 'Đã giao hàng', isoTime(day + 1, 10), 5);
      }
    }
    if (state === 'completed') pushEvent(id, 'completed', 'Hoàn tất đơn', isoTime(day + 2, 16));
  };

  const b2bIds = [2, 3, 4, 5];
  const b2cIds = [1, 6, 7, 8];
  const flowStates: OrderState[] = ['pending', 'confirmed', 'preparing', 'to_ship', 'to_ship', 'partial'];
  let orderId = 0;
  for (let day = -62; day <= 0; day++) {
    const dailyOrders = day > -3 ? 3 : day % 7 === 0 ? 1 : int(0, 2);
    for (let n = 0; n < dailyOrders; n++) {
      orderId += 1;
      const isB2b = rnd() < 0.45;
      const customerId = pick(isB2b ? b2bIds : b2cIds);
      const state: OrderState = day > -3 ? pick(flowStates) : rnd() < 0.08 ? 'cancelled' : day > -6 ? 'delivered' : 'completed';
      const channel: SaleChannel = isB2b ? pick(['phone', 'zalo', 'facebook'] as const) : pick(['web', 'web', 'counter', 'phone', 'facebook'] as const);
      const lineCount = isB2b ? int(1, 3) : int(1, 2);
      const spec = [...variants].sort(() => rnd() - 0.5).slice(0, lineCount).map((v): [number, number] => [v.id, isB2b ? int(6, 28) * 2 : int(1, 6)]);
      makeOrder(orderId, customerId, state, day, channel, spec);
    }
  }

  // Đơn vượt hạn mức chờ duyệt của Quán Hải Đăng (FR over-limit).
  orderId += 1;
  makeOrder(orderId, 4, 'pending', 0, 'phone', [[3, 80], [1, 90]]);
  const overLimitOrder = orders[orderId - 1];
  const haiDangDebt = debtBalance.get(4) ?? 0;
  overLimitOrder.isOverLimit = true;
  overLimitOrder.overLimitAmount = Math.max(0, haiDangDebt + overLimitOrder.total - customers[3].creditLimit);

  // Khách B2B thanh toán các khoản cũ, chừa lại khoản còn nợ để tuổi nợ có đủ nhóm.
  b2bIds.forEach((customerId) => {
    const charges = debtLedger.filter((d) => d.customerId === customerId && d.entryType === 'charge');
    const paidCount = customerId === 4 ? Math.floor(charges.length * 0.55) : Math.floor(charges.length * 0.7);
    charges.slice(0, paidCount).forEach((charge) => {
      const at = isoTime(Math.round((new Date(charge.dueDate!).getTime() - Date.now()) / 86_400_000) - 2, 15);
      const paymentId = pushPayment(charge.orderId!, customerId, 'bank_transfer', charge.amount, at);
      postDebt(customerId, charge.orderId, paymentId, 'payment', -charge.amount, null, at, `Thanh toán ${payments[paymentId - 1].code}`);
      const order = orders.find((o) => o.id === charge.orderId);
      if (order) order.paymentStatus = 'paid';
    });
    customers[customerId - 1].currentDebt = debtBalance.get(customerId) ?? 0;
  });
  // Sổ nợ ghi theo thời gian: sắp lại rồi tính số dư lũy kế để khớp bút toán.
  debtLedger.sort((a, b) => a.createdAt.localeCompare(b.createdAt));
  const running = new Map<number, number>();
  debtLedger.forEach((d, i) => {
    d.id = i + 1;
    const balance = (running.get(d.customerId) ?? 0) + d.amount;
    running.set(d.customerId, balance);
    d.balanceAfter = balance;
  });

  const returnOrders = orders.filter((o) => o.state === 'completed').slice(-3);
  const returns: T.SalesReturn[] = returnOrders.map((o, i) => {
    const line = orderLines.find((l) => l.orderId === o.id)!;
    return {
      id: i + 1, code: `TH-${pad(i + 1, 4)}`, orderId: o.id, customerId: o.customerId, reason: ['Khay bị rách khi nhận', 'Sai quy cách', 'Hàng không đạt độ lạnh'][i], amount: line.unitPrice,
      items: [{ variantId: line.variantId, lotId: null, qty: 1, amount: line.unitPrice }], status: (['pending', 'received', 'refunded'] as const)[i], createdBy: 3, createdAt: isoTime(-i - 1, 11),
    };
  });

  const reviews: T.ProductReview[] = ([
    [1, 1, 5, 'Chả mực ngon', 'Giã tay chắc miếng, thơm mùi mực. Giao hàng nhanh.', 'approved', -6], [2, 6, 4, 'Khá ổn', 'Vị ngọt tôm rõ, hơi nhiều dầu khi chiên.', 'approved', -4],
    [3, 8, 5, 'Mực tươi', 'Làm sạch kỹ, không tanh.', 'pending', -1], [1, 7, 2, 'Giao chậm', 'Đá đã tan khi nhận hàng.', 'pending', 0], [2, 1, 1, 'Nội dung không phù hợp', 'Quảng cáo trang khác.', 'rejected', -10],
  ] as const).map(([productId, customerId, rating, title, body, status, day], i) => ({ id: i + 1, productId, customerId, orderId: null, rating, title, body, status, createdAt: isoTime(day, 20) }));

  const vouchers: T.Voucher[] = [
    { id: 1, code: 'HAISAN10', name: 'Giảm 10% đơn đầu', voucherType: 'percent', value: 10, minOrder: 200000, maxDiscount: 50000, startsAt: isoTime(-30), endsAt: isoTime(30), usageLimit: 200, perCustomerLimit: 1, usedCount: 47, isActive: true },
    { id: 2, code: 'FREESHIP', name: 'Miễn phí giao trong bán kính', voucherType: 'freeship', value: 30000, minOrder: 300000, maxDiscount: null, startsAt: isoTime(-15), endsAt: isoTime(15), usageLimit: null, perCustomerLimit: 3, usedCount: 112, isActive: true },
    { id: 3, code: 'TET2026', name: 'Khuyến mãi Tết', voucherType: 'amount', value: 100000, minOrder: 1000000, maxDiscount: null, startsAt: isoTime(-300), endsAt: isoTime(-270), usageLimit: 100, perCustomerLimit: 1, usedCount: 100, isActive: false },
  ];

  const approvals: T.ApprovalTask[] = [
    { id: 1, approvalType: 'over_limit_order', refType: 'sales_order', refId: overLimitOrder.id, title: `Đơn ${overLimitOrder.code} của Quán Hải Đăng vượt hạn mức`, status: 'pending', requestedBy: 3, decidedBy: null, decidedAt: null, note: null, createdAt: overLimitOrder.placedAt },
    { id: 2, approvalType: 'damage_report', refType: 'damage_report', refId: 1, title: 'BH-0001: 4 khay muc-05 hỏng do mất lạnh', status: 'pending', requestedBy: 4, decidedBy: null, decidedAt: null, note: null, createdAt: isoTime(-1, 15) },
    { id: 3, approvalType: 'damage_report', refType: 'damage_report', refId: 2, title: 'BH-0002: 2 khay cha-tom-05 rách, nghi nhiễm bẩn', status: 'pending', requestedBy: 4, decidedBy: null, decidedAt: null, note: null, createdAt: isoTime(0, 8) },
    { id: 4, approvalType: 'price_list', refType: 'customer_price', refId: 3, title: 'Khách sạn Vịnh Ngọc: đề xuất giá riêng muc-05 từ 112.000 ₫ xuống 110.000 ₫', status: 'pending', requestedBy: 3, decidedBy: null, decidedAt: null, note: null, createdAt: isoTime(-1, 9) },
    { id: 5, approvalType: 'damage_report', refType: 'damage_report', refId: 3, title: 'BH-0003: 3 khay cha-muc-05 rơi vỡ', status: 'approved', requestedBy: 4, decidedBy: 2, decidedAt: isoTime(-12, 16), note: 'Đồng ý', createdAt: isoTime(-12, 10) },
  ];

  const notifications: T.Notification[] = [
    { id: 1, userId: 2, type: 'approval', title: '4 việc đang chờ bạn duyệt', body: 'Đơn vượt hạn mức, báo hỏng và giá riêng', link: '/approvals', isRead: false, createdAt: isoTime(0, 8, 5) },
    { id: 2, userId: 2, type: 'expiry', title: 'Lô sắp hết hạn', body: 'Có lô mực nguyên con còn 1 ngày', link: '/inventory?tab=expiry', isRead: false, createdAt: isoTime(0, 7, 0) },
    { id: 3, userId: 2, type: 'debt', title: 'Công nợ quá hạn', body: 'Quán Hải Đăng có khoản đã quá hạn', link: '/debt', isRead: true, createdAt: isoTime(-1, 9) },
    { id: 4, userId: 3, type: 'order', title: 'Đơn online mới', body: 'Có đơn mới từ website cần xác nhận', link: '/orders?state=pending', isRead: false, createdAt: isoTime(0, 8, 40) },
    { id: 5, userId: 4, type: 'expiry', title: 'Lô sắp hết hạn', body: 'Ưu tiên xuất lô mực nguyên con', link: '/inventory?tab=expiry', isRead: false, createdAt: isoTime(0, 7, 0) },
    { id: 6, userId: 5, type: 'delivery', title: 'Lịch giao hôm nay', body: 'Kiểm tra các đợt giao được phân công', link: '/deliveries', isRead: false, createdAt: isoTime(0, 7, 10) },
    { id: 7, userId: 1, type: 'system', title: 'Kết nối hóa đơn điện tử chưa sẵn sàng', body: 'Tích hợp HĐĐT đang ở trạng thái sắp ra mắt', link: '/admin/config', isRead: false, createdAt: isoTime(-2, 9) },
  ];

  const cfg = (key: string, value: string, valueType: T.AppConfig['valueType'], description: string): T.AppConfig => ({ key, value, valueType, description, updatedBy: null, updatedAt: isoTime(-30) });
  const config = [
    cfg('net_default', '15', 'int', 'Số ngày công nợ NET mặc định'), cfg('low_stock_threshold', '15', 'int', 'Ngưỡng cảnh báo tồn thấp (khay)'),
    cfg('expiry_warning_days', '5', 'int', 'Số ngày cảnh báo sắp hết hạn'), cfg('delivery_radius_km', '8', 'int', 'Bán kính shop tự giao (km)'),
    cfg('currency', 'VND', 'string', 'Đơn vị tiền tệ'), cfg('integration.payos', '{"status":"connected","name":"PayOS — QR/chuyển khoản"}', 'json', 'Cổng thanh toán PayOS'),
    cfg('integration.einvoice', '{"status":"coming","name":"Kế toán / Hóa đơn điện tử"}', 'json', 'Hóa đơn điện tử'),
  ];

  const auditLog: T.AuditLog[] = [
    { id: 1, userId: 2, action: 'approve', entityType: 'damage_report', entityId: 3, detail: { status: 'approved' }, ipAddress: '10.0.0.12', createdAt: isoTime(-12, 16) },
    { id: 2, userId: 1, action: 'update', entityType: 'app_config', entityId: null, detail: { key: 'low_stock_threshold', value: '15' }, ipAddress: '10.0.0.4', createdAt: isoTime(-30, 9) },
    { id: 3, userId: 3, action: 'login', entityType: 'app_user', entityId: 3, detail: null, ipAddress: '10.0.0.21', createdAt: isoTime(-1, 8, 3) },
  ];

  return {
    roles, users, customers, addresses, categories, products, variants, tiers, customerPrices, warehouses, locations, rawMaterials, suppliers, lots, stock, ledger, receipts, stocktakes, stocktakeLines,
    damageReports, requests, quotes, quoteLines, orders, orderLines, deliveries, returns, orderEvents, payments, debtLedger, reviews, vouchers, approvals, notifications, config, auditLog,
  };
}

function load(): MockDb {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) return JSON.parse(raw) as MockDb;
  } catch {
    // Dữ liệu lưu hỏng hoặc storage bị chặn: dùng lại seed.
  }
  return seed();
}

export let db: MockDb = load();

export function saveDb() {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(db));
  } catch {
    // Storage đầy hoặc bị chặn: dữ liệu vẫn dùng được đến khi tải lại trang.
  }
}

export function resetDb() {
  db = seed();
  saveDb();
}

export function nextId(rows: { id: number }[]) {
  return rows.reduce((max, row) => Math.max(max, row.id), 0) + 1;
}

export function nextCode(prefix: string, rows: { code?: string | null }[], width = 4) {
  const max = rows.reduce((m, row) => {
    const n = Number(row.code?.split('-').pop());
    return Number.isFinite(n) ? Math.max(m, n) : m;
  }, 0);
  return `${prefix}-${String(max + 1).padStart(width, '0')}`;
}
