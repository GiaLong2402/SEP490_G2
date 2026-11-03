import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ReportsPage = lazy(() => import('./pages/ReportsPage'));

export const reportRoutes: FeatureRoute[] = [{ path: 'reports', element: <ReportsPage />, permission: 'reports' }];
