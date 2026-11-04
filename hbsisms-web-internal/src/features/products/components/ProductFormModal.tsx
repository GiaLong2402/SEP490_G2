import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Select, Textarea } from '@/shared/ui';
import { productsApi } from '../api';
import { CategoryFormModal } from './CategoryFormModal';
import { useCategories } from '../hooks';
import type { ProductDetail, ProductInput } from '../types';

const empty: ProductInput = { code: '', name: '', categoryId: null, description: '', storageNote: '', origin: '', isPublished: false, isActive: true };

export function ProductFormModal({ open, product, onClose, onSaved }: { open: boolean; product?: ProductDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const categories = useCategories();
  const [form, setForm] = useState<ProductInput>(empty);
  const [touched, setTouched] = useState(false);
  const [addingCategory, setAddingCategory] = useState(false);
  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setForm(product ? { code: product.code, name: product.name, categoryId: product.categoryId, description: product.description, storageNote: product.storageNote, origin: product.origin, isPublished: product.isPublished, isActive: product.isActive } : empty);
  }, [open, product]);
  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm((f) => ({ ...f, [key]: value }));
  const save = useAction(() => (product ? productsApi.update(product.id, form) : productsApi.create(form)), {
    success: 'Đã lưu sản phẩm',
    onDone: (saved) => {
      onClose();
      onSaved?.(saved.id);
    },
  });
  const valid = form.code.trim() && form.name.trim();
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
          <Select value={form.categoryId ?? ''} onChange={(e) => set('categoryId', e.target.value ? Number(e.target.value) : null)}>
            <option value="">Chưa phân loại</option>
            {categories.data?.filter((c) => c.isActive || c.id === form.categoryId).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
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
