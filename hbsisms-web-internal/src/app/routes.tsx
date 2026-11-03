import { lazy, Suspense } from 'react';
import { SearchX, TriangleAlert } from 'lucide-react';
import { createBrowserRouter } from 'react-router-dom';
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
import { posRoutes } from '@/features/pos';
import { productRoutes } from '@/features/products';
import { quoteRoutes } from '@/features/quotes';
import { receiptRoutes } from '@/features/receipts';
import { reportRoutes } from '@/features/reports';
import { requestRoutes } from '@/features/requests';
import { returnRoutes } from '@/features/returns';
import { stocktakeRoutes } from '@/features/stocktakes';
import { supplierRoutes } from '@/features/suppliers';
import { warehouseRoutes } from '@/features/warehouses';
import { Button, LinkButton, PageLoading } from '@/shared/ui';
import { AppLayout } from './AppLayout';

const LoginPage = lazy(() => import('@/features/auth/pages/LoginPage'));

// Thêm feature mới: khai báo routes trong feature rồi đưa vào danh sách này.
const featureRoutes = [
  ...dashboardRoutes, ...posRoutes, ...requestRoutes, ...quoteRoutes, ...orderRoutes, ...deliveryRoutes, ...returnRoutes, ...inventoryRoutes, ...receiptRoutes, ...stocktakeRoutes, ...warehouseRoutes, ...supplierRoutes,
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
  return (
    <main className="mx-auto mt-24 max-w-sm px-4 text-center">
      <TriangleAlert size={32} className="mx-auto mb-3 text-red-fg" aria-hidden />
      <h1 className="text-h1">Đã có lỗi xảy ra</h1>
      <p className="mb-4 mt-1 text-ink-muted">Dữ liệu của bạn không bị ảnh hưởng. Hãy tải lại trang và thử lại.</p>
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
