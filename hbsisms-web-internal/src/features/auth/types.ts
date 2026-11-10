import type { ID } from '@/shared/domain/entities';
import type { Permission } from '@/shared/domain/enums';

export interface CurrentUser {
  id: ID;
  username: string;
  fullName: string;
  email: string | null;
  position: string | null;
  roleCode: string;
  roleName: string;
  permissions: Permission[];
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface OtpLoginRequest {
  idToken: string;
}

export interface LoginResponse {
  token: string;
  user: CurrentUser;
}
