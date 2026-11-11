import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { customersApi, useCustomer, useCustomerOptions } from '@/features/customers';
import { LineItemsEditor, isCompleteLine, newLine, toPricingLines, usePricing, type EditableLine } from '@/features/pricing';
import { useVariantOptions } from '@/features/products';
import { useAction } from '@/shared/api/useAction';
import { fulfillmentLabels, paymentMethodLabels, saleChannelLabels, type FulfillmentMethod, type PaymentMethod, type SaleChannel } from '@/shared/domain/enums';
import { money } from '@/shared/lib/format';
import { Alert, Button, Card, Chip, Field, Input, Meter, PageHeader, Select, Textarea, enumOptions } from '@/shared/ui';
import { ordersApi } from '../api';
import { useOrder } from '../hooks';

export default function OrderFormPage() {
  const navigate = useNavigate();
  const params = useParams();
  const editId = params.id ? Number(params.id) : null;
  const isEdit = editId != null && Number.isFinite(editId);
  const existing = useOrder(editId ?? 0, isEdit);
  const customers = useCustomerOptions();
  const variants = useVariantOptions();
  const [customerId, setCustomerId] = useState<number | null>(null);
  const customer = useCustomer(customerId).data;
  const addresses = useQuery({ queryKey: ['addresses', customerId], queryFn: () => customersApi.addresses(customerId!), enabled: !!customerId }).data;

  const [channel, setChannel] = useState<SaleChannel>('phone');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('cash');
  const [fulfillment, setFulfillment] = useState<FulfillmentMethod>('shop_delivery');
  const [addressId, setAddressId] = useState<number | null>(null);
  const [shippingFee, setShippingFee] = useState('0');
  const [discount, setDiscount] = useState('0');
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<EditableLine[]>([newLine()]);
  const [touched, setTouched] = useState(false);
  const [hydrated, setHydrated] = useState(!isEdit);

  const isB2b = customer?.customerType === 'b2b';

  useEffect(() => {
    if (!isEdit || !existing.data || hydrated) return;
    const o = existing.data;
    if (!['pending', 'confirmed'].includes(o.state)) {
      navigate(`/orders/${o.id}`, { replace: true });
      return;
    }
    setCustomerId(o.customerId);
    setChannel(o.channel ?? 'phone');
    setPaymentMethod(o.paymentMethod);
    setFulfillment(o.fulfillmentMethod);
    setAddressId(o.shipAddressId);
    setShippingFee(String(o.shippingFee ?? 0));
    setDiscount(String(o.discountTotal ?? 0));
    setNote(o.note ?? '');
    setLines(o.lines.length
      ? o.lines.map((l) => ({ ...newLine(l.variantId, String(l.qty)), unit: l.unit }))
      : [newLine()]);
    setHydrated(true);
  }, [isEdit, existing.data, hydrated, navigate]);

  // Đổi khách: chọn địa chỉ mặc định và phương thức thanh toán hợp lệ (chỉ khi tạo mới / đổi tay).
  useEffect(() => {
    if (!customer || (isEdit && !hydrated)) return;
    if (!isEdit) setPaymentMethod(customer.customerType === 'b2b' ? 'credit_net' : 'cash');
  }, [customer?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (isEdit && !hydrated) return;
    if (isEdit && addressId != null) return;
    setAddressId(addresses?.find((a) => a.isDefault)?.id ?? addresses?.[0]?.id ?? null);
  }, [addresses, isEdit, hydrated, addressId]);

  const selectedAddress = addresses?.find((a) => a.id === addressId);
  const pricingLines = useMemo(() => toPricingLines(lines), [lines]);
  const pricing = usePricing({ customerId, paymentMethod, shippingFee: Number(shippingFee) || 0, discountTotal: Number(discount) || 0, lines: toPricingLines(lines.filter(isCompleteLine)) });
  const linesValid = lines.every(isCompleteLine);
  const needsAddress = fulfillment !== 'pickup';
  const shortage = pricing.data?.lines.some((l) => l.shortfall > 0);

  const body = () => ({
    customerId: customerId!,
    orderType: (isB2b ? 'b2b' : 'retail') as 'b2b' | 'retail',
    channel,
    paymentMethod,
    fulfillmentMethod: fulfillment,
    shipAddressId: needsAddress ? addressId : null,
    shippingFee: Number(shippingFee) || 0,
    discountTotal: Number(discount) || 0,
    voucherCode: null,
    note: note.trim() || null,
    lines: pricingLines,
  });

  const create = useAction(
    () => ordersApi.create(body()),
    { success: (o) => `Đã tạo đơn ${o.code}`, onDone: (o) => navigate(`/orders/${o.id}`) },
  );
  const update = useAction(
    () => ordersApi.update(editId!, body()),
    { success: (o) => `Đã cập nhật đơn ${o.code}`, onDone: (o) => navigate(`/orders/${o.id}`) },
  );

  const submit = () => {
    setTouched(true);
    if (customerId && linesValid && (!needsAddress || addressId)) {
      if (isEdit) update.mutate();
      else create.mutate();
    }
  };

  const saving = create.isPending || update.isPending;
  const title = isEdit ? 'Sửa đơn hàng' : 'Tạo đơn hàng';
  const crumb = isEdit
    ? <><Link to="/orders" className="hover:underline">Đơn hàng</Link> / <Link to={`/orders/${editId}`} className="hover:underline">{existing.data?.code ?? `#${editId}`}</Link> / Sửa</>
    : <><Link to="/orders" className="hover:underline">Đơn hàng</Link> / Tạo đơn</>;

  if (isEdit && existing.isLoading) return null;

  return (
    <>
      <PageHeader breadcrumb={crumb} title={title} />
      <div className="grid items-start gap-4 min-[1500px]:grid-cols-[1fr_320px]">
        <div className="min-w-0 space-y-4">
          <Card title="Khách hàng và giao nhận">
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              <Field label="Khách hàng" required error={touched && !customerId ? 'Vui lòng chọn khách' : undefined} className="md:col-span-2 xl:col-span-1">
                <Select value={customerId ?? ''} onChange={(e) => setCustomerId(e.target.value ? Number(e.target.value) : null)}>
                  <option value="">Chọn khách hàng...</option>
                  {customers.data?.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
                </Select>
              </Field>
              <Field label="Kênh bán" required><Select value={channel} onChange={(e) => setChannel(e.target.value as SaleChannel)}>{enumOptions(saleChannelLabels)}</Select></Field>
              <Field label="Hình thức giao" required><Select value={fulfillment} onChange={(e) => setFulfillment(e.target.value as FulfillmentMethod)}>{enumOptions(fulfillmentLabels)}</Select></Field>
              {needsAddress && (
                <Field label="Địa chỉ giao" required className="md:col-span-2 xl:col-span-3" error={touched && customerId && !addressId ? 'Khách chưa có địa chỉ, hãy thêm ở hồ sơ khách hoặc chọn "Khách đến lấy"' : undefined}>
                  <Select disabled={!customerId} value={addressId ?? ''} onChange={(e) => setAddressId(Number(e.target.value))}>
                    {!addresses?.length && <option value="">{customerId ? 'Khách chưa có địa chỉ' : 'Chọn khách trước'}</option>}
                    {addresses?.map((a) => <option key={a.id} value={a.id}>{[a.line1, a.ward, a.district].filter(Boolean).join(', ')}</option>)}
                  </Select>
                </Field>
              )}
            </div>
            {needsAddress && selectedAddress && (
              <div className="mt-2 flex items-center gap-2">
                <Chip tone={selectedAddress.inDeliveryRadius ? 'green' : 'amber'}>{selectedAddress.inDeliveryRadius ? 'Trong bán kính tự giao' : 'Ngoài bán kính, nên dùng đối tác giao'}</Chip>
                {selectedAddress.distanceKm !== null && <span className="text-small text-ink-muted">{selectedAddress.distanceKm} km</span>}
              </div>
            )}
          </Card>
          <Card title="Dòng hàng" flush>
            <LineItemsEditor lines={lines} onChange={setLines} variants={variants.data ?? []} priced={pricing.data?.lines} />
            {touched && !linesValid && <p className="px-3 pb-3 text-small text-red-fg">Mỗi dòng cần chọn sản phẩm và số lượng là số nguyên dương.</p>}
          </Card>
        </div>

        <aside className="grid items-start gap-4 md:grid-cols-2 min-[1500px]:sticky min-[1500px]:top-0 min-[1500px]:grid-cols-1">
          {isB2b && customer && (
            <Card title="Công nợ khách">
              <div className="mb-2 flex justify-between text-small"><span className="text-ink-muted">Nợ / hạn mức</span><span className="num">{money(customer.currentDebt)} / {money(customer.creditLimit)}</span></div>
              <Meter value={customer.currentDebt} max={customer.creditLimit} label="Tỷ lệ sử dụng hạn mức" />
              <p className="mt-2 text-small text-ink-muted">Kỳ hạn NET {customer.netDays} ngày</p>
            </Card>
          )}
          <Card title="Tóm tắt đơn">
            <div className="space-y-3">
              <Field label="Thanh toán" required>
                <Select value={paymentMethod} onChange={(e) => setPaymentMethod(e.target.value as PaymentMethod)}>
                  {Object.entries(paymentMethodLabels).filter(([key]) => key !== 'credit_net' || isB2b).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
                </Select>
              </Field>
              <div className="grid grid-cols-2 gap-2">
                <Field label="Chiết khấu (₫)"><Input type="number" min={0} className="text-right" value={discount} onChange={(e) => setDiscount(e.target.value)} /></Field>
                <Field label="Phí giao (₫)"><Input type="number" min={0} className="text-right" value={shippingFee} onChange={(e) => setShippingFee(e.target.value)} /></Field>
              </div>
              <Field label="Ghi chú"><Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
              <dl className="space-y-1.5 border-t border-line pt-3">
                <div className="flex justify-between"><dt className="text-ink-muted">Tạm tính</dt><dd className="num">{money(pricing.data?.subtotal ?? 0)}</dd></div>
                <div className="flex justify-between text-h2"><dt>Tổng cộng</dt><dd className="tabular-nums">{money(pricing.data?.total ?? 0)}</dd></div>
              </dl>
              {pricing.data?.isOverLimit && <Alert tone="danger" title={`Vượt hạn mức ${money(pricing.data.overLimitAmount)}`}>Đơn sẽ chuyển Chủ cửa hàng duyệt, chưa giữ hàng cho đến khi được duyệt.</Alert>}
              {shortage && <Alert tone="warning" title="Không đủ hàng khả dụng">Đơn vẫn lưu được; phần thiếu ở trạng thái chờ hàng về.</Alert>}
              {pricing.error && <Alert tone="danger">{pricing.error.message}</Alert>}
              <Button variant="primary" className="w-full" loading={saving} onClick={submit}>
                {isEdit ? 'Lưu thay đổi' : pricing.data?.isOverLimit ? 'Tạo đơn và gửi duyệt' : 'Tạo đơn'}
              </Button>
            </div>
          </Card>
        </aside>
      </div>
    </>
  );
}
