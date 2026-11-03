import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const WarehousesPage = lazy(() => import('./pages/WarehousesPage'));

export const warehouseRoutes: FeatureRoute[] = [{ path: 'warehouses', element: <WarehousesPage />, permission: 'inventory' }];
