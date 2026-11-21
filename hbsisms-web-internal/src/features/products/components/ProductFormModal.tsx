import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Select, Textarea } from '@/shared/ui';
import { productsApi } from '../api';
import { CategoryFormModal } from './CategoryFormModal';
import { CategorySelectMenu } from './CategoryFilterMenu';
import { useCategories, useUnits } from '../hooks';
import type { ProductDetail, ProductInput } from '../types';

const empty: ProductInput = {
  code: '',
  name: '',
  categoryId: null,
  description: '',
  storageNote: '',
  origin: '',
  imageUrl: '',
  gallery: [],
  isPublished: false,
  isActive: true,
  isRawMaterial: false,
  isSellable: true,
  isProducible: false,
  unitId: null,
};

function galleryToText(gallery: string[] | null | undefined) {
  return (gallery ?? []).join('\n');
}

function textToGallery(text: string) {
  return text
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean);
}

export function ProductFormModal({ open, product, onClose, onSaved }: { open: boolean; product?: ProductDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const categories = useCategories();
  const units = useUnits();
  const [form, setForm] = useState<ProductInput>(empty);
  const [galleryText, setGalleryText] = useState('');
  const [touched, setTouched] = useState(false);
  const [addingCategory, setAddingCategory] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    if (product) {
      setForm({
        code: product.code,
        name: product.name,
        categoryId: product.categoryId,
        description: product.description,
        storageNote: product.storageNote,
        origin: product.origin,
        imageUrl: product.imageUrl ?? '',
        gallery: product.gallery ?? [],
        isPublished: product.isPublished,
        isActive: product.isActive,
        isRawMaterial: product.isRawMaterial ?? false,
        isSellable: product.isSellable ?? true,
        isProducible: product.isProducible ?? false,
        unitId: product.unitId ?? null,
      });
      setGalleryText(galleryToText(product.gallery));
    } else {
      setForm(empty);
      setGalleryText('');
    }
  }, [open, product]);
  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm((f) => ({ ...f, [key]: value }));
  const save = useAction(
    () => {
      const body: ProductInput = {
        ...form,
        imageUrl: form.imageUrl?.trim() || null,
        gallery: textToGallery(galleryText),
        description: form.description?.trim() || null,
        storageNote: form.storageNote?.trim() || null,
        origin: form.origin?.trim() || null,
      };
      return product ? productsApi.update(product.id, body) : productsApi.create(body);
    },
    {
      success: 'Đã lưu sản phẩm',
      onDone: (saved) => {
        onClose();
        onSaved?.(saved.id);
      },
    },
  );
  const noFlag = !form.isRawMaterial && !form.isSellable && !form.isProducible;
  const unitMissing = form.isRawMaterial && form.unitId === null;
  const valid = form.code.trim() && form.name.trim() && !noFlag && !unitMissing;
  return (
    <>
    <Modal
      open={open}
      title={product ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
      onClose={onClose}
      width="lg"
      onSubmit={() => {
        setTouched(true);
        if (valid) save.mutate();
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Mã sản phẩm" required error={touched && !form.code.trim() ? 'Vui lòng nhập mã' : undefined}>
          <Input value={form.code} onChange={(e) => set('code', e.target.value)} />
        </Field>
        <Field label="Tên sản phẩm" required error={touched && !form.name.trim() ? 'Vui lòng nhập tên' : undefined}>
          <Input value={form.name} onChange={(e) => set('name', e.target.value)} />
        </Field>
        <Field label="Loại sản phẩm" hint={<button type="button" className="text-primary hover:underline" onClick={() => setAddingCategory(true)}>+ Thêm loại mới</button>}>
          <CategorySelectMenu
            categories={categories.data ?? []}
            value={form.categoryId}
            onChange={(categoryId) => set('categoryId', categoryId)}
          />
        </Field>
        <Field label="Xuất xứ">
          <Input value={form.origin ?? ''} onChange={(e) => set('origin', e.target.value)} />
        </Field>
        <Field label="Mô tả" className="sm:col-span-2">
          <Textarea value={form.description ?? ''} onChange={(e) => set('description', e.target.value)} />
        </Field>
        <Field label="Hướng dẫn bảo quản" className="sm:col-span-2">
          <Input value={form.storageNote ?? ''} onChange={(e) => set('storageNote', e.target.value)} />
        </Field>
        <Field label="Ảnh chính (URL)" className="sm:col-span-2" hint="Dán URL ảnh thật — chưa hỗ trợ tải file lên Cloudinary">
          <Input
            placeholder="Dán URL ảnh chính vào đây"
            value={form.imageUrl ?? ''}
            onChange={(e) => set('imageUrl', e.target.value)}
          />
        </Field>
        {form.imageUrl?.trim() ? (
          <img
            src={form.imageUrl.trim()}
            alt=""
            className="h-24 w-24 rounded border border-line object-cover sm:col-span-2"
          />
        ) : null}
        <Field label="Gallery (mỗi dòng một URL)" className="sm:col-span-2">
          <Textarea
            rows={3}
            placeholder="Dán mỗi URL một dòng (chỗ này trống = chưa có gallery)"
            value={galleryText}
            onChange={(e) => setGalleryText(e.target.value)}
          />
        </Field>
        <div className="sm:col-span-2 space-y-2">
          <div className="flex flex-wrap gap-5">
            <Checkbox
              label="Là nguyên liệu"
              checked={form.isRawMaterial}
              onChange={(e) => {
                const on = e.target.checked;
                setForm((f) => ({ ...f, isRawMaterial: on, unitId: on ? f.unitId : null }));
              }}
            />
            <Checkbox label="Có thể bán" checked={form.isSellable} onChange={(e) => set('isSellable', e.target.checked)} />
            <Checkbox label="Có thể sản xuất" checked={form.isProducible} onChange={(e) => set('isProducible', e.target.checked)} />
          </div>
          {touched && noFlag ? <p className="text-small text-red-fg">Chọn ít nhất một loại: nguyên liệu, bán hoặc sản xuất</p> : null}
          {form.isRawMaterial ? (
            <Field label="Đơn vị tính (nguyên liệu)" required error={touched && unitMissing ? 'Nguyên liệu bắt buộc chọn đơn vị tính' : undefined} hint="Chỉ dùng khi mặt hàng là NVL (thường là kg). Đồng bộ sang tồn nguyên liệu cùng mã.">
              <Select aria-label="Đơn vị tính nguyên liệu" value={form.unitId === null ? '' : String(form.unitId)} onChange={(e) => set('unitId', e.target.value ? Number(e.target.value) : null)}>
                <option value="">Chọn...</option>
                {(units.data ?? []).filter((u) => u.isActive || u.id === form.unitId).map((u) => <option key={u.id} value={u.id}>{u.name} ({u.code})</option>)}
              </Select>
            </Field>
          ) : null}
        </div>
        <div className="flex gap-5 sm:col-span-2">
          <Checkbox label="Hiển thị trên cổng B2C" checked={form.isPublished} onChange={(e) => set('isPublished', e.target.checked)} />
          <Checkbox label="Đang kinh doanh" checked={form.isActive} onChange={(e) => set('isActive', e.target.checked)} />
        </div>
      </div>
    </Modal>
    <CategoryFormModal open={addingCategory} onClose={() => setAddingCategory(false)} onSaved={(saved) => set('categoryId', saved.id)} />
    </>
  );
}
