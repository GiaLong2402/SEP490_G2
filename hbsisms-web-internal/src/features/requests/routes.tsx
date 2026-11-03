import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const RequestsPage = lazy(() => import('./pages/RequestsPage'));

export const requestRoutes: FeatureRoute[] = [{ path: 'requests', element: <RequestsPage />, permission: 'orders' }];
