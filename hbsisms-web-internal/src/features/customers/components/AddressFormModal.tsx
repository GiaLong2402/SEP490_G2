import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import type { CustomerAddress, ID } from '@/shared/domain/entities';
import { Button, Checkbox, Field, Input, Modal } from '@/shared/ui';
import { customersApi } from '../api';
import type { AddressInput } from '../types';

/** Địa giới hành chính 2 cấp (sau sáp nhập 2025): xã/phường + tỉnh/thành phố. `district` giữ trong API để tương thích dữ liệu cũ. */
export const emptyAddress: AddressInput = {
  kind: 'shipping',
  recipientName: '',
  phone: '',
  line1: '',
  ward: '',
  district: '',
  province: '',
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
    district: a.district ?? '',
    province: a.province ?? '',
    distanceKm: a.distanceKm,
    isDefault: a.isDefault,
  };
}

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
  const isEdit = addressId != null;

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(initial ?? emptyAddress);
    // Chỉ reset khi mở modal / đổi bản ghi — tránh reset khi parent tạo object mới mỗi render.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional
  }, [open, addressId]);

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

  return (
    <Modal
      open={open}
      title={isEdit ? 'Sửa địa chỉ' : 'Thêm địa chỉ'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (!form.line1.trim()) return;
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
        <Field label="Xã/phường">
          <Input
            value={form.ward ?? ''}
            onChange={(e) => setForm({ ...form, ward: e.target.value, district: '' })}
            placeholder="Xã / phường / đặc khu"
          />
        </Field>
        <Field label="Tỉnh/thành phố">
          <Input
            value={form.province ?? ''}
            onChange={(e) => setForm({ ...form, province: e.target.value })}
            placeholder="Hải Phòng"
          />
        </Field>
        <Field label="Người nhận">
          <Input value={form.recipientName ?? ''} onChange={(e) => setForm({ ...form, recipientName: e.target.value })} />
        </Field>
        <Field label="SĐT người nhận">
          <Input value={form.phone ?? ''} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
        </Field>
        <Field label="Khoảng cách từ kho (km)" hint="Backend sẽ tự tính khi tích hợp bản đồ">
          <Input
            type="number"
            min={0}
            step="0.1"
            value={form.distanceKm ?? ''}
            onChange={(e) => setForm({ ...form, distanceKm: e.target.value === '' ? null : Number(e.target.value) })}
          />
        </Field>
        <div className="self-end pb-2">
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
