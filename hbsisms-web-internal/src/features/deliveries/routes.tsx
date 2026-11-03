import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const DeliveriesPage = lazy(() => import('./pages/DeliveriesPage'));

// Nhân viên giao mang vai trò kho nên mở cho cả hai quyền (spec mục 5 #7).
export const deliveryRoutes: FeatureRoute[] = [{ path: 'deliveries', element: <DeliveriesPage />, permission: ['orders', 'inventory'] }];
