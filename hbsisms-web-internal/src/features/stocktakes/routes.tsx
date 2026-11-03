import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const StocktakesPage = lazy(() => import('./pages/StocktakesPage'));
const StocktakeDetailPage = lazy(() => import('./pages/StocktakeDetailPage'));

export const stocktakeRoutes: FeatureRoute[] = [
  { path: 'stocktakes', element: <StocktakesPage />, permission: 'inventory' },
  { path: 'stocktakes/:id', element: <StocktakeDetailPage />, permission: 'inventory' },
];
