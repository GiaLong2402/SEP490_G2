import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const DebtPage = lazy(() => import('./pages/DebtPage'));

export const debtRoutes: FeatureRoute[] = [{ path: 'debt', element: <DebtPage />, permission: 'debt' }];
