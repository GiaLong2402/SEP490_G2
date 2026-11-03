// Điểm gắn API duy nhất của ứng dụng. Feature chỉ gọi `api.get('/orders')`;
// transport quyết định gọi mạng thật hay handler mock (xem app/main.tsx).

export type Method = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
export type QueryValue = string | number | boolean | undefined | null;
export type Query = Record<string, QueryValue>;

export interface RequestOptions {
  query?: Query;
  body?: unknown;
  signal?: AbortSignal;
}

export type Transport = <T>(method: Method, path: string, options: RequestOptions) => Promise<T>;

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const TOKEN_KEY = 'hbsisms.token';

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      // Trình duyệt chặn storage: phiên chỉ tồn tại đến khi tải lại trang.
    }
  },
};

export function createHttpTransport(baseUrl: string): Transport {
  return async <T>(method: Method, path: string, { query, body, signal }: RequestOptions) => {
    const url = new URL(baseUrl.replace(/\/$/, '') + path, window.location.origin);
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') url.searchParams.set(key, String(value));
    });
    const token = tokenStore.get();
    const res = await fetch(url, {
      method,
      signal,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
    if (!res.ok) {
      const payload = await res.json().catch(() => null);
      throw new ApiError(res.status, payload?.message ?? `Lỗi máy chủ (${res.status})`);
    }
    return (res.status === 204 ? undefined : await res.json()) as T;
  };
}

let transport: Transport | null = null;
let onUnauthorized: () => void = () => {};

export function configureApi(next: Transport) {
  transport = next;
}

export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

async function send<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  if (!transport) throw new Error('API chưa được cấu hình');
  try {
    return await transport<T>(method, path, options);
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) onUnauthorized();
    throw error;
  }
}

export const api = {
  get: <T>(path: string, query?: Query, signal?: AbortSignal) => send<T>('GET', path, { query, signal }),
  post: <T>(path: string, body?: unknown) => send<T>('POST', path, { body }),
  put: <T>(path: string, body?: unknown) => send<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => send<T>('PATCH', path, { body }),
  delete: <T>(path: string) => send<T>('DELETE', path),
};

/** Dạng trả về chung của API danh sách. */
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
}

export interface ListQuery extends Query {
  q?: string;
  page?: number;
  pageSize?: number;
}
