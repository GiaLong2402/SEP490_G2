import type { ReactNode } from 'react';
import type { Permission } from '@/shared/domain/enums';

export interface FeatureRoute {
  path: string;
  element: ReactNode;
  /** Có một trong các quyền này là được vào. Bỏ trống: mọi nhân viên. */
  permission?: Permission | Permission[];
}
