import { api } from '@/shared/api/client';

export type CloudinaryUploadResult = {
  secureUrl: string;
  publicId: string;
};

type CloudinarySignature = {
  cloudName: string;
  apiKey: string;
  timestamp: number;
  signature: string;
  folder: string;
};

/** Chỉ giữ a-zA-Z0-9_- , lowercase, cắt độ dài. */
export function sanitizePublicIdPart(value: string, maxLen = 48): string {
  return (
    value
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_-]+/g, '-')
      .replace(/^-+|-+$/g, '')
      .slice(0, maxLen) || 'product'
  );
}

export function buildProductImagePublicId(
  code: string | null | undefined,
  kind: 'main' | 'gallery',
  index?: number,
): string {
  const base = sanitizePublicIdPart(code?.trim() || 'product');
  const stamp = Date.now();
  if (kind === 'main') return `${base}-main-${stamp}`;
  return `${base}-gallery-${stamp}-${index ?? 0}`;
}

async function requestSignature(folder?: string): Promise<CloudinarySignature> {
  return api.post<CloudinarySignature>(
    '/media/cloudinary-signature',
    folder?.trim() ? { folder: folder.trim() } : {},
  );
}

/**
 * Signed upload: xin chữ ký từ backend (API Secret không lên FE), rồi POST file lên Cloudinary.
 */
export async function uploadImageToCloudinary(
  file: File,
  opts?: { folder?: string },
): Promise<CloudinaryUploadResult> {
  if (!file.type.startsWith('image/')) {
    throw new Error('Chỉ hỗ trợ tệp ảnh');
  }

  const sign = await requestSignature(opts?.folder);

  const body = new FormData();
  body.append('file', file);
  body.append('api_key', sign.apiKey);
  body.append('timestamp', String(sign.timestamp));
  body.append('signature', sign.signature);
  body.append('folder', sign.folder);

  const res = await fetch(`https://api.cloudinary.com/v1_1/${sign.cloudName}/image/upload`, {
    method: 'POST',
    body,
  });

  const data = (await res.json().catch(() => null)) as {
    secure_url?: string;
    public_id?: string;
    error?: { message?: string };
  } | null;

  if (!res.ok || !data?.secure_url) {
    throw new Error(data?.error?.message || 'Upload Cloudinary thất bại');
  }

  return {
    secureUrl: data.secure_url,
    publicId: data.public_id ?? '',
  };
}
