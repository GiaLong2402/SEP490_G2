import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const SuppliersPage = lazy(() => import('./pages/SuppliersPage'));

export const supplierRoutes: FeatureRoute[] = [{ path: 'suppliers', element: <SuppliersPage />, permission: 'inventory' }];
