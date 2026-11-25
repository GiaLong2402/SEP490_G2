import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { isMockMode } from '@/shared/api/config';
import { useAction } from '@/shared/api/useAction';
import { resetMockData } from '../resetMockData';
import { dateTime } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, ErrorState, Input, PageHeader, PageLoading } from '@/shared/ui';
import { adminApi } from '../api';
import type { AppConfig } from '../types';

type GroupId = 'inventory' | 'sales' | 'payment' | 'other';

const GROUPS: { id: GroupId; title: string; blurb: string; keys: string[] }[] = [
  {
    id: 'inventory',
    title: 'Kho & cảnh báo',
    blurb: 'Tồn thấp và hạn sử dụng — ảnh hưởng Dashboard / Kho.',
    keys: ['low_stock_threshold', 'expiry_warning_days'],
  },
  {
    id: 'sales',
    title: 'Bán hàng',
    blurb: 'Giữ hàng quầy, duyệt giảm giá, gợi ý giá bán.',
    keys: ['sales_unpaid_hold_hours', 'sales_hold_warn_minutes', 'order_discount_approve_pct', 'sell_price_markup_percent'],
  },
  {
    id: 'payment',
    title: 'Thanh toán & online',
    blurb: 'Giữ đơn PayOS, ân hạn, phí giao.',
    keys: ['payment_hold_minutes', 'payment_grace_minutes', 'shipping_fee_delivery'],
  },
];

/** Tiêu đề ngắn + giải thích — tránh mô tả kỹ thuật dài từ DB. */
const COPY: Record<string, { title: string; hint: string }> = {
  low_stock_threshold: {
    title: 'Cảnh báo tồn thấp',
    hint: 'SKU còn dưới mức này (tổng mọi lô) → báo trên Dashboard / Kho.',
  },
  expiry_warning_days: {
    title: 'Cảnh báo sắp hết hạn',
    hint: 'Lô còn hạn trong số ngày này → hiện “sắp hết hạn”.',
  },
  sales_unpaid_hold_hours: {
    title: 'Thời gian giữ hàng (Sales)',
    hint: 'Đơn quầy / điện thoại / Zalo chưa thanh toán được giữ tối đa bao lâu.',
  },
  sales_hold_warn_minutes: {
    title: 'Nhắc trước khi hết giữ hàng',
    hint: 'Báo Sales trước khi hết hạn giữ đơn chưa thanh toán.',
  },
  order_discount_approve_pct: {
    title: 'Giảm giá cần Chủ duyệt',
    hint: 'Chiết khấu trên tạm tính vượt mức này → chờ Chủ duyệt.',
  },
  sell_price_markup_percent: {
    title: '% lời gợi ý giá bán',
    hint: 'Giá vốn/khay × (1 + % này). Chỉ gợi ý sau sản xuất, không tự đổi giá đang bán.',
  },
  payment_hold_minutes: {
    title: 'Thời gian giữ hàng (online)',
    hint: 'Đơn web chưa thanh toán PayOS được giữ tối đa bao lâu.',
  },
  payment_grace_minutes: {
    title: 'Ân hạn nhận thanh toán',
    hint: 'Thêm vài phút sau hết giữ hàng để nhận giao dịch đang chuyển.',
  },
  shipping_fee_delivery: {
    title: 'Phí giao tận nơi (online)',
    hint: 'Cộng vào đơn web khi khách chọn giao tận nơi. Nhận tại shop = 0.',
  },
  net_default: {
    title: 'Công nợ NET mặc định',
    hint: 'Số ngày công nợ mặc định khi mở hạn mức khách.',
  },
  delivery_radius_km: {
    title: 'Bán kính shop tự giao',
    hint: 'Khoảng cách tối đa shop tự giao (km).',
  },
};

function unitFor(key: string): string | null {
  if (key.endsWith('_days') || key.includes('warning_days')) return 'ngày';
  if (key.endsWith('_hours')) return 'giờ';
  if (key.endsWith('_minutes')) return 'phút';
  if (key.endsWith('_pct') || key.endsWith('_percent')) return '%';
  if (key.includes('fee') || key.includes('shipping')) return '₫';
  if (key.endsWith('_km')) return 'km';
  if (key === 'low_stock_threshold') return 'khay';
  if (key === 'net_default') return 'ngày';
  return null;
}

function ConfigRow({ row }: { row: AppConfig }) {
  const [value, setValue] = useState(row.value ?? '');
  useEffect(() => setValue(row.value ?? ''), [row.value]);
  const save = useAction(() => adminApi.updateConfig(row.key, value), { success: 'Đã lưu cấu hình' });
  const invalid = row.valueType === 'int' && !/^\d+$/.test(value);
  const dirty = value !== (row.value ?? '');
  const unit = unitFor(row.key);
  const copy = COPY[row.key];
  const title = copy?.title ?? row.description ?? row.key;
  const hint = copy?.hint;

  return (
    <form
      className="rounded-md border border-line bg-canvas/40 p-3"
      onSubmit={(event) => {
        event.preventDefault();
        if (!invalid && dirty) save.mutate();
      }}
    >
      <label className="block text-small font-medium text-ink" htmlFor={`cfg-${row.key}`} title={row.key}>
        {title}
      </label>
      {hint && <p className="mt-0.5 text-micro leading-snug text-ink-muted">{hint}</p>}
      <div className="mt-2 flex items-center gap-2">
        <div className="relative min-w-0 flex-1">
          <Input
            id={`cfg-${row.key}`}
            type={row.valueType === 'int' ? 'number' : 'text'}
            min={0}
            value={value}
            onChange={(e) => setValue(e.target.value)}
            className={unit ? 'pr-12' : undefined}
            aria-invalid={invalid || undefined}
          />
          {unit && (
            <span className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-micro text-ink-faint">{unit}</span>
          )}
        </div>
        <Button type="submit" disabled={!dirty || invalid} loading={save.isPending}>
          Lưu
        </Button>
      </div>
      {invalid ? (
        <p className="mt-1.5 text-micro text-red-fg">Phải là số nguyên không âm</p>
      ) : (
        <p className="mt-1.5 text-micro text-ink-faint">Cập nhật {dateTime(row.updatedAt)}</p>
      )}
    </form>
  );
}

function groupSettings(settings: AppConfig[]) {
  const used = new Set<string>();
  const sections = GROUPS.map((g) => {
    const rows = g.keys.map((k) => settings.find((s) => s.key === k)).filter((r): r is AppConfig => Boolean(r));
    rows.forEach((r) => used.add(r.key));
    return { ...g, rows };
  }).filter((g) => g.rows.length > 0);

  const rest = settings.filter((s) => !used.has(s.key));
  if (rest.length) {
    sections.push({
      id: 'other',
      title: 'Khác',
      blurb: 'Tham số bổ sung.',
      keys: rest.map((r) => r.key),
      rows: rest,
    });
  }
  return sections;
}

export default function ConfigPage() {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['admin-config'], queryFn: adminApi.config });
  const [resetting, setResetting] = useState(false);

  const { sections, integrations } = useMemo(() => {
    if (!data) return { sections: [], integrations: [] as { key: string; description: string | null; status?: string; name?: string }[] };
    const settings = data.filter((c) => c.valueType !== 'json');
    const integrations = data
      .filter((c) => c.valueType === 'json')
      .map((c) => ({ key: c.key, description: c.description, ...(JSON.parse(c.value ?? '{}') as { status?: string; name?: string }) }));
    return { sections: groupSettings(settings), integrations };
  }, [data]);

  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;

  return (
    <>
      <PageHeader title="Cấu hình hệ thống" subtitle="Ngưỡng cảnh báo, giữ hàng và phí — sửa từng mục rồi Lưu" />

      <div className="space-y-4">
        {sections.map((section) => (
          <Card
            key={section.id}
            title={
              <span className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
                <span>{section.title}</span>
                <span className="text-small font-normal text-ink-muted">{section.blurb}</span>
              </span>
            }
          >
            <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-3">{section.rows.map((row) => <ConfigRow key={row.key} row={row} />)}</div>
          </Card>
        ))}

        {integrations.length > 0 && (
          <Card title="Tích hợp">
            <ul className="divide-y divide-line">
              {integrations.map((item) => (
                <li key={item.key} className="flex items-start justify-between gap-3 py-3 first:pt-0 last:pb-0">
                  <div>
                    <div className="font-medium text-ink">{item.name}</div>
                    <div className="text-small text-ink-faint">{item.description}</div>
                  </div>
                  <Chip tone={item.status === 'connected' ? 'green' : 'neutral'}>
                    {item.status === 'connected' ? 'Đã kết nối' : 'Sắp có'}
                  </Chip>
                </li>
              ))}
            </ul>
          </Card>
        )}

        {isMockMode && (
          <Card title="Dữ liệu demo">
            <p className="mb-3 text-ink-muted">Đang chạy bằng dữ liệu giả lập lưu trong trình duyệt này.</p>
            <Button icon={<RotateCcw size={14} />} onClick={() => setResetting(true)}>
              Khôi phục dữ liệu demo
            </Button>
          </Card>
        )}
      </div>

      <ConfirmDialog
        open={resetting}
        danger
        title="Khôi phục dữ liệu demo?"
        message="Mọi thay đổi bạn đã thực hiện trong bản demo trên trình duyệt này sẽ mất."
        confirmLabel="Khôi phục"
        onConfirm={() => void resetMockData()}
        onClose={() => setResetting(false)}
      />
    </>
  );
}
