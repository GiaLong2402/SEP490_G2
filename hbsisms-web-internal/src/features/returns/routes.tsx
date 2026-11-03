import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ReturnsPage = lazy(() => import('./pages/ReturnsPage'));

export const returnRoutes: FeatureRoute[] = [{ path: 'returns', element: <ReturnsPage />, permission: 'orders' }];
