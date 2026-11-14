import { useEffect, useMemo, useState } from 'react';
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
  const isEdit = addressId != null;
  const hasPin = form.latitude != null && form.longitude != null;

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setLocateError(null);
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

  async function locateFromAddress() {
    if (!form.line1.trim()) {
      setLocateError('Nhập số nhà / đường (ô Địa chỉ) trước khi định vị');
      return;
    }
    if (!form.province?.trim()) {
      setLocateError('Chọn tỉnh/thành trước khi định vị');
      return;
    }
    setLocating(true);
    setLocateError(null);
    try {
      const hit = await geoApi.geocode(addressQuery);
      setForm((prev) => ({
        ...prev,
        latitude: Number(hit.latitude),
        longitude: Number(hit.longitude),
        distanceKm: Number(hit.distanceKm),
      }));
    } catch (err) {
      setLocateError(err instanceof Error ? err.message : 'Không định vị được địa chỉ');
    } finally {
      setLocating(false);
    }
  }

  async function applyAddressFromMap() {
    if (form.latitude == null || form.longitude == null) {
      setLocateError('Chọn vị trí trên bản đồ trước');
      return;
    }
    setApplyingMap(true);
    setLocateError(null);
    try {
      const hit = await geoApi.reverse(form.latitude, form.longitude);
      const next = applyReverseAddress(hit);
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
    }
  }

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
            onChange={(e) => setForm({ ...form, line1: e.target.value })}
            placeholder="Số nhà, đường, thôn/ấp…"
          />
        </Field>

        <AdminUnitFields
          province={form.province}
          ward={form.ward}
          onChange={(next) => setForm((prev) => ({ ...prev, ...next, district: null }))}
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
            <Button type="button" onClick={locateFromAddress} loading={locating}>
              Định vị từ địa chỉ
            </Button>
            <Button
              type="button"
              onClick={applyAddressFromMap}
              loading={applyingMap}
              disabled={!hasPin}
            >
              Dùng địa chỉ từ bản đồ
            </Button>
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
            onChange={(pos) =>
              setForm((prev) => ({
                ...prev,
                latitude: pos.lat,
                longitude: pos.lng,
                distanceKm: null,
              }))
            }
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
