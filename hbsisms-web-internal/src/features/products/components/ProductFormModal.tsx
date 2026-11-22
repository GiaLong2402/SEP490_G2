import { useEffect, useRef, useState } from 'react';
import { ApiError } from '@/shared/api/client';
import { useAction } from '@/shared/api/useAction';
import { Button, Checkbox, Field, Input, Modal, Textarea } from '@/shared/ui';
import { productsApi } from '../api';
import { CategoryFormModal } from './CategoryFormModal';
import { CategorySelectMenu } from './CategoryFilterMenu';
import { useCategories } from '../hooks';
import type { ProductDetail, ProductInput } from '../types';

/** Sản phẩm = hàng bán (quy cách khay). NVL quản lý riêng — mentor tạm bỏ vừa NVL vừa bán. */
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

function collectImageUrls(product?: ProductDetail) {
  if (!product) return [] as string[];
  const urls: string[] = [];
  if (product.imageUrl?.trim()) urls.push(product.imageUrl.trim());
  for (const url of product.gallery ?? []) {
    const trimmed = url?.trim();
    if (trimmed && !urls.includes(trimmed)) urls.push(trimmed);
  }
  return urls;
}

export function ProductFormModal({ open, product, onClose, onSaved }: { open: boolean; product?: ProductDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const categories = useCategories();
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [form, setForm] = useState<ProductInput>(empty);
  const [imageUrls, setImageUrls] = useState<string[]>([]);
  const [coverUrl, setCoverUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [touched, setTouched] = useState(false);
  const [addingCategory, setAddingCategory] = useState(false);

  useEffect(() => {
    if (!open) return;
    setTouched(false);
    setUploadError(null);
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
        isRawMaterial: false,
        isSellable: true,
        isProducible: false,
        unitId: null,
      });
      const urls = collectImageUrls(product);
      setImageUrls(urls);
      setCoverUrl(product.imageUrl?.trim() || urls[0] || null);
    } else {
      setForm(empty);
      setImageUrls([]);
      setCoverUrl(null);
    }
  }, [open, product]);

  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm((f) => ({ ...f, [key]: value }));

  const removeImage = (url: string) => {
    setImageUrls((prev) => {
      const next = prev.filter((u) => u !== url);
      setCoverUrl((current) => {
        if (current !== url) return current;
        return next[0] ?? null;
      });
      return next;
    });
  };

  const onPickFiles = async (files: FileList | null) => {
    if (!files?.length) return;
    setUploadError(null);
    setUploading(true);
    try {
      const { urls } = await productsApi.uploadImages(Array.from(files));
      setImageUrls((prev) => {
        const merged = [...prev];
        for (const url of urls) {
          if (!merged.includes(url)) merged.push(url);
        }
        return merged;
      });
      setCoverUrl((current) => current ?? urls[0] ?? null);
    } catch (error) {
      setUploadError(error instanceof ApiError ? error.message : 'Không tải được ảnh');
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const save = useAction(
    () => {
      const gallery = imageUrls.filter((url) => url !== coverUrl);
      const body: ProductInput = {
        ...form,
        isRawMaterial: false,
        isSellable: true,
        isProducible: false,
        unitId: null,
        imageUrl: coverUrl,
        gallery,
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
      footer={<><Button onClick={onClose}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending || uploading}>Lưu</Button></>}
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
        <Field
          label="Ảnh sản phẩm"
          className="sm:col-span-2"
          hint="Tải JPEG/PNG/WebP từ máy (tối đa 5MB/ảnh). Bấm một ảnh để chọn làm đại diện."
          error={uploadError ?? undefined}
        >
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            className="block w-full text-small file:mr-3 file:rounded file:border-0 file:bg-surface-2 file:px-3 file:py-1.5 file:text-small"
            disabled={uploading}
            onChange={(e) => void onPickFiles(e.target.files)}
          />
          {uploading ? <p className="mt-2 text-small text-ink-faint">Đang tải ảnh lên…</p> : null}
          {imageUrls.length > 0 ? (
            <div className="mt-3 flex flex-wrap gap-2">
              {imageUrls.map((url) => {
                const isCover = coverUrl === url;
                return (
                  <div key={url} className="relative">
                    <button
                      type="button"
                      onClick={() => setCoverUrl(url)}
                      className={`block overflow-hidden rounded border-2 ${isCover ? 'border-primary' : 'border-line'}`}
                      title={isCover ? 'Ảnh đại diện' : 'Chọn làm ảnh đại diện'}
                    >
                      <img src={url} alt="" className="h-20 w-20 object-cover" />
                    </button>
                    {isCover ? (
                      <span className="absolute left-1 top-1 rounded bg-primary px-1 text-[10px] font-medium text-white">Đại diện</span>
                    ) : null}
                    <button
                      type="button"
                      className="absolute -right-1 -top-1 flex h-5 w-5 items-center justify-center rounded-full bg-ink text-[11px] text-white"
                      onClick={() => removeImage(url)}
                      aria-label="Xóa ảnh"
                    >
                      ×
                    </button>
                  </div>
                );
              })}
            </div>
          ) : (
            <p className="mt-2 text-small text-ink-faint">Chưa có ảnh</p>
          )}
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
