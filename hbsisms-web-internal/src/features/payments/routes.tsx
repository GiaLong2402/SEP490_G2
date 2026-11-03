import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const PaymentsPage = lazy(() => import('./pages/PaymentsPage'));

export const paymentRoutes: FeatureRoute[] = [{ path: 'payments', element: <PaymentsPage />, permission: 'debt' }];
