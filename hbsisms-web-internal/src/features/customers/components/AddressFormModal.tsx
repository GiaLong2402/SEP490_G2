import { useEffect, useMemo, useRef, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { geoApi } from '@/shared/api/geo';
import type { CustomerAddress, ID } from '@/shared/domain/entities';
import { AdminUnitFields } from '@/shared/geo/AdminUnitFields';
import { applyReverseAddress } from '@/shared/geo/applyReverseAddress';
import { GoongMapPicker } from '@/shared/maps/GoongMapPicker';
import type { LngLat } from '@/shared/maps/goong';
import { Button, Checkbox, Field, Input, Modal } from '@/shared/ui';
import { customersApi } from '../api';
import type { AddressInput } from '../types';

const GEOCODE_DEBOUNCE_MS = 700;
const REVERSE_DEBOUNCE_MS = 450;

/** Địa giới hành chính 2 cấp (sau sáp nhập 2025): xã/phường + tỉnh/thành phố. `district` giữ trong API để tương thích dữ liệu cũ. */
export const emptyAddress: AddressInput = {
  kind: 'shipping',
  recipientName: '',
  phone: '',
  line1: '',
  ward: null,
  district: null,
  province: null,
  latitude: null,
  longitude: null,
  distanceKm: null,
  isDefault: false,
};

export function toAddressInput(a: CustomerAddress): AddressInput {
  return {
    kind: a.kind,
    recipientName: a.recipientName,
    phone: a.phone,
    line1: a.line1,
    ward: a.ward,
    district: null,
    province: a.province,
    latitude: a.latitude,
    longitude: a.longitude,
    distanceKm: a.distanceKm,
    isDefault: a.isDefault,
  };
}

const FALLBACK_SHOP: LngLat = { lat: 21.012728, lng: 105.525905 };

export function AddressFormModal({
  open,
  customerId,
  addressId = null,
  initial = null,
  onClose,
  onSaved,
}: {
  open: boolean;
  customerId: ID;
  addressId?: ID | null;
  initial?: AddressInput | null;
  onClose: () => void;
  onSaved?: (address: CustomerAddress) => void;
}) {
  const [form, setForm] = useState<AddressInput>(emptyAddress);
  const [touched, setTouched] = useState(false);
  const [shop, setShop] = useState<LngLat>(FALLBACK_SHOP);
  const [locating, setLocating] = useState(false);
  const [applyingMap, setApplyingMap] = useState(false);
  const [locateError, setLocateError] = useState<string | null>(null);
  /** User kéo ghim → tạm không auto-geocode ghi đè đến khi đổi text địa chỉ. */
  const pinLockedRef = useRef(false);
  /** Chỉ reverse khi user click/kéo map — không chạy khi mở form / geocode từ text. */
  const mapDrivenRef = useRef(false);
  const lastQueryRef = useRef('');
  const isEdit = addressId != null;

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setLocateError(null);
    pinLockedRef.current = false;
    mapDrivenRef.current = false;
    lastQueryRef.current = '';
    setForm(initial ?? emptyAddress);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional
  }, [open, addressId]);

  useEffect(() => {
    if (!open) return;
    let cancelled = false;
    geoApi
      .shop()
      .then((s) => {
        if (!cancelled) setShop({ lat: Number(s.latitude), lng: Number(s.longitude) });
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [open]);

  const save = useAction(
    () => {
      const payload: AddressInput = { ...form, district: '' };
      return isEdit
        ? customersApi.updateAddress(customerId, addressId, payload)
        : customersApi.addAddress(customerId, payload);
    },
    {
      success: isEdit ? 'Đã cập nhật địa chỉ' : 'Đã thêm địa chỉ',
      onDone: (saved) => {
        onClose();
        onSaved?.(saved);
      },
    },
  );

  const line1Error = touched && !form.line1.trim() ? 'Vui lòng nhập địa chỉ' : undefined;
  const provinceError = touched && !form.province?.trim() ? 'Chọn tỉnh/thành' : undefined;

  const addressQuery = useMemo(
    () => [form.line1, form.ward, form.province].filter((p) => p && String(p).trim()).join(', '),
    [form.line1, form.ward, form.province],
  );

  // Gõ đủ địa chỉ + tỉnh → tự geocode ghim (giống external).
  useEffect(() => {
    if (!open) return;
    if (!form.line1?.trim() || !form.province?.trim()) return;
    if (pinLockedRef.current && lastQueryRef.current === addressQuery) return;

    const t = window.setTimeout(async () => {
      if (pinLockedRef.current && lastQueryRef.current === addressQuery) return;
      setLocating(true);
      setLocateError(null);
      try {
        const hit = await geoApi.geocode(addressQuery);
        lastQueryRef.current = addressQuery;
        pinLockedRef.current = false;
        mapDrivenRef.current = false;
        setForm((prev) => ({
          ...prev,
          latitude: Number(hit.latitude),
          longitude: Number(hit.longitude),
          distanceKm: Number(hit.distanceKm),
        }));
      } catch (err) {
        setLocateError(err instanceof Error ? err.message : 'Chưa định vị được — vẫn có thể bấm map hoặc lưu');
      } finally {
        setLocating(false);
      }
    }, GEOCODE_DEBOUNCE_MS);

    return () => window.clearTimeout(t);
  }, [open, addressQuery, form.line1, form.province]);

  async function reverseIntoForm(lat: number, lng: number) {
    setApplyingMap(true);
    setLocateError(null);
    try {
      const hit = await geoApi.reverse(lat, lng);
      const next = applyReverseAddress(hit);
      pinLockedRef.current = true;
      lastQueryRef.current = [next.line1, next.ward, next.province]
        .filter((p) => p && String(p).trim())
        .join(', ');
      setForm((prev) => ({
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

  // Click / kéo ghim → tự đổ Địa chỉ + Tỉnh + Phường.
  useEffect(() => {
    if (!open || !mapDrivenRef.current) return;
    if (form.latitude == null || form.longitude == null) return;
    const lat = form.latitude;
    const lng = form.longitude;
    const t = window.setTimeout(() => {
      void reverseIntoForm(lat, lng);
    }, REVERSE_DEBOUNCE_MS);
    return () => window.clearTimeout(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only when pin moves from map
  }, [open, form.latitude, form.longitude]);

  return (
    <Modal
      open={open}
      title={isEdit ? 'Sửa địa chỉ' : 'Thêm địa chỉ'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!form.line1.trim() || !form.province?.trim()) return;
        save.mutate();
      }}
      footer={(
        <>
          <Button onClick={onClose}>Hủy bỏ</Button>
          <Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button>
        </>
      )}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Địa chỉ" required className="sm:col-span-2" error={line1Error}>
          <Input
            value={form.line1}
            onChange={(e) => {
              pinLockedRef.current = false;
              setForm({ ...form, line1: e.target.value });
            }}
            placeholder="Số nhà, đường, thôn/ấp…"
          />
        </Field>

        <AdminUnitFields
          province={form.province}
          ward={form.ward}
          onChange={(next) => {
            pinLockedRef.current = false;
            setForm((prev) => ({ ...prev, ...next, district: null }));
          }}
        />
        {provinceError && <p className="sm:col-span-2 text-small text-red-fg">{provinceError}</p>}

        <Field label="Người nhận">
          <Input value={form.recipientName ?? ''} onChange={(e) => setForm({ ...form, recipientName: e.target.value })} />
        </Field>
        <Field label="SĐT người nhận">
          <Input value={form.phone ?? ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </Field>

        <div className="sm:col-span-2 space-y-2">
          <div className="flex flex-wrap items-center gap-2">
            {locating && <span className="text-small text-ink-muted">Đang định vị trên bản đồ…</span>}
            {applyingMap && <span className="text-small text-ink-muted">Đang đọc địa chỉ từ bản đồ…</span>}
            {form.distanceKm != null && (
              <span className="text-small text-ink-muted">
                ~{form.distanceKm} km
                {form.distanceKm <= 8 ? ' · trong bán kính' : ' · ngoài bán kính'}
              </span>
            )}
          </div>
          {locateError && <p className="text-small text-red-600">{locateError}</p>}
          <GoongMapPicker
            open={open}
            latitude={form.latitude}
            longitude={form.longitude}
            fallbackCenter={shop}
            onChange={(pos) => {
              pinLockedRef.current = true;
              mapDrivenRef.current = true;
              setForm((prev) => ({
                ...prev,
                latitude: pos.lat,
                longitude: pos.lng,
                distanceKm: null,
              }));
            }}
          />
        </div>

        <div className="self-end pb-2 sm:col-span-2">
          <Checkbox
            label="Đặt làm mặc định"
            checked={form.isDefault}
            onChange={(e) => setForm({ ...form, isDefault: e.target.checked })}
          />
        </div>
      </div>
    </Modal>
  );
}
