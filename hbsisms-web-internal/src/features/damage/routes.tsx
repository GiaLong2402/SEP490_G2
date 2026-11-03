import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const DamagePage = lazy(() => import('./pages/DamagePage'));

export const damageRoutes: FeatureRoute[] = [{ path: 'damage', element: <DamagePage />, permission: 'inventory' }];
