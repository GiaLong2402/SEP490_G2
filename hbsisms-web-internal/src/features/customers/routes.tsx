import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const CustomersPage = lazy(() => import('./pages/CustomersPage'));
const CustomerDetailPage = lazy(() => import('./pages/CustomerDetailPage'));

export const customerRoutes: FeatureRoute[] = [
  { path: 'customers', element: <CustomersPage />, permission: 'customers' },
  { path: 'customers/:id', element: <CustomerDetailPage />, permission: 'customers' },
];
