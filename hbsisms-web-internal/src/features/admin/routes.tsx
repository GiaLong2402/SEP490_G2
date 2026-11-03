import { lazy } from 'react';
import type { FeatureRoute } from '@/shared/lib/route';

const UsersPage = lazy(() => import('./pages/UsersPage'));
const RolesPage = lazy(() => import('./pages/RolesPage'));
const ConfigPage = lazy(() => import('./pages/ConfigPage'));
const AuditLogPage = lazy(() => import('./pages/AuditLogPage'));

export const adminRoutes: FeatureRoute[] = [
  { path: 'admin/users', element: <UsersPage />, permission: 'admin' },
  { path: 'admin/roles', element: <RolesPage />, permission: 'admin' },
  { path: 'admin/config', element: <ConfigPage />, permission: 'admin' },
  { path: 'admin/audit', element: <AuditLogPage />, permission: 'admin' },
];
