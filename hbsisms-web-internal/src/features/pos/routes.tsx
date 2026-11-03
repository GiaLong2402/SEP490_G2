import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const PosPage = lazy(() => import('./pages/PosPage'));

export const posRoutes: FeatureRoute[] = [{ path: 'pos', element: <PosPage />, permission: 'orders' }];
