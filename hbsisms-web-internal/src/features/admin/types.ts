import type { AppConfig, AppRole, AppUser, AuditLog, ID } from '@/shared/domain/entities';

export interface UserListItem extends AppUser {
  roleName: string | null;
}

export interface UserInput {
  username: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  roleId: ID;
  position: string | null;
  employeeCode: string | null;
  isActive: boolean;
  /** Bắt buộc khi tạo; khi sửa — có giá trị = đặt lại mật khẩu. */
  password?: string;
}

export interface AuditRow extends AuditLog {
  userName: string | null;
}

export type { AppConfig, AppRole };
