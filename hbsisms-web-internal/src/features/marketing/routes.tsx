import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const VouchersPage = lazy(() => import('./pages/VouchersPage'));
const ReviewsPage = lazy(() => import('./pages/ReviewsPage'));

// Vai trò kho cũng có quyền products để xem hàng, nên gắn voucher và đánh giá với quyền customers.
export const marketingRoutes: FeatureRoute[] = [
  { path: 'vouchers', element: <VouchersPage />, permission: 'customers' },
  { path: 'reviews', element: <ReviewsPage />, permission: 'customers' },
];
