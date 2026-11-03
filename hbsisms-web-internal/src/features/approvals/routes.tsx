import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ApprovalsPage = lazy(() => import('./pages/ApprovalsPage'));

export const approvalRoutes: FeatureRoute[] = [{ path: 'approvals', element: <ApprovalsPage />, permission: 'approve' }];
