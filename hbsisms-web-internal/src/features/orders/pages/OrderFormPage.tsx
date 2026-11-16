import { useEffect, useMemo, useRef, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { AddressFormModal, CustomerSearchField, customersApi, emptyAddress, useCustomer, type AddressInput } from '@/features/customers';
import { normalizeVnPhone, validateRequiredPhone } from '@/features/customers/customerValidation';
import { LineItemsEditor, isCompleteLine, newLine, toPricingLines, type EditableLine, usePricing } from '@/features/pricing';
import { useVariantOptions } from '@/features/products';
import { useAction } from '@/shared/api/useAction';
import { geoApi } from '@/shared/api/geo';
import { fulfillmentLabels, paymentMethodLabels, saleChannelLabels, staffSaleChannelLabels, staffSaleChannels, type FulfillmentMethod, type PaymentMethod, type SaleChannel } from '@/shared/domain/enums';
import { normalizePaymentForFulfillment } from '../cod';
import { AdminUnitFields } from '@/shared/geo/AdminUnitFields';
import { applyReverseAddress } from '@/shared/geo/applyReverseAddress';
import { formatThousands, money, parseThousands } from '@/shared/lib/format';
import { GoongMapPicker } from '@/shared/maps/GoongMapPicker';
import type { LngLat } from '@/shared/maps/goong';
import { Alert, Button, Card, Chip, Field, Input, PageHeader, Select, Textarea, enumOptions } from '@/shared/ui';
import { ordersApi } from '../api';
import { useOrder } from '../hooks';
import type { ShipAddressInput } from '../types';

const GEOCODE_DEBOUNCE_MS = 700;
const REVERSE_DEBOUNCE_MS = 450;
const FALLBACK_SHOP: LngLat = { lat: 20.951248, lng: 107.080502 };
const DEFAULT_RADIUS_KM = 8;

/** Không bán nợ — chỉ tiền mặt / chuyển khoản / COD trên form Sales.
 *  PayOS (cổng QR web) bản chất cũng là CK → gộp vào bank_transfer, không hiện riêng. */
const SALES_PAYMENT_OPTIONS = Object.entries(paymentMethodLabels).filter(
  ([key]) => key !== 'credit_net' && key !== 'payos',
);

function toSalesPaymentMethod(method: PaymentMethod): PaymentMethod {
  if (method === 'credit_net') return 'cash';
  if (method === 'payos') return 'bank_transfer';
  return method;
}

const emptyWalkInAddress = (): ShipAddressInput => ({
  recipientName: '',
  phone: '',
  line1: '',
  ward: '',
  district: '',
  province: '',
  latitude: null,
  longitude: null,
  distanceKm: null,
});

export default function OrderFormPage() {
  const navigate = useNavigate();
  const params = useParams();
  const editId = params.id ? Number(params.id) : null;
  const isEdit = editId != null && Number.isFinite(editId);
  const existing = useOrder(editId ?? 0, isEdit);
  const variants = useVariantOptions();
  const [walkIn, setWalkIn] = useState(false);
  const [customerId, setCustomerId] = useState<number | null>(null);
  const [walkInName, setWalkInName] = useState('');
  const [walkInPhone, setWalkInPhone] = useState('');
  const [walkInAddress, setWalkInAddress] = useState<ShipAddressInput>(emptyWalkInAddress);
  const customer = useCustomer(customerId).data;
  const addresses = useQuery({ queryKey: ['addresses', customerId], queryFn: () => customersApi.addresses(customerId!), enabled: !!customerId }).data;

  const [channel, setChannel] = useState<SaleChannel>('phone');
  const [paymentMethod, setPaymentMethod] = useState<PaymentMethod>('cash');
  const [fulfillment, setFulfillment] = useState<FulfillmentMethod>('shop_delivery');
  const [addressId, setAddressId] = useState<number | null>(null);
  const [addressModalOpen, setAddressModalOpen] = useState(false);
  const [addressInitial, setAddressInitial] = useState<AddressInput | null>(null);
  const [shippingFee, setShippingFee] = useState('0');
  const [discount, setDiscount] = useState('0');
  const [note, setNote] = useState('');
  const [lines, setLines] = useState<EditableLine[]>([newLine()]);
  const [touched, setTouched] = useState(false);
  const [hydrated, setHydrated] = useState(!isEdit);
  const [shop, setShop] = useState<LngLat>(FALLBACK_SHOP);
  const [radiusKm, setRadiusKm] = useState(DEFAULT_RADIUS_KM);
  const [locating, setLocating] = useState(false);
  const [applyingMap, setApplyingMap] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  /** Map thu gọn mặc định — chỉ mở khi NV cần chỉnh ghim. */
  const [walkInMapOpen, setWalkInMapOpen] = useState(false);
  const pinLockedRef = useRef(false);
  const mapDrivenRef = useRef(false);
  const lastQueryRef = useRef('');

  const isB2b = !walkIn && customer?.customerType === 'b2b';
  const channelLabels = staffSaleChannels.includes(channel)
    ? staffSaleChannelLabels
    : { ...staffSaleChannelLabels, [channel]: saleChannelLabels[channel] };

  useEffect(() => {
    if (!isEdit || !existing.data || hydrated) return;
    const o = existing.data;
    if (!['pending', 'confirmed'].includes(o.state)) {
      navigate(`/orders/${o.id}`, { replace: true });
      return;
    }
    const isWalkIn = o.customerId == null;
    setWalkIn(isWalkIn);
    setCustomerId(o.customerId);
    setWalkInName(o.customerName ?? 'Khách vãng lai');
    setWalkInPhone(o.customer?.phone ?? '');
    if (o.address && isWalkIn) {
      setWalkInAddress({
        recipientName: o.address.recipientName ?? '',
        phone: o.address.phone ?? '',
        line1: o.address.line1,
        ward: o.address.ward ?? '',
        district: o.address.district ?? '',
        province: o.address.province ?? '',
        latitude: o.address.latitude,
        longitude: o.address.longitude,
        distanceKm: o.address.distanceKm,
      });
    }
    setChannel(o.channel ?? 'phone');
    setPaymentMethod(normalizePaymentForFulfillment(toSalesPaymentMethod(o.paymentMethod), o.fulfillmentMethod));
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

  useEffect(() => {
    if (isEdit && !hydrated) return;
    if (walkIn) return;
    if (addressId != null && addresses?.some((a) => a.id === addressId)) return;
    setAddressId(addresses?.find((a) => a.isDefault)?.id ?? addresses?.[0]?.id ?? null);
  }, [addresses, isEdit, hydrated, addressId, walkIn]);

  const openAddAddress = () => {
    if (!customerId) return;
    const isFirst = !addresses?.length;
    setAddressInitial({
      ...emptyAddress,
      recipientName: customer?.name ?? '',
      phone: customer?.phone ?? '',
      isDefault: isFirst,
    });
    setAddressModalOpen(true);
  };

  useEffect(() => {
    if (walkIn) {
      setCustomerId(null);
      setAddressId(null);
      if (paymentMethod === 'credit_net') setPaymentMethod('cash');
    } else {
      setWalkInMapOpen(false);
    }
  }, [walkIn]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (fulfillment === 'pickup') setWalkInMapOpen(false);
  }, [fulfillment]);

  // Shop tự giao + tiền mặt = thu khi giao → COD.
  useEffect(() => {
    setPaymentMethod((prev) => normalizePaymentForFulfillment(prev, fulfillment));
  }, [fulfillment]);

  useEffect(() => {
    let cancelled = false;
    geoApi
      .shop()
      .then((s) => {
        if (cancelled) return;
        setShop({ lat: Number(s.latitude), lng: Number(s.longitude) });
        setRadiusKm(Number(s.deliveryRadiusKm) || DEFAULT_RADIUS_KM);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  const walkInAddressQuery = useMemo(
    () => [walkInAddress.line1, walkInAddress.ward, walkInAddress.province].filter((p) => p && String(p).trim()).join(', '),
    [walkInAddress.line1, walkInAddress.ward, walkInAddress.province],
  );

  // Gõ đủ địa chỉ + tỉnh → geocode ghim / km (giống AddressFormModal).
  useEffect(() => {
    if (!walkIn || fulfillment === 'pickup') return;
    if (!walkInAddress.line1?.trim() || !walkInAddress.province?.trim()) return;
    if (pinLockedRef.current && lastQueryRef.current === walkInAddressQuery) return;

    const t = window.setTimeout(async () => {
      if (pinLockedRef.current && lastQueryRef.current === walkInAddressQuery) return;
      setLocating(true);
      setLocateError(null);
      try {
        const hit = await geoApi.geocode(walkInAddressQuery);
        lastQueryRef.current = walkInAddressQuery;
        pinLockedRef.current = false;
        mapDrivenRef.current = false;
        setWalkInAddress((prev) => ({
          ...prev,
          latitude: Number(hit.latitude),
          longitude: Number(hit.longitude),
          distanceKm: Number(hit.distanceKm),
        }));
      } catch (err) {
        setLocateError(err instanceof Error ? err.message : 'Chưa định vị được — vẫn có thể bấm map');
      } finally {
        setLocating(false);
      }
    }, GEOCODE_DEBOUNCE_MS);

    return () => window.clearTimeout(t);
  }, [walkIn, fulfillment, walkInAddressQuery, walkInAddress.line1, walkInAddress.province]);

  async function reverseWalkInPin(lat: number, lng: number) {
    setApplyingMap(true);
    setLocateError(null);
    try {
      const hit = await geoApi.reverse(lat, lng);
      const next = applyReverseAddress(hit);
      pinLockedRef.current = true;
      lastQueryRef.current = [next.line1, next.ward, next.province]
        .filter((p) => p && String(p).trim())
        .join(', ');
      setWalkInAddress((prev) => ({
        ...prev,
        line1: next.line1 ?? prev.line1,
        ward: next.ward,
        district: null,
        province: next.province,
        latitude: next.latitude,
        longitude: next.longitude,
        distanceKm: next.distanceKm,
      }));
    } catch (err) {
      setLocateError(err instanceof Error ? err.message : 'Không đọc được địa chỉ từ bản đồ');
    } finally {
      setApplyingMap(false);
      mapDrivenRef.current = false;
    }
  }

  useEffect(() => {
    if (!walkIn || fulfillment === 'pickup' || !mapDrivenRef.current) return;
    if (walkInAddress.latitude == null || walkInAddress.longitude == null) return;
    const lat = walkInAddress.latitude;
    const lng = walkInAddress.longitude;
    const t = window.setTimeout(() => {
      void reverseWalkInPin(lat, lng);
    }, REVERSE_DEBOUNCE_MS);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when pin moves from map
  }, [walkIn, fulfillment, walkInAddress.latitude, walkInAddress.longitude]);

  const selectedAddress = addresses?.find((a) => a.id === addressId);
  const walkInInRadius = walkInAddress.distanceKm != null && walkInAddress.distanceKm <= radiusKm;
  const pricingLines = useMemo(() => toPricingLines(lines), [lines]);
  const effectivePaymentMethod = normalizePaymentForFulfillment(paymentMethod, fulfillment);
  const pricing = usePricing({
    customerId: walkIn ? null : customerId,
    paymentMethod: effectivePaymentMethod,
    shippingFee: Number(shippingFee) || 0,
    discountTotal: Number(discount) || 0,
    lines: toPricingLines(lines.filter(isCompleteLine)),
  });
  const linesValid = lines.every(isCompleteLine);
  const needsAddress = fulfillment !== 'pickup';
  const shortage = pricing.data?.lines.some((l) => l.shortfall > 0);
  const discountAmount = Number(discount) || 0;
  const shippingAmount = Number(shippingFee) || 0;
  const phoneError = walkIn ? validateRequiredPhone(walkInPhone, 'Số điện thoại') : undefined;
  const customerOk = walkIn
    ? walkInName.trim().length > 0 && !phoneError
    : !!customerId;
  const walkInAddressOk = !!walkInAddress.line1.trim() && !!walkInAddress.province?.trim();
  const addressOk = !needsAddress || (walkIn ? walkInAddressOk : (!!customerId && !!addressId));

  const body = () => ({
    customerId: walkIn ? null : customerId,
    customerName: walkIn ? walkInName.trim() || 'Khách vãng lai' : null,
    phone: walkIn ? normalizeVnPhone(walkInPhone) : null,
    orderType: (isB2b ? 'b2b' : 'retail') as 'b2b' | 'retail',
    channel,
    paymentMethod: effectivePaymentMethod,
    fulfillmentMethod: fulfillment,
    shipAddressId: walkIn || !needsAddress ? null : addressId,
    shipAddress: walkIn && needsAddress
      ? {
          recipientName: walkInAddress.recipientName?.trim() || walkInName.trim() || null,
          phone: normalizeVnPhone(walkInAddress.phone) || normalizeVnPhone(walkInPhone),
          line1: walkInAddress.line1.trim(),
          ward: walkInAddress.ward?.trim() || null,
          district: null,
          province: walkInAddress.province?.trim() || null,
          latitude: walkInAddress.latitude ?? null,
          longitude: walkInAddress.longitude ?? null,
          distanceKm: walkInAddress.distanceKm ?? null,
        }
      : null,
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
    if (customerOk && linesValid && addressOk) {
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
            <div className="mb-3 flex flex-wrap gap-2">
              <Chip tone={!walkIn ? 'blue' : 'slate'}>
                <button type="button" className="bg-transparent" onClick={() => setWalkIn(false)}>Khách có sẵn</button>
              </Chip>
              <Chip tone={walkIn ? 'blue' : 'slate'}>
                <button type="button" className="bg-transparent" onClick={() => setWalkIn(true)}>Khách vãng lai</button>
              </Chip>
            </div>
            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-3">
              {walkIn ? (
                <>
                  <Field label="Tên khách" required error={touched && !walkInName.trim() ? 'Nhập tên khách vãng lai' : undefined} className="md:col-span-2 xl:col-span-1">
                    <Input value={walkInName} onChange={(e) => setWalkInName(e.target.value)} placeholder="Nhập tên khách" />
                  </Field>
                  <Field label="Số điện thoại" required error={touched ? phoneError : undefined}>
                    <Input value={walkInPhone} onChange={(e) => setWalkInPhone(e.target.value)} placeholder="09xxxxxxxx" inputMode="tel" />
                  </Field>
                </>
              ) : (
                <Field label="Khách hàng" required error={touched && !customerId ? 'Vui lòng chọn khách' : undefined} className="md:col-span-2 xl:col-span-1">
                  <CustomerSearchField value={customerId} onChange={setCustomerId} />
                </Field>
              )}
              <Field label="Kênh bán" required><Select value={channel} onChange={(e) => setChannel(e.target.value as SaleChannel)}>{enumOptions(channelLabels)}</Select></Field>
              <Field label="Hình thức giao" required><Select value={fulfillment} onChange={(e) => setFulfillment(e.target.value as FulfillmentMethod)}>{enumOptions(fulfillmentLabels)}</Select></Field>
              {needsAddress && walkIn && (
                <>
                  <Field
                    label="Địa chỉ giao"
                    required
                    className="md:col-span-2 xl:col-span-3"
                    error={touched && !walkInAddress.line1.trim() ? 'Nhập địa chỉ giao hàng' : undefined}
                  >
                    <Input
                      value={walkInAddress.line1}
                      onChange={(e) => {
                        pinLockedRef.current = false;
                        setWalkInAddress((a) => ({ ...a, line1: e.target.value }));
                      }}
                      placeholder="Số nhà, đường…"
                    />
                  </Field>
                  <AdminUnitFields
                    province={walkInAddress.province ?? null}
                    ward={walkInAddress.ward ?? null}
                    onChange={(next) => {
                      pinLockedRef.current = false;
                      setWalkInAddress((a) => ({ ...a, ...next, district: null }));
                    }}
                  />
                  {touched && !walkInAddress.province?.trim() && (
                    <p className="md:col-span-2 xl:col-span-3 text-small text-red-fg">Chọn tỉnh/thành</p>
                  )}
                  <Field label="Người nhận">
                    <Input
                      value={walkInAddress.recipientName ?? ''}
                      onChange={(e) => setWalkInAddress((a) => ({ ...a, recipientName: e.target.value }))}
                      placeholder={walkInName || 'Người nhận'}
                    />
                  </Field>
                  <Field label="SĐT người nhận">
                    <Input
                      value={walkInAddress.phone ?? ''}
                      onChange={(e) => setWalkInAddress((a) => ({ ...a, phone: e.target.value }))}
                      placeholder={walkInPhone || '09xxxxxxxx'}
                      inputMode="tel"
                    />
                  </Field>
                  <div className="md:col-span-2 xl:col-span-3 space-y-2">
                    <div className="flex flex-wrap items-center gap-2">
                      <Button
                        type="button"
                        variant={walkInMapOpen ? 'secondary' : 'ghost'}
                        onClick={() => setWalkInMapOpen((v) => !v)}
                      >
                        {walkInMapOpen ? 'Ẩn bản đồ' : 'Chọn trên bản đồ'}
                      </Button>
                      {locating && <span className="text-small text-ink-muted">Đang định vị…</span>}
                      {applyingMap && <span className="text-small text-ink-muted">Đang đọc địa chỉ từ bản đồ…</span>}
                      {!walkInMapOpen && walkInAddress.distanceKm != null && (
                        <span className="text-small text-ink-muted">Đã định vị — mở bản đồ nếu cần chỉnh ghim</span>
                      )}
                    </div>
                    {locateError && <p className="text-small text-red-600">{locateError}</p>}
                    {walkInMapOpen && (
                      <GoongMapPicker
                        open={walkIn && needsAddress && walkInMapOpen}
                        latitude={walkInAddress.latitude ?? null}
                        longitude={walkInAddress.longitude ?? null}
                        fallbackCenter={shop}
                        onChange={(pos) => {
                          pinLockedRef.current = true;
                          mapDrivenRef.current = true;
                          setWalkInAddress((prev) => ({
                            ...prev,
                            latitude: pos.lat,
                            longitude: pos.lng,
                            distanceKm: null,
                          }));
                        }}
                      />
                    )}
                  </div>
                </>
              )}
              {needsAddress && !walkIn && (
                <Field
                  label="Địa chỉ giao"
                  required
                  className="md:col-span-2 xl:col-span-3"
                  error={touched && !addressOk
                    ? 'Khách chưa có địa chỉ — hãy thêm địa chỉ hoặc chọn "Khách đến lấy"'
                    : undefined}
                >
                  {customerId && !addresses?.length ? (
                    <div className="flex flex-wrap items-center gap-2 rounded-md border border-dashed border-line bg-card px-3 py-2.5">
                      <p className="min-w-0 flex-1 text-small text-ink-muted">Khách chưa có địa chỉ giao hàng.</p>
                      <Button type="button" icon={<Plus size={14} />} onClick={openAddAddress}>
                        Thêm địa chỉ
                      </Button>
                    </div>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <Select
                        className="min-w-0 flex-1"
                        disabled={!customerId}
                        value={addressId ?? ''}
                        onChange={(e) => setAddressId(Number(e.target.value))}
                      >
                        {!customerId && <option value="">Chọn khách trước</option>}
                        {addresses?.map((a) => (
                          <option key={a.id} value={a.id}>{[a.line1, a.ward, a.province || a.district].filter(Boolean).join(', ')}</option>
                        ))}
                      </Select>
                      <Button type="button" icon={<Plus size={14} />} disabled={!customerId} onClick={openAddAddress}>
                        Thêm địa chỉ
                      </Button>
                    </div>
                  )}
                </Field>
              )}
            </div>
            {needsAddress && !walkIn && selectedAddress && (
              <div className="mt-2 flex items-center gap-2">
                <Chip tone={selectedAddress.inDeliveryRadius ? 'green' : 'amber'}>{selectedAddress.inDeliveryRadius ? 'Trong bán kính tự giao' : 'Ngoài bán kính, nên dùng đối tác giao'}</Chip>
                {selectedAddress.distanceKm !== null && <span className="text-small text-ink-muted">{selectedAddress.distanceKm} km</span>}
              </div>
            )}
            {needsAddress && walkIn && walkInAddress.distanceKm != null && (
              <div className="mt-2 flex items-center gap-2">
                <Chip tone={walkInInRadius ? 'green' : 'amber'}>
                  {walkInInRadius ? 'Trong bán kính tự giao' : 'Ngoài bán kính, nên dùng đối tác giao'}
                </Chip>
                <span className="text-small text-ink-muted">{walkInAddress.distanceKm} km · bán kính {radiusKm} km</span>
              </div>
            )}
          </Card>
          <Card title="Dòng hàng" flush>
            <LineItemsEditor lines={lines} onChange={setLines} variants={variants.data ?? []} priced={pricing.data?.lines} />
            {touched && !linesValid && <p className="px-3 pb-3 text-small text-red-fg">Mỗi dòng cần chọn sản phẩm và số lượng là số nguyên dương.</p>}
          </Card>
        </div>

        <aside className="grid items-start gap-4 md:grid-cols-2 min-[1500px]:sticky min-[1500px]:top-0 min-[1500px]:grid-cols-1">
          <Card title="Tóm tắt đơn">
            <div className="space-y-3">
              <Field label="Thanh toán" required>
                <Select
                  value={effectivePaymentMethod}
                  onChange={(e) => {
                    const next = e.target.value as PaymentMethod;
                    setPaymentMethod(normalizePaymentForFulfillment(next, fulfillment));
                  }}
                >
                  {SALES_PAYMENT_OPTIONS.map(([key, label]) => (
                    <option key={key} value={key} disabled={fulfillment === 'shop_delivery' && key === 'cash'}>
                      {label}{fulfillment === 'shop_delivery' && key === 'cod' ? ' (thu khi giao)' : ''}
                    </option>
                  ))}
                </Select>
                {fulfillment === 'shop_delivery' && effectivePaymentMethod === 'cod' && (
                  <p className="mt-1 text-small text-ink-muted">Shop tự giao + thu tiền mặt khi giao được ghi nhận là COD.</p>
                )}
              </Field>
              <div className="grid grid-cols-2 gap-2">
                <Field label="Chiết khấu (₫)">
                  <Input
                    type="text"
                    inputMode="numeric"
                    autoComplete="off"
                    className="text-right"
                    value={formatThousands(discount)}
                    onChange={(e) => setDiscount(parseThousands(e.target.value))}
                  />
                </Field>
                <Field label="Phí giao (₫)">
                  <Input
                    type="text"
                    inputMode="numeric"
                    autoComplete="off"
                    className="text-right"
                    value={formatThousands(shippingFee)}
                    onChange={(e) => setShippingFee(parseThousands(e.target.value))}
                  />
                </Field>
              </div>
              <Field label="Ghi chú"><Textarea rows={2} value={note} onChange={(e) => setNote(e.target.value)} /></Field>
              <dl className="space-y-1.5 border-t border-line pt-3">
                <div className="flex justify-between">
                  <dt className="text-ink-muted">Tạm tính</dt>
                  <dd className="num">{money(pricing.data?.subtotal ?? 0)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-ink-muted">Chiết khấu</dt>
                  <dd className="num">{money(discountAmount > 0 ? -discountAmount : 0)}</dd>
                </div>
                <div className="flex justify-between">
                  <dt className="text-ink-muted">Phí giao</dt>
                  <dd className="num">{money(shippingAmount)}</dd>
                </div>
                <div className="flex justify-between text-h2">
                  <dt>Tổng cộng</dt>
                  <dd className="tabular-nums">{money(pricing.data?.total ?? 0)}</dd>
                </div>
              </dl>
              {shortage && <Alert tone="warning" title="Không đủ hàng khả dụng">Đơn Sales thường phải giữ đủ hàng; hệ thống sẽ từ chối nếu tồn không đủ khi xác nhận.</Alert>}
              {pricing.error && <Alert tone="danger">{pricing.error.message}</Alert>}
              <Button variant="primary" className="w-full" loading={saving} onClick={submit}>
                {isEdit ? 'Lưu thay đổi' : 'Tạo đơn'}
              </Button>
            </div>
          </Card>
        </aside>
      </div>

      {customerId != null && (
        <AddressFormModal
          open={addressModalOpen}
          customerId={customerId}
          initial={addressInitial}
          onClose={() => {
            setAddressModalOpen(false);
            setAddressInitial(null);
          }}
          onSaved={(saved) => setAddressId(saved.id)}
        />
      )}
    </>
  );
}
