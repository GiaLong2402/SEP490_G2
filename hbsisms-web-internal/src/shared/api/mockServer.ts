// Bộ định tuyến giả lập: nhận đúng URL REST như backend thật. Xóa cùng features/*/mock.ts khi có backend.
import { ApiError, tokenStore, type Method, type Page, type Query, type Transport } from './client';

export interface MockRequest<B = any> {
  params: Record<string, string>;
  query: Query;
  body: B;
  userId: number | null;
}

type Handler = (req: MockRequest) => unknown;

const routes: { method: Method; regex: RegExp; keys: string[]; handler: Handler }[] = [];

export function mock(method: Method, pattern: string, handler: Handler) {
  const keys: string[] = [];
  const source = pattern.replace(/:(\w+)/g, (_, key: string) => {
    keys.push(key);
    return '([^/]+)';
  });
  routes.push({ method, regex: new RegExp(`^${source}$`), keys, handler });
}

export function fail(status: number, message: string): never {
  throw new ApiError(status, message);
}

export function paginate<T>(rows: T[], query: Query): Page<T> {
  const page = Math.max(1, Number(query.page ?? 1));
  const pageSize = Math.max(1, Number(query.pageSize ?? 15));
  return { items: rows.slice((page - 1) * pageSize, page * pageSize), total: rows.length, page, pageSize };
}

/** So khớp không phân biệt hoa thường và dấu tiếng Việt. */
export function matches(q: unknown, ...fields: (string | null | undefined)[]) {
  if (!q) return true;
  const fold = (s: string) => s.normalize('NFD').replace(/[̀-ͯ]/g, '').replace(/đ/g, 'd').replace(/Đ/g, 'd').toLowerCase();
  const needle = fold(String(q));
  return fields.some((field) => field && fold(field).includes(needle));
}

export function createMockTransport(afterWrite: () => void, latency = 180): Transport {
  return async <T>(method: Method, path: string, { query = {}, body }: { query?: Query; body?: unknown }) => {
    await new Promise((resolve) => setTimeout(resolve, latency));
    for (const route of routes) {
      if (route.method !== method) continue;
      const match = route.regex.exec(path);
      if (!match) continue;
      const params = Object.fromEntries(route.keys.map((key, index) => [key, decodeURIComponent(match[index + 1])]));
      const token = tokenStore.get();
      const userId = token?.startsWith('mock-') ? Number(token.slice(5)) : null;
      // Sao chép JSON để dữ liệu trả về không dùng chung tham chiếu với CSDL giả lập, giống API thật.
      const result = route.handler({ params, query, body, userId });
      if (method !== 'GET') afterWrite();
      return (result === undefined ? undefined : JSON.parse(JSON.stringify(result))) as T;
    }
    throw new ApiError(404, `Chưa có handler mock cho ${method} ${path}`);
  };
}
