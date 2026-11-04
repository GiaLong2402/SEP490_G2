import { useEffect, useState } from 'react';
import { useAction } from '@/shared/api/useAction';
import { Button, Field, Input, Modal, Select, Textarea } from '@/shared/ui';
import { productsApi } from '../api';
import { useCategories } from '../hooks';
import type { CategoryListItem } from '../types';

const empty = { code: '', name: '', description: '', parentId: '', sortOrder: '0', isActive: true };

/** "Chả hải sản" -> "cha-hai-san", cùng kiểu với mã trong seed.sql. */
const toCode = (name: string) =>
  name.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/[đĐ]/g, 'd').toLowerCase().trim().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');

export function CategoryFormModal({ open, category, onClose, onSaved }: { open: boolean; category?: CategoryListItem; onClose: () => void; onSaved?: (id: number) => void }) {
  const categories = useCategories().data ?? [];
  const [form, setForm] = useState(empty);
  const [codeEdited, setCodeEdited] = useState(false);
  const [touched, setTouched] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setCodeEdited(!!category);
    setForm(category ? { code: category.code, name: category.name, description: category.description ?? '', parentId: category.parentId ? String(category.parentId) : '', sortOrder: String(category.sortOrder), isActive: category.isActive } : { ...empty, sortOrder: String(categories.reduce((max, c) => Math.max(max, c.sortOrder), 0) + 1) });
    // Chỉ khởi tạo form lúc mở; danh sách làm mới sau đó không được ghi đè nội dung đang nhập.
  }, [open, category]); // eslint-disable-line react-hooks/exhaustive-deps

  // Không cho chọn chính nó hoặc loại con của nó làm loại cha.
  const blocked = new Set<number>(category ? [category.id] : []);
  for (let grew = true; grew; ) {
    grew = false;
    categories.forEach((c) => {
      if (c.parentId !== null && blocked.has(c.parentId) && !blocked.has(c.id)) {
        blocked.add(c.id);
        grew = true;
      }
    });
  }

  const errors = {
    name: !form.name.trim() ? 'Vui lòng nhập tên loại' : undefined,
    code: !form.code.trim() ? 'Vui lòng nhập mã loại' : undefined,
    sortOrder: !Number.isInteger(Number(form.sortOrder)) ? 'Phải là số nguyên' : undefined,
  };

  const save = useAction(
    (body: { code: string; name: string; description: string | null; parentId: number | null; sortOrder: number; isActive: boolean }) =>
      category ? productsApi.updateCategory(category.id, body) : productsApi.createCategory(body),
    {
      success: (saved) => `Đã lưu loại ${saved.name}`,
      onDone: (saved) => {
        onClose();
        onSaved?.(saved.id);
      },
    },
  );

  return (
    <Modal
      open={open}
      title={category ? `Sửa loại ${category.name}` : 'Thêm loại sản phẩm'}
      onClose={onClose}
      onSubmit={() => {
        setTouched(true);
        if (errors.name || errors.code || errors.sortOrder) return;
        // Build body at submit time so isActive matches what the user just chose.
        save.mutate({
          code: form.code.trim(),
          name: form.name.trim(),
          description: form.description.trim() || null,
          parentId: form.parentId ? Number(form.parentId) : null,
          sortOrder: Number(form.sortOrder),
          isActive: form.isActive,
        });
      }}
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
    >
      <div className="grid gap-3 sm:grid-cols-2">
        <Field label="Tên loại" required error={touched ? errors.name : undefined}>
          <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value, code: codeEdited ? form.code : toCode(e.target.value) })} />
        </Field>
        <Field label="Mã loại" required error={touched ? errors.code : undefined}>
          <Input value={form.code} onChange={(e) => { setCodeEdited(true); setForm({ ...form, code: e.target.value }); }} />
        </Field>
        <Field label="Loại cha">
          <Select value={form.parentId} onChange={(e) => setForm({ ...form, parentId: e.target.value })}>
            <option value="">Không có</option>
            {categories.filter((c) => !blocked.has(c.id)).map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </Select>
        </Field>
        <Field label="Thứ tự hiển thị" error={touched ? errors.sortOrder : undefined}>
          <Input type="number" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} />
        </Field>
        <Field label="Mô tả" className="sm:col-span-2">
          <Textarea value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
        </Field>
        <Field
          label="Trạng thái"
          className="sm:col-span-2"
          hint={
            category && !form.isActive && category.productCount > 0
              ? `${category.productCount} sản phẩm vẫn giữ loại này, nhưng loại sẽ không còn trong ô chọn khi tạo sản phẩm mới.`
              : undefined
          }
        >
          <Select
            value={form.isActive ? 'true' : 'false'}
            onChange={(e) => setForm({ ...form, isActive: e.target.value === 'true' })}
          >
            <option value="true">Đang dùng</option>
            <option value="false">Ngừng dùng</option>
          </Select>
        </Field>
      </div>
    </Modal>
  );
}
