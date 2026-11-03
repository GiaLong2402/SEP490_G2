import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const ProductsPage = lazy(() => import('./pages/ProductsPage'));
const ProductDetailPage = lazy(() => import('./pages/ProductDetailPage'));
const CategoriesPage = lazy(() => import('./pages/CategoriesPage'));

export const productRoutes: FeatureRoute[] = [
  { path: 'products', element: <ProductsPage />, permission: 'products' },
  { path: 'products/:id', element: <ProductDetailPage />, permission: 'products' },
  { path: 'categories', element: <CategoriesPage />, permission: 'products' },
];
