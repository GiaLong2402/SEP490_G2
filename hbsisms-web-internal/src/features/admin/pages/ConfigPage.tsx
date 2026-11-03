import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { RotateCcw } from 'lucide-react';
import { isMockMode } from '@/shared/api/config';
import { useAction } from '@/shared/api/useAction';
import { resetMockData } from '../resetMockData';
import { dateTime } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, ErrorState, Field, Input, PageHeader, PageLoading } from '@/shared/ui';
import { adminApi } from '../api';
import type { AppConfig } from '../types';

function ConfigRow({ row }: { row: AppConfig }) {
  const [value, setValue] = useState(row.value ?? '');
  useEffect(() => setValue(row.value ?? ''), [row.value]);
  const save = useAction(() => adminApi.updateConfig(row.key, value), { success: `Đã lưu ${row.key}` });
  const invalid = row.valueType === 'int' && !/^\d+$/.test(value);
  return (
    <form
      className="flex flex-wrap items-end gap-3 border-t border-line px-4 py-3 first:border-t-0"
      onSubmit={(event) => {
        event.preventDefault();
        if (!invalid) save.mutate();
      }}
    >
      <Field label={row.description ?? row.key} hint={`${row.key} · cập nhật ${dateTime(row.updatedAt)}`} error={invalid ? 'Phải là số nguyên không âm' : undefined} className="min-w-[260px] flex-1">
        <Input type={row.valueType === 'int' ? 'number' : 'text'} min={0} value={value} onChange={(e) => setValue(e.target.value)} />
      </Field>
      <Button type="submit" disabled={value === (row.value ?? '') || invalid} loading={save.isPending} className="mb-5">Lưu</Button>
    </form>
  );
}

export default function ConfigPage() {
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['admin-config'], queryFn: adminApi.config });
  const [resetting, setResetting] = useState(false);
  if (isLoading) return <PageLoading />;
  if (error || !data) return <ErrorState error={error} onRetry={refetch} />;

  const settings = data.filter((c) => c.valueType !== 'json');
  const integrations = data.filter((c) => c.valueType === 'json').map((c) => ({ key: c.key, description: c.description, ...(JSON.parse(c.value ?? '{}') as { status?: string; name?: string }) }));

  return (
    <>
      <PageHeader title="Cấu hình hệ thống" subtitle="Ngưỡng cảnh báo, kỳ hạn công nợ và tích hợp" />
      <div className="grid items-start gap-4 lg:grid-cols-[1fr_340px]">
        <Card title="Tham số" flush>{settings.map((row) => <ConfigRow key={row.key} row={row} />)}</Card>
        <div className="space-y-4">
          <Card title="Tích hợp">
            <ul className="space-y-3">
              {integrations.map((item) => (
                <li key={item.key} className="flex items-start justify-between gap-3">
                  <div><div className="font-medium">{item.name}</div><div className="text-small text-ink-faint">{item.description}</div></div>
                  <Chip tone={item.status === 'connected' ? 'green' : 'neutral'}>{item.status === 'connected' ? 'Đã kết nối' : 'Sắp có'}</Chip>
                </li>
              ))}
            </ul>
          </Card>
          {isMockMode && (
            <Card title="Dữ liệu demo">
              <p className="mb-3 text-ink-muted">Đang chạy bằng dữ liệu giả lập lưu trong trình duyệt này.</p>
              <Button icon={<RotateCcw size={14} />} onClick={() => setResetting(true)}>Khôi phục dữ liệu demo</Button>
            </Card>
          )}
        </div>
      </div>
      <ConfirmDialog open={resetting} danger title="Khôi phục dữ liệu demo?" message="Mọi thay đổi bạn đã thực hiện trong bản demo trên trình duyệt này sẽ mất." confirmLabel="Khôi phục" onConfirm={() => void resetMockData()} onClose={() => setResetting(false)} />
    </>
  );
}
