import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const PaymentsPage = lazy(() => import('./pages/PaymentsPage'));

/** Sales thu tiền bằng quyền orders; Chủ/Admin xem sổ qua debt. */
export const paymentRoutes: FeatureRoute[] = [{ path: 'payments', element: <PaymentsPage />, permission: ['orders', 'debt'] }];
