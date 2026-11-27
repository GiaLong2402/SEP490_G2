import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { CalendarPlus, Lock, RotateCcw, Trash2 } from 'lucide-react';
import { useAuth } from '@/features/auth';
import { useAction } from '@/shared/api/useAction';
import { dateTime, money, num } from '@/shared/lib/format';
import {
  Button,
  Card,
  Chip,
  ConfirmDialog,
  DataTable,
  Drawer,
  Field,
  FilterBar,
  FilterSelect,
  Input,
  Modal,
  PageHeader,
  StatCard,
  StatGrid,
  Textarea,
  type Column,
} from '@/shared/ui';
import { costingApi } from '../api';
import type { CostItemBalance, CostItemType, CostPeriod, CostPeriodStatus } from '../types';

const statusMeta: Record<CostPeriodStatus, { label: string; tone: 'green' | 'amber' }> = {
  OPEN: { label: 'Đang mở', tone: 'amber' },
  CLOSED: { label: 'Đã khóa', tone: 'green' },
};

const itemTypeLabel: Record<CostItemType, string> = {
  RAW_MATERIAL: 'Nguyên liệu',
  FINISHED: 'Thành phẩm',
};

function currentYearMonth() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

export default function CostingPage() {
  const { can } = useAuth();
  const canOpen = can('admin') || can('approve');
  const periodsQ = useQuery({ queryKey: ['cost-periods'], queryFn: costingApi.list });
  const openPeriod = periodsQ.data?.find((p) => p.status === 'OPEN') ?? null;
  const latestYm = periodsQ.data?.[0]?.yearMonth ?? null;

  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [itemType, setItemType] = useState<'' | CostItemType>('');
  const [openModal, setOpenModal] = useState(false);
  const [yearMonth, setYearMonth] = useState(currentYearMonth);
  const [closingId, setClosingId] = useState<number | null>(null);
  const [reopening, setReopening] = useState<CostPeriod | null>(null);
  const [reopenReason, setReopenReason] = useState('');
  const [discarding, setDiscarding] = useState<CostPeriod | null>(null);
  const [discardReason, setDiscardReason] = useState('');

  const selected = periodsQ.data?.find((p) => p.id === selectedId) ?? null;
  const closingPeriod = periodsQ.data?.find((p) => p.id === closingId) ?? null;
  const balancesQ = useQuery({
    queryKey: ['cost-period-balances', selectedId, itemType || null],
    queryFn: () => costingApi.balances(selectedId!, itemType || undefined),
    enabled: selectedId != null,
  });

  const open = useAction((ym: string) => costingApi.openPeriod(ym), {
    success: (p) => `Đã mở kỳ ${p.yearMonth}`,
    onDone: (p) => {
      setOpenModal(false);
      setSelectedId(p.id);
    },
  });

  const close = useAction((id: number) => costingApi.closePeriod(id), {
    success: (p) => `Đã khóa sổ kỳ ${p.yearMonth}`,
    onDone: () => setClosingId(null),
  });

  const reopen = useAction(
    ({ id, reason }: { id: number; reason: string }) => costingApi.reopenPeriod(id, reason),
    {
      success: (p) => `Đã mở lại kỳ ${p.yearMonth}`,
      onDone: (p) => {
        setReopening(null);
        setReopenReason('');
        setSelectedId(p.id);
      },
    },
  );

  const discard = useAction(
    ({ id, reason }: { id: number; reason: string }) => costingApi.discardOpenPeriod(id, reason),
    {
      success: () => 'Đã hủy kỳ mở nhầm',
      onDone: () => {
        const discardedId = discarding?.id;
        setDiscarding(null);
        setDiscardReason('');
        if (selectedId === discardedId) setSelectedId(null);
      },
    },
  );

  const totals = useMemo(() => {
    const rows = balancesQ.data ?? [];
    return {
      openingValue: rows.reduce((s, r) => s + Number(r.openingValue), 0),
      inboundValue: rows.reduce((s, r) => s + Number(r.inboundValue), 0),
      outboundValue: rows.reduce((s, r) => s + Number(r.outboundValue), 0),
      closingValue: rows.reduce((s, r) => s + Number(r.closingValue), 0),
    };
  }, [balancesQ.data]);

  const canReopenSelected =
    canOpen && selected?.status === 'CLOSED' && !openPeriod && selected.yearMonth === latestYm;
  const canDiscardSelected =
    canOpen && selected?.status === 'OPEN' && selected.yearMonth === latestYm;

  const periodColumns: Column<CostPeriod>[] = [
    {
      key: 'ym',
      header: 'Kỳ',
      cell: (p) => (
        <button type="button" className="font-medium text-primary hover:underline" onClick={() => setSelectedId(p.id)}>
          {p.yearMonth}
        </button>
      ),
    },
    {
      key: 'status',
      header: 'Trạng thái',
      cell: (p) => <Chip tone={statusMeta[p.status].tone}>{statusMeta[p.status].label}</Chip>,
    },
    {
      key: 'items',
      header: 'Mặt hàng',
      align: 'right',
      cell: (p) => <span className="num">{num(p.balanceCount)}</span>,
    },
    { key: 'opened', header: 'Mở lúc', hideOnMobile: true, cell: (p) => dateTime(p.openedAt) },
    { key: 'closed', header: 'Khóa lúc', hideOnMobile: true, cell: (p) => dateTime(p.closedAt) },
  ];

  const balanceColumns: Column<CostItemBalance>[] = [
    {
      key: 'type',
      header: 'Loại',
      cell: (r) => <Chip tone={r.itemType === 'RAW_MATERIAL' ? 'blue' : 'neutral'}>{itemTypeLabel[r.itemType]}</Chip>,
    },
    {
      key: 'item',
      header: 'Mặt hàng',
      cell: (r) => (
        <div>
          <div className="font-medium">{r.itemName}</div>
          <div className="num text-small text-ink-faint">{r.itemCode}</div>
        </div>
      ),
    },
    { key: 'oq', header: 'Tồn đầu', align: 'right', cell: (r) => <span className="num">{num(r.openingQty)}</span> },
    { key: 'ov', header: 'Giá trị đầu', align: 'right', hideOnMobile: true, cell: (r) => <span className="num">{money(r.openingValue)}</span> },
    { key: 'iq', header: 'Nhập', align: 'right', hideOnMobile: true, cell: (r) => <span className="num">{num(r.inboundQty)}</span> },
    { key: 'xq', header: 'Xuất', align: 'right', hideOnMobile: true, cell: (r) => <span className="num">{num(r.outboundQty)}</span> },
    { key: 'cq', header: 'Tồn cuối', align: 'right', cell: (r) => <span className="num">{num(r.closingQty)}</span> },
    { key: 'cv', header: 'Giá trị cuối', align: 'right', cell: (r) => <span className="num">{money(r.closingValue)}</span> },
    {
      key: 'avg',
      header: 'ĐG bình quân',
      align: 'right',
      cell: (r) => <span className="num">{r.avgUnitCost == null ? '—' : money(r.avgUnitCost)}</span>,
    },
  ];

  return (
    <>
      <PageHeader
        title="Giá vốn theo kỳ"
        subtitle="Báo cáo giá vốn theo tháng. Đơn giá vốn tồn kho cập nhật mỗi nhập/xuất (xem mục Giá vốn tồn kho)."
        actions={
          canOpen ? (
            <div className="flex flex-wrap gap-2">
              {openPeriod && (
                <>
                  <Button variant="primary" icon={<Lock size={15} />} onClick={() => setClosingId(openPeriod.id)}>
                    Khóa sổ
                  </Button>
                  {openPeriod.yearMonth === latestYm && (
                    <Button
                      variant="danger"
                      icon={<Trash2 size={15} />}
                      onClick={() => {
                        setDiscardReason('');
                        setDiscarding(openPeriod);
                      }}
                    >
                      Hủy kỳ mở nhầm
                    </Button>
                  )}
                </>
              )}
              <Button
                icon={<CalendarPlus size={15} />}
                disabled={!!openPeriod}
                title={openPeriod ? `Đang mở kỳ ${openPeriod.yearMonth} — khóa sổ trước` : undefined}
                onClick={() => {
                  setYearMonth(currentYearMonth());
                  setOpenModal(true);
                }}
              >
                Mở kỳ
              </Button>
            </div>
          ) : undefined
        }
      />

      {openPeriod && (
        <StatGrid cols={2}>
          <StatCard label="Kỳ đang mở" value={openPeriod.yearMonth} hint={`${num(openPeriod.balanceCount)} mặt hàng`} />
          <StatCard label="Mở lúc" value={dateTime(openPeriod.openedAt)} />
        </StatGrid>
      )}

      <Card flush>
        <DataTable
          columns={periodColumns}
          rows={periodsQ.data}
          rowKey={(p) => p.id}
          loading={periodsQ.isLoading}
          error={periodsQ.error}
          onRetry={periodsQ.refetch}
          emptyText="Chưa có kỳ giá vốn — Chủ/Admin bấm Mở kỳ để bắt đầu"
        />
      </Card>

      <Drawer
        open={selectedId != null}
        title={selected ? `Số dư kỳ ${selected.yearMonth}` : 'Số dư kỳ'}
        onClose={() => setSelectedId(null)}
        width={920}
      >
        {selected && (
          <div className="space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex flex-wrap items-center gap-2">
                <Chip tone={statusMeta[selected.status].tone}>{statusMeta[selected.status].label}</Chip>
                <span className="text-small text-ink-muted">Mở {dateTime(selected.openedAt)}</span>
                {selected.closedAt && (
                  <span className="text-small text-ink-muted">· Khóa {dateTime(selected.closedAt)}</span>
                )}
                {selected.reopenReason && (
                  <span className="text-small text-ink-muted">· Lý do mở lại: {selected.reopenReason}</span>
                )}
              </div>
              <div className="flex flex-wrap gap-2">
                {canOpen && selected.status === 'OPEN' && (
                  <Button icon={<Lock size={14} />} onClick={() => setClosingId(selected.id)}>
                    Khóa sổ
                  </Button>
                )}
                {canDiscardSelected && (
                  <Button
                    variant="danger"
                    icon={<Trash2 size={14} />}
                    onClick={() => {
                      setDiscardReason('');
                      setDiscarding(selected);
                    }}
                  >
                    Hủy kỳ
                  </Button>
                )}
                {canReopenSelected && (
                  <Button
                    icon={<RotateCcw size={14} />}
                    onClick={() => {
                      setReopenReason('');
                      setReopening(selected);
                    }}
                  >
                    Mở lại kỳ
                  </Button>
                )}
              </div>
            </div>
            <StatGrid cols={4}>
              <StatCard label="Giá trị đầu" value={money(totals.openingValue)} />
              <StatCard label="Nhập trong kỳ" value={money(totals.inboundValue)} />
              <StatCard label="Xuất trong kỳ" value={money(totals.outboundValue)} />
              <StatCard label="Giá trị cuối" value={money(totals.closingValue)} />
            </StatGrid>
            <Card flush>
              <FilterBar>
                <FilterSelect label="Loại" value={itemType} onChange={(v) => setItemType((v as CostItemType) || '')}>
                  <option value="">Tất cả</option>
                  <option value="RAW_MATERIAL">Nguyên liệu</option>
                  <option value="FINISHED">Thành phẩm</option>
                </FilterSelect>
              </FilterBar>
              <DataTable
                columns={balanceColumns}
                rows={balancesQ.data}
                rowKey={(r) => r.id}
                loading={balancesQ.isLoading}
                error={balancesQ.error}
                onRetry={balancesQ.refetch}
                emptyText="Kỳ này chưa có số dư mặt hàng"
              />
            </Card>
          </div>
        )}
      </Drawer>

      <Modal
        open={openModal}
        title="Mở kỳ giá vốn"
        onClose={() => setOpenModal(false)}
        onSubmit={() => open.mutate(yearMonth)}
        footer={
          <>
            <Button onClick={() => setOpenModal(false)}>Hủy</Button>
            <Button type="submit" variant="primary" loading={open.isPending}>
              Mở kỳ
            </Button>
          </>
        }
      >
        <Field
          label="Tháng"
          required
          hint="Tồn đầu ưu tiên từ giá vốn tồn kho hiện hành; không có thì kỳ trước đã khóa hoặc snapshot kho."
        >
          <Input type="month" value={yearMonth} onChange={(e) => setYearMonth(e.target.value)} required />
        </Field>
      </Modal>

      <Modal
        open={reopening != null}
        title={reopening ? `Mở lại kỳ ${reopening.yearMonth}` : 'Mở lại kỳ'}
        onClose={() => {
          setReopening(null);
          setReopenReason('');
        }}
        onSubmit={() => reopening && reopen.mutate({ id: reopening.id, reason: reopenReason.trim() })}
        footer={
          <>
            <Button onClick={() => { setReopening(null); setReopenReason(''); }}>Hủy</Button>
            <Button type="submit" variant="primary" loading={reopen.isPending} disabled={reopenReason.trim().length < 3}>
              Mở lại
            </Button>
          </>
        }
      >
        <Field label="Lý do mở lại" required hint="Bắt buộc theo task 58 — ví dụ: khóa nhầm, còn phiếu tháng này chưa ghi.">
          <Textarea
            value={reopenReason}
            onChange={(e) => setReopenReason(e.target.value)}
            placeholder="VD: Khóa nhầm — còn phiếu nhập NVL ngày 27/11"
            maxLength={500}
            required
          />
        </Field>
      </Modal>

      <Modal
        open={discarding != null}
        title={discarding ? `Hủy kỳ ${discarding.yearMonth}` : 'Hủy kỳ'}
        onClose={() => {
          setDiscarding(null);
          setDiscardReason('');
        }}
        onSubmit={() => discarding && discard.mutate({ id: discarding.id, reason: discardReason.trim() })}
        footer={
          <>
            <Button onClick={() => { setDiscarding(null); setDiscardReason(''); }}>Đóng</Button>
            <Button type="submit" variant="danger" loading={discard.isPending} disabled={discardReason.trim().length < 3}>
              Hủy kỳ
            </Button>
          </>
        }
      >
        <p className="mb-3 text-small text-ink-muted">
          Xóa kỳ đang mở và số dư mirror của kỳ đó (giá vốn tồn kho vẫn giữ). Dùng khi mở nhầm tháng sau trong khi vẫn làm việc tháng trước.
          Phiếu kho không bị xóa.
        </p>
        <Field label="Lý do hủy" required>
          <Textarea
            value={discardReason}
            onChange={(e) => setDiscardReason(e.target.value)}
            placeholder="VD: Mở 2026-12 sớm — vẫn đang demo tháng 11"
            maxLength={500}
            required
          />
        </Field>
      </Modal>

      <ConfirmDialog
        open={closingId != null}
        title="Khóa sổ cuối kỳ"
        message={
          closingPeriod
            ? `Khóa kỳ ${closingPeriod.yearMonth}? Hệ thống tính đơn giá bình quân chính thức (NVL trước, rồi thành phẩm), chốt giá trị xuất / tồn cuối. Sau khi khóa không sửa phiếu của kỳ này.`
            : 'Khóa kỳ đang mở?'
        }
        confirmLabel="Khóa sổ"
        loading={close.isPending}
        onConfirm={() => closingId != null && close.mutate(closingId)}
        onClose={() => setClosingId(null)}
      />
    </>
  );
}
