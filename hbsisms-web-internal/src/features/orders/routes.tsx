import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const OrdersPage = lazy(() => import('./pages/OrdersPage'));
const OrderFormPage = lazy(() => import('./pages/OrderFormPage'));
const OrderDetailPage = lazy(() => import('./pages/OrderDetailPage'));

export const orderRoutes: FeatureRoute[] = [
  { path: 'orders', element: <OrdersPage />, permission: 'orders' },
  { path: 'orders/new', element: <OrderFormPage />, permission: 'orders' },
  { path: 'orders/:id', element: <OrderDetailPage />, permission: 'orders' },
];
