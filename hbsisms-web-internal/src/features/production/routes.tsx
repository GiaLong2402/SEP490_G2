import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ProductionOrdersPage = lazy(() => import('./pages/ProductionOrdersPage'));
const ProductionOrderDetailPage = lazy(() => import('./pages/ProductionOrderDetailPage'));
const NewProductionOrderPage = lazy(() => import('./pages/ProductionOrderDetailPage').then((m) => ({ default: m.NewProductionOrderPage })));
const EditRedirect = lazy(() => import('./pages/ProductionOrderDetailPage').then((m) => ({ default: m.EditRedirect })));

export const productionRoutes: FeatureRoute[] = [
  { path: 'production-orders', element: <ProductionOrdersPage />, permission: 'inventory' },
  { path: 'production-orders/new', element: <NewProductionOrderPage />, permission: 'inventory' },
  { path: 'production-orders/:id', element: <ProductionOrderDetailPage />, permission: 'inventory' },
  { path: 'production-orders/:id/edit', element: <EditRedirect />, permission: 'inventory' },
];
