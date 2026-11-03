import type { ApprovalTask, ID } from '@/shared/domain/entities';

export interface ApprovalListItem extends ApprovalTask {
  requesterName: string | null;
  deciderName: string | null;
  /** Đường dẫn tới chứng từ gốc. */
  refLink: string | null;
}

export interface ApprovalDecision {
  decision: 'approved' | 'rejected';
  note: string;
}

export type { ID };
