import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { Permission } from '@/shared/domain/enums';
import type { AppConfig, AppRole, AuditRow, UserInput, UserListItem } from './types';

export const adminApi = {
  users: (query: ListQuery & { roleId?: string }, signal?: AbortSignal) => api.get<Page<UserListItem>>('/admin/users', query, signal),
  createUser: (body: UserInput) => api.post<UserListItem>('/admin/users', body),
  updateUser: (id: ID, body: UserInput) => api.put<UserListItem>(`/admin/users/${id}`, body),
  roles: () => api.get<AppRole[]>('/admin/roles'),
  updateRole: (id: ID, permissions: Permission[]) => api.put<void>(`/admin/roles/${id}`, { permissions }),
  config: () => api.get<AppConfig[]>('/admin/config'),
  updateConfig: (key: string, value: string) => api.put<void>(`/admin/config/${encodeURIComponent(key)}`, { value }),
  audit: (query: ListQuery & { action?: string }, signal?: AbortSignal) => api.get<Page<AuditRow>>('/admin/audit-log', query, signal),
};
