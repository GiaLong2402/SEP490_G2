import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const CostingPage = lazy(() => import('./pages/CostingPage'));
const WacRunningPage = lazy(() => import('./pages/WacRunningPage'));

export const costingRoutes: FeatureRoute[] = [
  { path: 'costing', element: <CostingPage />, permission: ['inventory', 'admin', 'reports'] },
  { path: 'costing/wac', element: <WacRunningPage />, permission: ['inventory', 'admin', 'reports'] },
];
