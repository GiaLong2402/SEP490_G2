import { useState } from 'react';
import { Lock, LockOpen, MapPin, Pencil, Plus, Trash2 } from 'lucide-react';
import { Link, useParams } from 'react-router-dom';
import { useVariantOptions } from '@/features/products';
import { useAction } from '@/shared/api/useAction';
import type { CustomerAddress, ID } from '@/shared/domain/entities';
import { approvalStatusMeta, customerTypeMeta, orderStateMeta } from '@/shared/domain/enums';
import { date, dateTime, money } from '@/shared/lib/format';
import { Button, Card, Chip, ConfirmDialog, DataTable, ErrorState, Field, IconButton, InfoList, Input, Meter, Modal, PageHeader, PageLoading, Select, StatusChip, Tabs, type Column } from '@/shared/ui';
import { customersApi } from '../api';
import { AddressFormModal, emptyAddress, toAddressInput } from '../components/AddressFormModal';
import { CustomerFormModal } from '../components/CustomerFormModal';
import { useCustomer } from '../hooks';
import type { AddressInput, CustomerOrderRow, CustomerPriceRow } from '../types';

type Tab = 'address' | 'prices' | 'orders';

/** Phân biệt note duyệt giá đang dùng vs lý do từ chối đề xuất mới trên cùng dòng approved. */
function formatPriceDecisionNote(p: CustomerPriceRow): string {
  if (p.status === 'pending') return '—';
  const note = p.decisionNote?.trim() || '';
  if (p.lastDecision === 'rejected') {
    if (p.status === 'approved') {
      return note ? `Đề xuất gần nhất: từ chối — ${note}` : 'Đề xuất gần nhất: từ chối';
    }
    return note || 'Từ chối';
  }
  if (p.lastDecision === 'approved') return note || '—';
  return note || '—';
}

export default function CustomerDetailPage() {
  const id = Number(useParams().id);
  const { data: customer, isLoading, error, refetch } = useCustomer(id);
  const variants = useVariantOptions();
  const [tab, setTab] = useState<Tab>('address');
  const [editing, setEditing] = useState(false);
  const [addressOpen, setAddressOpen] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<ID | null>(null);
  const [addressInitial, setAddressInitial] = useState<AddressInput | null>(null);
  const [deletingAddress, setDeletingAddress] = useState<CustomerAddress | null>(null);
  const [priceOpen, setPriceOpen] = useState(false);
  const [price, setPrice] = useState({ variantId: '', unitPrice: '', effectiveFrom: '', effectiveTo: '' });
  const [priceTouched, setPriceTouched] = useState(false);
  const [lockConfirm, setLockConfirm] = useState(false);

  const closeAddressModal = () => {
    setAddressOpen(false);
    setEditingAddressId(null);
    setAddressInitial(null);
  };

  const deleteAddress = useAction(
    () => customersApi.deleteAddress(id, deletingAddress!.id),
    { success: 'Đã xóa địa chỉ', onDone: () => setDeletingAddress(null) },
  );
  const proposePrice = useAction(
    () => customersApi.proposePrice(id, { variantId: Number(price.variantId), unitPrice: Number(price.unitPrice), effectiveFrom: price.effectiveFrom || null, effectiveTo: price.effectiveTo || null }),
    { success: 'Đã gửi đề xuất giá để Chủ cửa hàng duyệt', onDone: () => setPriceOpen(false) },
  );
  const setActive = useAction(
    (active: boolean) => customersApi.setActive(id, active),
    {
      success: (detail) => (detail.isActive ? 'Đã mở khóa khách hàng' : 'Đã khóa khách hàng'),
      onDone: () => setLockConfirm(false),
    },
  );

  if (isLoading) return <PageLoading />;
  if (error || !customer) return <ErrorState error={error} onRetry={refetch} />;
  const isB2b = customer.customerType === 'b2b';
  const prices = customer.prices ?? [];
  const priceColumns: Column<CustomerPriceRow>[] = [
    { key: 'sku', header: 'SKU', cell: (p) => <span className="num">{p.sku}</span> },
    { key: 'name', header: 'Sản phẩm', cell: (p) => `${p.productName} · ${p.packLabel}` },
    { key: 'list', header: 'Niêm yết', align: 'right', cell: (p) => <span className="num text-ink-muted">{money(p.listPrice)}</span> },
    { key: 'price', header: 'Giá riêng', align: 'right', cell: (p) => <span className="num font-medium">{p.approvedAt ? money(p.unitPrice) : '—'}</span> },
    {
      key: 'pending',
      header: 'Đề xuất',
      align: 'right',
      cell: (p) =>
        p.status === 'pending' && p.pendingPrice != null ? (
          <span className="num text-amber-fg">{money(p.pendingPrice)}</span>
        ) : (
          '—'
        ),
    },
    { key: 'effective', header: 'Hiệu lực', hideOnMobile: true, cell: (p) => (p.effectiveFrom || p.effectiveTo ? `${date(p.effectiveFrom)} → ${date(p.effectiveTo)}` : 'Không giới hạn') },
    { key: 'status', header: 'Trạng thái', cell: (p) => <StatusChip meta={approvalStatusMeta} value={p.status} /> },
    {
      key: 'note',
      header: 'Ghi chú duyệt',
      hideOnMobile: true,
      cell: (p) => <span className="text-ink-muted">{formatPriceDecisionNote(p)}</span>,
    },
  ];
  const orderColumns: Column<CustomerOrderRow>[] = [
    { key: 'code', header: 'Mã đơn', cell: (o) => <Link to={`/orders/${o.id}`} className="num text-primary hover:underline">{o.code}</Link> },
    { key: 'at', header: 'Ngày đặt', cell: (o) => dateTime(o.placedAt) },
    { key: 'total', header: 'Tổng tiền', align: 'right', cell: (o) => <span className="num">{money(o.total)}</span> },
    { key: 'state', header: 'Trạng thái', cell: (o) => <StatusChip meta={orderStateMeta} value={o.state} /> },
  ];

  const tabs: { key: Tab; label: string }[] = [{ key: 'address', label: `Địa chỉ (${customer.addresses.length})` }, ...(isB2b ? [{ key: 'prices' as Tab, label: `Giá riêng (${prices.length})` }] : []), { key: 'orders', label: 'Đơn gần đây' }];
  const priceValid = Number(price.variantId) > 0 && Number(price.unitPrice) > 0;

  return (
    <>
      <PageHeader
        breadcrumb={<><Link to="/customers" className="hover:underline">Khách hàng</Link> / {customer.code}</>}
        title={<span className="flex items-center gap-2">{customer.name} <StatusChip meta={customerTypeMeta} value={customer.customerType} /> <Chip tone={customer.isActive ? 'green' : 'neutral'}>{customer.isActive ? 'Hoạt động' : 'Ngừng'}</Chip></span>}
        actions={
          <>
            {customer.isActive ? (
              <Button variant="danger" icon={<Lock size={14} />} onClick={() => setLockConfirm(true)} loading={setActive.isPending}>
                Khóa khách
              </Button>
            ) : (
              <Button icon={<LockOpen size={14} />} onClick={() => setLockConfirm(true)} loading={setActive.isPending}>
                Mở khóa
              </Button>
            )}
            <Button icon={<Pencil size={14} />} onClick={() => setEditing(true)}>Sửa</Button>
          </>
        }
      />
      <div className="grid items-start gap-4 lg:grid-cols-[320px_1fr]">
        <div className="space-y-4">
          <Card title="Liên hệ">
            <InfoList items={[['Mã', <span className="num">{customer.code}</span>], ['Điện thoại', customer.phone], ['Email', customer.email], ...(isB2b ? ([['MST', customer.taxCode], ['Người liên hệ', customer.contactName], ['SĐT liên hệ', customer.contactPhone]] as [string, string | null][]) : []), ['Trạng thái', customer.isActive ? 'Hoạt động' : 'Ngừng']]} />
            {customer.note && <p className="mt-3 border-t border-line pt-3 text-ink-muted">{customer.note}</p>}
          </Card>
          {isB2b && (
            <Card title="Công nợ" actions={<Link to="/debt" className="text-small text-primary hover:underline">Xem sổ nợ</Link>}>
              <InfoList items={[['Nợ hiện tại', <span className="num font-semibold">{money(customer.currentDebt)}</span>], ['Hạn mức', <span className="num">{money(customer.creditLimit)}</span>], ['Kỳ hạn', `NET ${customer.netDays} ngày`]]} />
              <div className="mt-3"><Meter value={customer.currentDebt} max={customer.creditLimit} label="Tỷ lệ sử dụng hạn mức" /></div>
            </Card>
          )}
        </div>
        <Card flush>
          <Tabs label="Thông tin khách hàng" tabs={tabs} value={tab} onChange={setTab} />
          {tab === 'address' && (
            <div className="p-4">
              <div className="mb-3 flex justify-end">
                <Button
                  icon={<Plus size={14} />}
                  onClick={() => {
                    setEditingAddressId(null);
                    setAddressInitial(emptyAddress);
                    setAddressOpen(true);
                  }}
                >
                  Thêm địa chỉ
                </Button>
              </div>
              {customer.addresses.length === 0 && <p className="py-6 text-center text-ink-muted">Chưa có địa chỉ giao hàng</p>}
              <ul className="grid gap-3 md:grid-cols-2">
                {customer.addresses.map((a) => (
                  <li key={a.id} className="rounded-md border border-line p-3">
                    <div className="flex items-start gap-2">
                      <MapPin size={15} className="mt-0.5 shrink-0 text-ink-faint" aria-hidden />
                      <div className="min-w-0 flex-1">
                        <div className="font-medium">{a.line1}</div>
                        <div className="text-ink-muted">{[a.ward, a.district, a.province].filter(Boolean).join(', ')}</div>
                        <div className="mt-0.5 text-small text-ink-faint">{a.recipientName} · {a.phone}</div>
                        <div className="mt-2 flex flex-wrap gap-1.5">
                          {a.isDefault && <Chip tone="blue">Mặc định</Chip>}
                          <Chip tone={a.inDeliveryRadius ? 'green' : 'amber'}>{a.inDeliveryRadius ? 'Trong bán kính' : 'Ngoài bán kính'}{a.distanceKm !== null ? ` · ${a.distanceKm} km` : ''}</Chip>
                        </div>
                      </div>
                      <div className="flex shrink-0 gap-1">
                        <IconButton
                          label={`Sửa địa chỉ ${a.line1}`}
                          onClick={() => {
                            setEditingAddressId(a.id);
                            setAddressInitial(toAddressInput(a));
                            setAddressOpen(true);
                          }}
                        >
                          <Pencil size={14} />
                        </IconButton>
                        <IconButton label={`Xóa địa chỉ ${a.line1}`} onClick={() => setDeletingAddress(a)}>
                          <Trash2 size={14} />
                        </IconButton>
                      </div>
                    </div>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {tab === 'prices' && (
            <>
              <div className="flex justify-end p-3">
                <Button icon={<Plus size={14} />} onClick={() => { setPrice({ variantId: '', unitPrice: '', effectiveFrom: '', effectiveTo: '' }); setPriceTouched(false); setPriceOpen(true); }}>Đề xuất giá riêng</Button>
              </div>
              <DataTable columns={priceColumns} rows={prices} rowKey={(p) => p.id} emptyText="Khách chưa có giá riêng" />
            </>
          )}
          {tab === 'orders' && <DataTable columns={orderColumns} rows={customer.recentOrders} rowKey={(o) => o.id} emptyText="Khách chưa có đơn hàng" />}
        </Card>
      </div>

      <CustomerFormModal open={editing} customer={customer} onClose={() => setEditing(false)} />

      <AddressFormModal
        open={addressOpen}
        customerId={id}
        addressId={editingAddressId}
        initial={addressInitial}
        onClose={closeAddressModal}
      />

      <ConfirmDialog
        open={!!deletingAddress}
        danger
        title="Xóa địa chỉ?"
        message={deletingAddress ? `Xóa địa chỉ “${deletingAddress.line1}”? Thao tác này không thể hoàn tác.` : ''}
        confirmLabel="Xóa địa chỉ"
        loading={deleteAddress.isPending}
        onConfirm={() => deleteAddress.mutate()}
        onClose={() => setDeletingAddress(null)}
      />

      <Modal
        open={priceOpen}
        title="Đề xuất giá riêng"
        onClose={() => setPriceOpen(false)}
        onSubmit={() => {
          setPriceTouched(true);
          if (priceValid) proposePrice.mutate();
        }}
        footer={<><Button onClick={() => setPriceOpen(false)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={proposePrice.isPending}>Gửi duyệt</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Quy cách" required className="sm:col-span-2" error={priceTouched && !Number(price.variantId) ? 'Vui lòng chọn quy cách' : undefined}>
            <Select value={price.variantId} onChange={(e) => setPrice({ ...price, variantId: e.target.value })}>
              <option value="">Chọn...</option>
              {variants.data?.map((v) => <option key={v.id} value={v.id}>{v.productName} · {v.packLabel} — niêm yết {money(v.listPrice)}</option>)}
            </Select>
          </Field>
          <Field label="Giá đề xuất / khay" required className="sm:col-span-2" error={priceTouched && !(Number(price.unitPrice) > 0) ? 'Phải lớn hơn 0' : undefined} hint="Giá hiện tại vẫn áp dụng cho đến khi được duyệt"><Input type="number" min={0} value={price.unitPrice} onChange={(e) => setPrice({ ...price, unitPrice: e.target.value })} /></Field>
          <Field label="Hiệu lực từ"><Input type="date" value={price.effectiveFrom} onChange={(e) => setPrice({ ...price, effectiveFrom: e.target.value })} /></Field>
          <Field label="Đến ngày"><Input type="date" value={price.effectiveTo} onChange={(e) => setPrice({ ...price, effectiveTo: e.target.value })} /></Field>
        </div>
      </Modal>

      <ConfirmDialog
        open={lockConfirm}
        danger={customer.isActive}
        title={customer.isActive ? 'Khóa khách hàng?' : 'Mở khóa khách hàng?'}
        message={
          customer.isActive
            ? `Khóa “${customer.name}”? Khách đã khóa không thể tạo đơn mới cho đến khi được mở lại.`
            : `Mở khóa “${customer.name}”? Khách sẽ lại được phép tạo đơn mới.`
        }
        confirmLabel={customer.isActive ? 'Khóa khách' : 'Mở khóa'}
        loading={setActive.isPending}
        onConfirm={() => setActive.mutate(!customer.isActive)}
        onClose={() => setLockConfirm(false)}
      />
    </>
  );
}
