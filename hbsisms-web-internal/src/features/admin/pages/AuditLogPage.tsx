import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { ScrollText } from 'lucide-react';
import { Link } from 'react-router-dom';
import { ordersApi } from '@/features/orders/api';
import { productionApi } from '@/features/production/api';
import { quotesApi } from '@/features/quotes/api';
import { receiptsApi } from '@/features/receipts/api';
import { date, dateTime, money, num } from '@/shared/lib/format';
import {
  Button,
  Card,
  Chip,
  DateInput,
  ErrorState,
  FilterBar,
  FilterSelect,
  PageHeader,
  Pagination,
  SearchBox,
  useListParams,
} from '@/shared/ui';
import { adminApi } from '../api';
import type { AuditRow } from '../types';

/** Nhãn hiển thị trên timeline / chip (kể cả action lạ). */
const ACTION_LABEL: Record<string, string> = {
  create: 'Tạo',
  update: 'Cập nhật',
  delete: 'Xóa',
  approve: 'Duyệt',
  reject: 'Từ chối',
  login: 'Đăng nhập',
  adjust: 'Điều chỉnh tồn',
  convert: 'Chuyển thành đơn',
  complete: 'Hoàn thành',
  cancel: 'Hủy',
  refund: 'Hoàn tiền',
  reconcile: 'Đối soát',
  print_receipt: 'In phiếu',
  complete_counter: 'Hoàn tất quầy',
  register: 'Đăng ký',
  link_account: 'Liên kết tài khoản',
  apply_sell_price: 'Áp dụng giá bán',
  open: 'Mở kỳ',
  close: 'Khóa sổ',
};

/** Chỉ các hành động nghiệp vụ thường gặp — dùng cho dropdown lọc. */
const FILTER_ACTIONS: { value: string; label: string }[] = [
  { value: 'create', label: 'Tạo' },
  { value: 'update', label: 'Cập nhật' },
  { value: 'approve', label: 'Duyệt' },
  { value: 'complete', label: 'Hoàn thành' },
  { value: 'cancel', label: 'Hủy' },
  { value: 'apply_sell_price', label: 'Áp dụng giá bán' },
  { value: 'open', label: 'Mở kỳ giá vốn' },
  { value: 'close', label: 'Khóa sổ giá vốn' },
  { value: 'convert', label: 'Chuyển thành đơn' },
  { value: 'print_receipt', label: 'In phiếu' },
  { value: 'adjust', label: 'Điều chỉnh tồn' },
  { value: 'refund', label: 'Hoàn tiền' },
  { value: 'reconcile', label: 'Đối soát' },
  { value: 'complete_counter', label: 'Hoàn tất quầy' },
];

const ACTION_VERB: Record<string, string> = {
  create: 'đã tạo',
  update: 'đã cập nhật',
  delete: 'đã xóa',
  approve: 'đã duyệt',
  reject: 'đã từ chối',
  login: 'đã đăng nhập',
  adjust: 'đã điều chỉnh',
  convert: 'đã chuyển',
  complete: 'đã hoàn thành',
  cancel: 'đã hủy',
  refund: 'đã hoàn tiền',
  reconcile: 'đã đối soát',
  print_receipt: 'đã in phiếu',
  complete_counter: 'đã hoàn tất quầy',
  register: 'đã đăng ký',
  link_account: 'đã liên kết',
  apply_sell_price: 'đã áp dụng giá bán cho',
  open: 'đã mở',
  close: 'đã khóa sổ',
};

const ENTITY_LABEL: Record<string, string> = {
  app_config: 'Cấu hình',
  app_user: 'Người dùng',
  app_role: 'Vai trò',
  sales_order: 'Đơn hàng',
  quote: 'Báo giá',
  stock: 'Tồn kho',
  damage_report: 'Báo hỏng',
  stocktake: 'Kiểm kê',
  customer_price: 'Giá riêng',
  goods_receipt: 'Phiếu nhập',
  production_order: 'Lệnh sản xuất',
  payment: 'Thanh toán',
  delivery: 'Giao hàng',
  sales_return: 'Trả hàng',
  customer: 'Khách hàng',
  product: 'Sản phẩm',
  product_variant: 'Quy cách',
  cost_period: 'Kỳ giá vốn',
};

/** Khớp entityType thật mà API ghi audit — tránh chọn mục không bao giờ có dữ liệu. */
const FILTER_ENTITIES: { value: string; label: string }[] = [
  { value: 'goods_receipt', label: 'Phiếu nhập' },
  { value: 'sales_order', label: 'Đơn hàng' },
  { value: 'quote', label: 'Báo giá' },
  { value: 'production_order', label: 'Lệnh sản xuất' },
  { value: 'product_variant', label: 'Quy cách / giá bán' },
  { value: 'cost_period', label: 'Kỳ giá vốn' },
  { value: 'payment', label: 'Thanh toán' },
  { value: 'delivery', label: 'Giao hàng' },
  { value: 'sales_return', label: 'Trả hàng' },
  { value: 'stock', label: 'Tồn kho' },
  { value: 'app_config', label: 'Cấu hình hệ thống' },
  { value: 'customer', label: 'Khách hàng' },
];

const ACTION_TONE: Record<string, 'green' | 'amber' | 'red' | 'blue' | 'neutral'> = {
  create: 'green',
  update: 'blue',
  delete: 'red',
  approve: 'green',
  reject: 'red',
  login: 'neutral',
  adjust: 'amber',
  convert: 'blue',
  complete: 'green',
  cancel: 'red',
  refund: 'amber',
  reconcile: 'blue',
  print_receipt: 'neutral',
  complete_counter: 'green',
  apply_sell_price: 'green',
  open: 'amber',
  close: 'blue',
};

/** Key cấu hình / field audit → nhãn tiếng Việt. */
const FIELD_LABEL: Record<string, string> = {
  sell_price_markup_percent: '% lời giá bán',
  low_stock_threshold: 'Ngưỡng tồn thấp',
  order_discount_approve_pct: '% giảm giá cần duyệt',
  oldListPrice: 'Giá cũ',
  newListPrice: 'Giá mới',
  unitCost: 'Giá vốn',
  suggestedSellPrice: 'Giá gợi ý',
  markupPercent: '% lời',
  markupSource: 'Nguồn % lời',
  costSource: 'Nguồn giá vốn',
  sku: 'SKU',
  yearMonth: 'Kỳ',
  WAC: 'WAC (bình quân kỳ)',
  PRODUCTION: 'Giá thành lệnh SX',
};

const DOT_CLASS: Record<'green' | 'amber' | 'red' | 'blue' | 'neutral', string> = {
  green: 'bg-green-fg',
  amber: 'bg-amber-fg',
  red: 'bg-red-fg',
  blue: 'bg-blue-fg',
  neutral: 'bg-neutral-fg',
};

const VALUE_LABEL: Record<string, string> = {
  pending: 'chờ xử lý',
  completed: 'hoàn thành',
  cancelled: 'đã hủy',
  canceled: 'đã hủy',
  draft: 'nháp',
  approved: 'đã duyệt',
  in_progress: 'đang sản xuất',
  awaiting_receipt: 'chờ nhập kho',
  rejected: 'từ chối',
  paid: 'đã thanh toán',
  unpaid: 'chưa thanh toán',
  partial: 'thanh toán một phần',
  raw: 'nguyên liệu',
  finished: 'thành phẩm',
  delivery: 'giao hàng',
  pickup: 'nhận tại quầy',
  refund: 'hoàn tiền',
  sent: 'đã gửi',
  accepted: 'đã chấp nhận',
  true: 'có',
  false: 'không',
};

function humanizeCode(code: string) {
  return code.replace(/_/g, ' ').trim();
}

function actionLabel(action: string) {
  return ACTION_LABEL[action] ?? humanizeCode(action);
}

function fieldLabel(key: string) {
  return FIELD_LABEL[key] ?? humanizeCode(key);
}

function detailRecord(detail: unknown): Record<string, unknown> | null {
  if (detail == null || typeof detail !== 'object') return null;
  return detail as Record<string, unknown>;
}

function businessCode(row: Pick<AuditRow, 'entityCode' | 'detail'>): string | null {
  if (row.entityCode != null && String(row.entityCode).trim()) return String(row.entityCode).trim();
  const d = detailRecord(row.detail);
  if (!d) return null;
  if (d.code != null && String(d.code).trim()) return String(d.code).trim();
  if (d.orderCode != null && String(d.orderCode).trim()) return String(d.orderCode).trim();
  return null;
}

/** Deep-link tới chứng từ — chuẩn audit: bấm vào là mở đúng chỗ. */
function entityHref(row: AuditRow): string | null {
  const type = row.entityType;
  const id = row.entityId;
  if (!type) return null;
  const code = businessCode(row);

  switch (type) {
    case 'sales_order':
      return id != null ? `/orders/${id}` : '/orders';
    case 'quote':
      return id != null ? `/quotes/${id}` : '/quotes';
    case 'production_order':
      return id != null ? `/production-orders/${id}` : '/production-orders';
    case 'stocktake':
      return id != null ? `/stocktakes/${id}` : '/stocktakes';
    case 'goods_receipt':
      if (code) return `/receipts?q=${encodeURIComponent(code)}`;
      return id != null ? `/receipts?id=${id}` : '/receipts';
    case 'payment':
      return code ? `/payments?q=${encodeURIComponent(code)}` : '/payments';
    case 'delivery':
      return code ? `/deliveries?q=${encodeURIComponent(code)}` : '/deliveries';
    case 'sales_return':
      return code ? `/returns?q=${encodeURIComponent(code)}` : '/returns';
    case 'damage_report':
      return '/damage';
    case 'stock':
      return '/inventory';
    case 'app_config':
      return '/admin/config';
    case 'app_user':
      return '/admin/users';
    case 'app_role':
      return '/admin/roles';
    case 'product_variant': {
      const d = detailRecord(row.detail);
      const productId = d?.productId != null ? Number(d.productId) : null;
      if (productId && Number.isFinite(productId)) return `/products/${productId}`;
      return '/products';
    }
    case 'product':
      return row.entityId != null ? `/products/${row.entityId}` : '/products';
    case 'cost_period':
      return '/costing';
    default:
      return null;
  }
}

/** Ưu tiên mã nghiệp vụ (PN-… / SO-… / SKU); fallback ngắn gọn, không lộ snake_case. */
function entityPhrase(row: AuditRow) {
  const type = row.entityType;
  if (!type) return 'một đối tượng';
  const name = (ENTITY_LABEL[type] ?? humanizeCode(type)).toLowerCase();
  const code = businessCode(row);
  if (code) return `${name} ${code}`;
  const d = detailRecord(row.detail);
  if (type === 'product_variant' && d?.sku != null && String(d.sku).trim()) {
    return `quy cách ${String(d.sku).trim()}`;
  }
  if (type === 'cost_period' && d?.yearMonth != null) {
    return `kỳ giá vốn ${d.yearMonth}`;
  }
  if (type === 'app_config' && d?.key != null) {
    return `cấu hình «${fieldLabel(String(d.key))}»`;
  }
  return row.entityId != null ? `${name}` : name;
}

function prettyValue(raw: unknown): string {
  if (raw == null) return '';
  if (typeof raw === 'boolean') return VALUE_LABEL[String(raw)] ?? (raw ? 'có' : 'không');
  if (typeof raw === 'object') return '';
  const text = String(raw).trim();
  if (!text) return '';
  return VALUE_LABEL[text.toLowerCase()] ?? text;
}

function detailCaption(detail: unknown): string | null {
  if (detail == null) return null;
  if (typeof detail !== 'object') {
    const text = prettyValue(detail) || String(detail);
    return text || null;
  }

  const d = detail as Record<string, unknown>;
  const bits: string[] = [];

  const receiptType = prettyValue(d.receiptType);
  const status = prettyValue(d.status ?? d.state);

  if (receiptType && status) bits.push(`Phiếu ${receiptType}, ${status}`);
  else if (receiptType) bits.push(`Phiếu ${receiptType}`);
  else if (status) bits.push(status.charAt(0).toUpperCase() + status.slice(1));

  if (d.oldListPrice != null && d.newListPrice != null
    && Number.isFinite(Number(d.oldListPrice)) && Number.isFinite(Number(d.newListPrice))) {
    bits.push(`${money(Number(d.oldListPrice))} → ${money(Number(d.newListPrice))}`);
  } else if (d.newListPrice != null && Number.isFinite(Number(d.newListPrice))) {
    bits.push(`Giá mới ${money(Number(d.newListPrice))}`);
  }

  if (d.total != null && Number.isFinite(Number(d.total))) bits.push(`Tổng ${money(Number(d.total))}`);
  else if (d.amount != null && Number.isFinite(Number(d.amount))) bits.push(`Số tiền ${money(Number(d.amount))}`);

  if (d.qtyExpected != null && Number.isFinite(Number(d.qtyExpected))) bits.push(`Dự kiến ${num(Number(d.qtyExpected))}`);
  else if (d.qty != null && Number.isFinite(Number(d.qty))) bits.push(`Số lượng ${num(Number(d.qty))}`);
  if (d.qtyReceived != null && Number.isFinite(Number(d.qtyReceived))) bits.push(`Nhận ${num(Number(d.qtyReceived))}`);
  if (d.qtyDelta != null && Number.isFinite(Number(d.qtyDelta))) bits.push(`Chênh lệch ${num(Number(d.qtyDelta))}`);
  if (d.issuedQty != null && Number.isFinite(Number(d.issuedQty))) bits.push(`Xuất ${num(Number(d.issuedQty))}`);
  if (d.lines != null && Number.isFinite(Number(d.lines))) bits.push(`${num(Number(d.lines))} dòng`);

  if (d.unitCost != null && Number.isFinite(Number(d.unitCost))) bits.push(`Giá vốn ${money(Number(d.unitCost))}`);
  if (d.suggestedSellPrice != null && Number.isFinite(Number(d.suggestedSellPrice))) {
    bits.push(`Giá gợi ý ${money(Number(d.suggestedSellPrice))}`);
  }
  if (d.markupPercent != null && Number.isFinite(Number(d.markupPercent))) {
    bits.push(`Lời ${d.markupPercent}%`);
  }
  if (d.costSource != null) {
    const src = String(d.costSource);
    bits.push(FIELD_LABEL[src] ?? prettyValue(src) ?? src);
  }

  if (d.sourceQuoteId != null) bits.push(`Từ báo giá #${d.sourceQuoteId}`);
  if (d.orderId != null) bits.push(`Theo đơn #${d.orderId}`);
  if (d.productionOrderId != null) bits.push(`Theo lệnh sản xuất #${d.productionOrderId}`);
  if (d.yearMonth != null) bits.push(`Kỳ ${d.yearMonth}`);

  if (d.key != null) {
    const label = fieldLabel(String(d.key));
    bits.push(d.value != null ? `${label}: ${prettyValue(d.value) || String(d.value)}` : label);
  }

  if (d.awaitingPriceApproval === true) bits.push('Chờ duyệt giá');
  if (d.paymentStatus != null) bits.push(prettyValue(d.paymentStatus));
  if (d.fulfillmentMethod != null) bits.push(prettyValue(d.fulfillmentMethod));
  if (d.legLabel != null) bits.push(String(d.legLabel));
  if (d.direction != null) bits.push(prettyValue(d.direction));

  const note =
    d.note != null && String(d.note).trim()
      ? String(d.note).trim()
      : d.reason != null && String(d.reason).trim()
        ? String(d.reason).trim()
        : null;

  if (!bits.length && note) return note;
  if (!bits.length) return null;
  const head = bits.slice(0, 4).join(' · ');
  return note && bits.length <= 2 ? `${head} — ${note}` : head;
}

function localDayKey(value: string | Date) {
  const d = typeof value === 'string' ? new Date(value) : value;
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

function dayHeading(iso: string) {
  const today = new Date();
  const yesterday = new Date(today);
  yesterday.setDate(today.getDate() - 1);
  const key = localDayKey(iso);
  if (key === localDayKey(today)) return 'Hôm nay';
  if (key === localDayKey(yesterday)) return 'Hôm qua';
  return date(iso);
}

function groupByDay(rows: AuditRow[]) {
  const groups: { key: string; label: string; items: AuditRow[] }[] = [];
  for (const row of rows) {
    const key = localDayKey(row.createdAt);
    const last = groups[groups.length - 1];
    if (last && last.key === key) last.items.push(row);
    else groups.push({ key, label: dayHeading(row.createdAt), items: [row] });
  }
  return groups;
}

function EntityLink({ row }: { row: AuditRow }) {
  const phrase = entityPhrase(row);
  const href = entityHref(row);
  if (!href) return <span className="font-medium">{phrase}</span>;
  return (
    <Link to={href} className="font-medium text-primary hover:underline">
      {phrase}
    </Link>
  );
}

/** Khi API chưa trả entityCode — FE tự lấy mã PN/SO/… để hiện đúng trên nhật ký. */
async function resolveMissingCodes(rows: AuditRow[], signal?: AbortSignal) {
  const needed = new Map<string, { type: string; id: number }>();
  for (const row of rows) {
    if (businessCode(row) || row.entityId == null || !row.entityType) continue;
    const key = `${row.entityType}:${row.entityId}`;
    if (!needed.has(key)) needed.set(key, { type: row.entityType, id: row.entityId });
  }
  const codes: Record<string, string> = {};
  await Promise.all(
    [...needed.values()].map(async ({ type, id }) => {
      try {
        let code: string | null | undefined;
        if (type === 'goods_receipt') code = (await receiptsApi.get(id, signal)).code;
        else if (type === 'sales_order') code = (await ordersApi.get(id)).code;
        else if (type === 'quote') code = (await quotesApi.get(id)).code;
        else if (type === 'production_order') code = (await productionApi.get(id, signal)).code;
        if (code?.trim()) codes[`${type}:${id}`] = code.trim();
      } catch {
        /* chứng từ đã xóa / không quyền — giữ #id */
      }
    }),
  );
  return codes;
}

export default function AuditLogPage() {
  const list = useListParams(['action', 'entityType', 'dateFrom', 'dateTo'] as const);
  const query = {
    q: list.q,
    page: list.page,
    pageSize: 20,
    action: list.filters.action || undefined,
    entityType: list.filters.entityType || undefined,
    dateFrom: list.filters.dateFrom || undefined,
    dateTo: list.filters.dateTo || undefined,
  };
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['audit', query],
    queryFn: ({ signal }) => adminApi.audit(query, signal),
    placeholderData: keepPreviousData,
  });

  const missingKeys = useMemo(() => {
    if (!data?.items?.length) return [] as string[];
    const keys = new Set<string>();
    for (const row of data.items) {
      if (businessCode(row) || row.entityId == null || !row.entityType) continue;
      if (['goods_receipt', 'sales_order', 'quote', 'production_order'].includes(row.entityType)) {
        keys.add(`${row.entityType}:${row.entityId}`);
      }
    }
    return [...keys].sort();
  }, [data?.items]);

  const { data: resolvedCodes } = useQuery({
    queryKey: ['audit-entity-codes', missingKeys],
    queryFn: ({ signal }) => resolveMissingCodes(data?.items ?? [], signal),
    enabled: missingKeys.length > 0,
    staleTime: 60_000,
  });

  const items = useMemo(() => {
    if (!data?.items?.length) return [] as AuditRow[];
    let rows = data.items;
    if (resolvedCodes && Object.keys(resolvedCodes).length) {
      rows = rows.map((row) => {
        if (businessCode(row) || row.entityId == null || !row.entityType) return row;
        const code = resolvedCodes[`${row.entityType}:${row.entityId}`];
        return code ? { ...row, entityCode: code } : row;
      });
    }
    // Lọc thêm phía FE — vẫn đúng khi API chưa nhận dateFrom/dateTo (chưa restart).
    const { action, entityType, dateFrom, dateTo } = list.filters;
    if (action) rows = rows.filter((r) => r.action === action);
    if (entityType) rows = rows.filter((r) => r.entityType === entityType);
    if (dateFrom) rows = rows.filter((r) => localDayKey(r.createdAt) >= dateFrom);
    if (dateTo) rows = rows.filter((r) => localDayKey(r.createdAt) <= dateTo);
    return rows;
  }, [data?.items, resolvedCodes, list.filters]);

  const groups = items.length ? groupByDay(items) : [];

  const hasFilters =
    !!list.q || !!list.filters.action || !!list.filters.entityType || !!list.filters.dateFrom || !!list.filters.dateTo;

  return (
    <>
      <PageHeader
        title="Nhật ký hệ thống"
        subtitle="Theo dõi thao tác quan trọng — chỉ xem, không sửa"
        actions={
          <span className="inline-flex items-center gap-1.5 rounded-md border border-line bg-card px-2.5 py-1.5 text-small text-ink-muted shadow-card">
            <ScrollText size={14} className="text-primary" />
            Chỉ đọc
          </span>
        }
      />
      <Card flush className="overflow-hidden">
        <FilterBar
          actions={
            hasFilters ? (
              <Button type="button" variant="ghost" onClick={list.clearFilters}>
                Xóa bộ lọc
              </Button>
            ) : undefined
          }
        >
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm người, hành động..." />
          <FilterSelect label="Hành động" value={list.filters.action} onChange={(v) => list.setFilter('action', v)}>
            {FILTER_ACTIONS.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </FilterSelect>
          <FilterSelect label="Đối tượng" value={list.filters.entityType} onChange={(v) => list.setFilter('entityType', v)}>
            {FILTER_ENTITIES.map(({ value, label }) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </FilterSelect>
          <div className="flex w-full flex-wrap items-center gap-2 sm:w-auto" role="group" aria-label="Khoảng ngày">
            <span className="text-small text-ink-muted">Ngày</span>
            <DateInput
              aria-label="Từ ngày"
              className="w-[150px]"
              value={list.filters.dateFrom}
              max={list.filters.dateTo || undefined}
              onChange={(value) => {
                if (value && list.filters.dateTo && value > list.filters.dateTo) {
                  list.setFilters({ dateFrom: value, dateTo: '' });
                } else {
                  list.setFilter('dateFrom', value);
                }
              }}
            />
            <span className="text-small text-ink-muted">→</span>
            <DateInput
              aria-label="Đến ngày"
              className="w-[150px]"
              value={list.filters.dateTo}
              min={list.filters.dateFrom || undefined}
              onChange={(value) => {
                if (value && list.filters.dateFrom && value < list.filters.dateFrom) {
                  list.setFilters({ dateTo: value, dateFrom: '' });
                } else {
                  list.setFilter('dateTo', value);
                }
              }}
            />
          </div>
        </FilterBar>

        {error ? (
          <ErrorState error={error} onRetry={refetch} />
        ) : isLoading && !data ? (
          <div aria-busy className="space-y-3 px-4 py-5">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="flex gap-3">
                <div className="h-10 w-14 animate-pulse rounded bg-rowalt" />
                <div className="h-10 flex-1 animate-pulse rounded bg-rowalt" />
              </div>
            ))}
          </div>
        ) : !items.length ? (
          <p className="px-4 py-12 text-center text-ink-muted">
            {data?.items?.length
              ? 'Không có nhật ký khớp bộ lọc'
              : 'Chưa có nhật ký — thao tác cấu hình, đơn, kho… sẽ hiện tại đây'}
          </p>
        ) : (
          <div className="divide-y divide-line">
            {groups.map((g) => (
              <section key={g.key}>
                <div className="sticky top-0 z-[1] border-b border-line bg-rowalt/80 px-4 py-1.5 text-micro font-semibold uppercase tracking-wide text-ink-muted backdrop-blur-sm">
                  {g.label}
                </div>
                <ol>
                  {g.items.map((row, idx) => {
                    const tone = ACTION_TONE[row.action] ?? 'neutral';
                    const who = row.userName?.trim() || 'Hệ thống';
                    const verb = ACTION_VERB[row.action] ?? `đã ${humanizeCode(row.action)}`;
                    const caption = detailCaption(row.detail);
                    const time = dateTime(row.createdAt).split(' ').pop() ?? dateTime(row.createdAt);
                    return (
                      <li key={row.id} className="relative flex gap-3 px-4 py-3.5 hover:bg-rowalt/40">
                        <div className="w-12 shrink-0 pt-0.5 text-right">
                          <div className="tabular-nums text-small font-medium text-ink-muted">{time}</div>
                        </div>
                        <div className="relative flex w-4 shrink-0 justify-center">
                          <span className={`mt-1.5 h-2.5 w-2.5 rounded-full ${DOT_CLASS[tone]}`} aria-hidden />
                          {idx < g.items.length - 1 && (
                            <span className="absolute top-4 bottom-[-14px] w-px bg-line" aria-hidden />
                          )}
                        </div>
                        <div className="min-w-0 flex-1 space-y-1">
                          <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
                            <p className="text-body leading-snug text-ink">
                              <span className="font-semibold">{who}</span>
                              <span className="text-ink-muted"> {verb} </span>
                              <EntityLink row={row} />
                            </p>
                            <Chip tone={tone}>{actionLabel(row.action)}</Chip>
                          </div>
                          {caption ? <p className="text-small leading-relaxed text-ink-muted">{caption}</p> : null}
                        </div>
                      </li>
                    );
                  })}
                </ol>
              </section>
            ))}
          </div>
        )}

        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
    </>
  );
}
