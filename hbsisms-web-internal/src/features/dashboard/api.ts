import { api } from '@/shared/api/client';
import type { DashboardSummary, DashboardTasks } from './types';

export const dashboardApi = {
  summary: () => api.get<DashboardSummary>('/dashboard/summary'),
  tasks: () => api.get<DashboardTasks>('/dashboard/tasks'),
};
