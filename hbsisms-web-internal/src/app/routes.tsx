import { lazy, Suspense } from 'react';
import { SearchX, TriangleAlert } from 'lucide-react';
import { createBrowserRouter, isRouteErrorResponse, useRouteError } from 'react-router-dom';
import { adminRoutes } from '@/features/admin';
import { approvalRoutes } from '@/features/approvals';
import { RequireAuth, RequirePermission } from '@/features/auth';
import { customerRoutes } from '@/features/customers';
import { damageRoutes } from '@/features/damage';
import { dashboardRoutes } from '@/features/dashboard';
import { debtRoutes } from '@/features/debt';
import { deliveryRoutes } from '@/features/deliveries';
import { inventoryRoutes } from '@/features/inventory';
import { marketingRoutes } from '@/features/marketing';
import { orderRoutes } from '@/features/orders';
import { paymentRoutes } from '@/features/payments';
import { pickingRoutes } from '@/features/picking';
import { productRoutes } from '@/features/products';
import { productionRoutes } from '@/features/production';
import { quoteRoutes } from '@/features/quotes';
import { receiptRoutes } from '@/features/receipts';
import { reportRoutes } from '@/features/reports';
import { requestRoutes } from '@/features/requests';
import { returnRoutes } from '@/features/returns';
import { stocktakeRoutes } from '@/features/stocktakes';
import { warehouseRoutes } from '@/features/warehouses';
import { Button, LinkButton, PageLoading } from '@/shared/ui';
import { AppLayout } from './AppLayout';

const LoginPage = lazy(() => import('@/features/auth/pages/LoginPage'));

// Thêm feature mới: khai báo routes trong feature rồi đưa vào danh sách này.
const featureRoutes = [
  ...dashboardRoutes, ...requestRoutes, ...quoteRoutes, ...orderRoutes, ...pickingRoutes, ...deliveryRoutes, ...returnRoutes, ...inventoryRoutes, ...receiptRoutes, ...productionRoutes, ...stocktakeRoutes, ...warehouseRoutes,
  ...damageRoutes, ...paymentRoutes, ...debtRoutes, ...customerRoutes, ...productRoutes, ...marketingRoutes, ...approvalRoutes, ...reportRoutes, ...adminRoutes,
];

function NotFound() {
  return (
    <div className="mx-auto mt-16 max-w-sm text-center">
      <SearchX size={32} className="mx-auto mb-3 text-ink-faint" aria-hidden />
      <h1 className="text-h1">Không tìm thấy trang</h1>
      <p className="mb-4 mt-1 text-ink-muted">Đường dẫn có thể đã thay đổi hoặc không tồn tại.</p>
      <LinkButton to="/">Về trang tổng quan</LinkButton>
    </div>
  );
}

/** Lỗi lúc render: không để người dùng thấy trang lỗi mặc định của thư viện. */
function CrashPage() {
  const err = useRouteError();
  const detail = (() => {
    if (isRouteErrorResponse(err)) return `${err.status} ${err.statusText}`;
    if (err instanceof Error) return err.message;
    if (typeof err === 'string') return err;
    return null;
  })();
  if (import.meta.env.DEV && err instanceof Error && err.stack) {
    console.error('[CrashPage]', err);
  }
  return (
    <main className="mx-auto mt-24 max-w-md px-4 text-center">
      <TriangleAlert size={32} className="mx-auto mb-3 text-red-fg" aria-hidden />
      <h1 className="text-h1">Đã có lỗi xảy ra</h1>
      <p className="mb-4 mt-1 text-ink-muted">Dữ liệu của bạn không bị ảnh hưởng. Hãy tải lại trang và thử lại.</p>
      {import.meta.env.DEV && detail ? (
        <pre className="mb-4 max-h-40 overflow-auto rounded-md border border-line bg-thead p-3 text-left text-small text-red-fg whitespace-pre-wrap">
          {detail}
        </pre>
      ) : null}
      <Button variant="primary" onClick={() => window.location.reload()}>Tải lại trang</Button>
    </main>
  );
}

export const router = createBrowserRouter([
  { path: '/login', element: <Suspense fallback={<PageLoading />}><LoginPage /></Suspense> },
  {
    path: '/',
    element: <RequireAuth><AppLayout /></RequireAuth>,
    errorElement: <CrashPage />,
    children: [
      ...featureRoutes.map((route) => ({ path: route.path || undefined, index: route.path === '' ? (true as const) : undefined, element: <RequirePermission permission={route.permission}>{route.element}</RequirePermission> })),
      { path: '*', element: <NotFound /> },
    ],
  },
]);
