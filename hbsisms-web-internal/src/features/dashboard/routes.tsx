import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const DashboardPage = lazy(() => import('./pages/DashboardPage'));

export const dashboardRoutes: FeatureRoute[] = [{ path: '', element: <DashboardPage /> }];
