import { api, type ListQuery, type Page } from '@/shared/api/client';
import type { ID } from '@/shared/domain/entities';
import type { ApprovalDecision, ApprovalListItem } from './types';

export interface ApprovalQuery extends ListQuery {
  status?: string;
  approvalType?: string;
}

export const approvalsApi = {
  list: (query: ApprovalQuery, signal?: AbortSignal) => api.get<Page<ApprovalListItem>>('/approvals', query, signal),
  decide: (id: ID, body: ApprovalDecision) => api.post<void>(`/approvals/${id}/decision`, body),
};
