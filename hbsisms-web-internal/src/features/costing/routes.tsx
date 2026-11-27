import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const CostingPage = lazy(() => import('./pages/CostingPage'));

export const costingRoutes: FeatureRoute[] = [
  { path: 'costing', element: <CostingPage />, permission: ['inventory', 'admin', 'reports'] },
];
