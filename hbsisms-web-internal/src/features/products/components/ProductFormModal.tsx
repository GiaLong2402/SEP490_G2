import { useEffect, useRef, useState } from 'react';
<<<<<<< HEAD
import { ApiError } from '@/shared/api/client';
=======
import { ImagePlus, Trash2 } from 'lucide-react';
>>>>>>> origin/main
import { useAction } from '@/shared/api/useAction';
import { uploadImageToCloudinary } from '@/shared/media/cloudinaryUpload';
import { Button, Checkbox, Field, IconButton, Input, Modal, Textarea, useToast } from '@/shared/ui';
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

<<<<<<< HEAD
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
=======
export function ProductFormModal({ open, product, onClose, onSaved }: { open: boolean; product?: ProductDetail; onClose: () => void; onSaved?: (id: number) => void }) {
  const categories = useCategories();
  const toast = useToast();
  const mainInputRef = useRef<HTMLInputElement>(null);
  const galleryInputRef = useRef<HTMLInputElement>(null);
  const [form, setForm] = useState<ProductInput>(empty);
  const [touched, setTouched] = useState(false);
  const [addingCategory, setAddingCategory] = useState(false);
  const [uploadingMain, setUploadingMain] = useState(false);
  const [uploadingGallery, setUploadingGallery] = useState(false);
>>>>>>> origin/main

  useEffect(() => {
    if (!open) return;
    setTouched(false);
<<<<<<< HEAD
    setUploadError(null);
=======
    setUploadingMain(false);
    setUploadingGallery(false);
>>>>>>> origin/main
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
<<<<<<< HEAD
      const urls = collectImageUrls(product);
      setImageUrls(urls);
      setCoverUrl(product.imageUrl?.trim() || urls[0] || null);
    } else {
      setForm(empty);
      setImageUrls([]);
      setCoverUrl(null);
=======
    } else {
      setForm(empty);
>>>>>>> origin/main
    }
  }, [open, product]);

  const set = <K extends keyof ProductInput>(key: K, value: ProductInput[K]) => setForm((f) => ({ ...f, [key]: value }));

<<<<<<< HEAD
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

=======
>>>>>>> origin/main
  const save = useAction(
    () => {
      const gallery = imageUrls.filter((url) => url !== coverUrl);
      const body: ProductInput = {
        ...form,
        isRawMaterial: false,
        isSellable: true,
        isProducible: false,
        unitId: null,
<<<<<<< HEAD
        imageUrl: coverUrl,
        gallery,
=======
        imageUrl: form.imageUrl?.trim() || null,
        gallery: (form.gallery ?? []).map((url) => String(url).trim()).filter(Boolean),
>>>>>>> origin/main
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

<<<<<<< HEAD
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
=======
  const uploading = uploadingMain || uploadingGallery;
  const valid = form.code.trim() && form.name.trim();

  async function handleMainFile(file: File | undefined) {
    if (!file) return;
    setUploadingMain(true);
    try {
      // public_id để Cloudinary auto-generate (theo preset); folder = products
      const uploaded = await uploadImageToCloudinary(file);
      set('imageUrl', uploaded.secureUrl);
    } catch (err) {
      toast.error(err);
    } finally {
      setUploadingMain(false);
      if (mainInputRef.current) mainInputRef.current.value = '';
    }
  }

  async function handleGalleryFiles(files: FileList | null) {
    if (!files?.length) return;
    setUploadingGallery(true);
    try {
      const list = Array.from(files);
      const urls: string[] = [];
      for (const file of list) {
        const uploaded = await uploadImageToCloudinary(file);
        urls.push(uploaded.secureUrl);
      }
      setForm((f) => ({ ...f, gallery: [...(f.gallery ?? []), ...urls] }));
    } catch (err) {
      toast.error(err);
    } finally {
      setUploadingGallery(false);
      if (galleryInputRef.current) galleryInputRef.current.value = '';
    }
  }

  function removeGalleryAt(index: number) {
    setForm((f) => ({
      ...f,
      gallery: (f.gallery ?? []).filter((_, i) => i !== index),
    }));
  }

  return (
    <>
      <Modal
        open={open}
        title={product ? 'Sửa sản phẩm' : 'Thêm sản phẩm'}
        onClose={onClose}
        width="lg"
        onSubmit={() => {
          setTouched(true);
          if (valid && !uploading) save.mutate();
        }}
        footer={
          <>
            <Button onClick={onClose}>Hủy bỏ</Button>
            <Button type="submit" variant="primary" loading={save.isPending} disabled={uploading}>
              Lưu
            </Button>
          </>
        }
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

          <div className="sm:col-span-2">
            <div className="mb-2 text-small font-medium text-ink-muted">Ảnh chính</div>
            <div className="flex flex-wrap items-end gap-3">
              {form.imageUrl?.trim() ? (
                <div className="relative h-24 w-24 shrink-0 overflow-hidden rounded-md border border-line">
                  <img src={form.imageUrl.trim()} alt="" className="h-full w-full object-cover" />
                  <IconButton
                    label="Xóa ảnh chính"
                    className="absolute right-1 top-1 h-7 w-7 rounded-full bg-card/95 text-ink shadow-sm"
                    onClick={() => set('imageUrl', '')}
                    disabled={uploadingMain}
                  >
                    <Trash2 size={13} />
                  </IconButton>
                </div>
              ) : (
                <div className="flex h-24 w-24 shrink-0 items-center justify-center rounded-md border border-dashed border-line bg-rowalt text-ink-muted">
                  <ImagePlus size={22} aria-hidden />
                </div>
              )}
              <input
                ref={mainInputRef}
                type="file"
                accept="image/*"
                className="hidden"
                onChange={(e) => void handleMainFile(e.target.files?.[0])}
              />
              <Button
                type="button"
                icon={<ImagePlus size={15} />}
                loading={uploadingMain}
                onClick={() => mainInputRef.current?.click()}
              >
                Tải ảnh lên
              </Button>
            </div>
          </div>

          <div className="sm:col-span-2">
            <div className="mb-1 text-small font-medium text-ink-muted">Thư viện ảnh</div>
            <p className="mb-2 text-small text-ink-faint">Có thể chọn nhiều ảnh cùng lúc</p>
            {(form.gallery ?? []).length > 0 ? (
              <div className="mb-3 flex flex-wrap gap-3">
                {(form.gallery ?? []).map((url, index) => (
                  <div key={`${url}-${index}`} className="relative h-20 w-20 shrink-0 overflow-hidden rounded-md border border-line">
                    <img src={url} alt="" className="h-full w-full object-cover" />
                    <IconButton
                      label="Xóa ảnh gallery"
                      className="absolute right-1 top-1 h-7 w-7 rounded-full bg-card/95 text-ink shadow-sm"
                      onClick={() => removeGalleryAt(index)}
                      disabled={uploadingGallery}
                    >
                      <Trash2 size={13} />
                    </IconButton>
                  </div>
                ))}
              </div>
            ) : null}
            <input
              ref={galleryInputRef}
              type="file"
              accept="image/*"
              multiple
              className="hidden"
              onChange={(e) => void handleGalleryFiles(e.target.files)}
            />
            <Button
              type="button"
              icon={<ImagePlus size={15} />}
              loading={uploadingGallery}
              onClick={() => galleryInputRef.current?.click()}
            >
              Thêm ảnh
            </Button>
          </div>

          <div className="flex gap-5 sm:col-span-2">
            <Checkbox label="Hiển thị trên cổng B2C" checked={form.isPublished} onChange={(e) => set('isPublished', e.target.checked)} />
            <Checkbox label="Đang kinh doanh" checked={form.isActive} onChange={(e) => set('isActive', e.target.checked)} />
          </div>
>>>>>>> origin/main
        </div>
      </Modal>
      <CategoryFormModal open={addingCategory} onClose={() => setAddingCategory(false)} onSaved={(saved) => set('categoryId', saved.id)} />
    </>
  );
}
