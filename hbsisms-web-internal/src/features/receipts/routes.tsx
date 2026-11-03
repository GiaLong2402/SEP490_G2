import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ReceiptsPage = lazy(() => import('./pages/ReceiptsPage'));

export const receiptRoutes: FeatureRoute[] = [{ path: 'receipts', element: <ReceiptsPage />, permission: 'inventory' }];
