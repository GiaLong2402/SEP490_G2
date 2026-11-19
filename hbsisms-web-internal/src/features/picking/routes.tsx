import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const PickingPage = lazy(() => import('./pages/PickingPage'));

export const pickingRoutes: FeatureRoute[] = [
  { path: 'picking', element: <PickingPage />, permission: ['orders', 'inventory'] },
];

export default pickingRoutes;
