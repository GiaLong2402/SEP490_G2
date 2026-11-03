import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const QuotesPage = lazy(() => import('./pages/QuotesPage'));
const QuoteFormPage = lazy(() => import('./pages/QuoteFormPage'));
const QuoteDetailPage = lazy(() => import('./pages/QuoteDetailPage'));

export const quoteRoutes: FeatureRoute[] = [
  { path: 'quotes', element: <QuotesPage />, permission: 'orders' },
  { path: 'quotes/new', element: <QuoteFormPage />, permission: 'orders' },
  { path: 'quotes/:id', element: <QuoteDetailPage />, permission: 'orders' },
];
