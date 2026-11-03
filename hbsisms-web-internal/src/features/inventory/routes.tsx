import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const InventoryPage = lazy(() => import('./pages/InventoryPage'));

export const inventoryRoutes: FeatureRoute[] = [{ path: 'inventory', element: <InventoryPage />, permission: 'inventory' }];
