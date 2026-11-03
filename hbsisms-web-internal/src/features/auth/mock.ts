import { fail, mock } from '@/shared/api/mockServer';
import { db } from '@/shared/mock/db';
import { audit } from '@/shared/mock/services';
import type { CurrentUser } from './types';

function toCurrentUser(id: number): CurrentUser {
  const user = db.users.find((u) => u.id === id);
  const role = db.roles.find((r) => r.id === user?.roleId);
  if (!user || !role) fail(401, 'Phiên đăng nhập đã hết hạn');
  return { id: user.id, username: user.username ?? '', fullName: user.fullName, email: user.email, position: user.position, roleCode: role.code, roleName: role.name, permissions: role.permissions };
}

mock('POST', '/auth/login', ({ body }) => {
  const user = db.users.find((u) => u.username === String(body.username).trim().toLowerCase());
  if (!user || user.password !== body.password) fail(400, 'Sai tên đăng nhập hoặc mật khẩu');
  if (!user.isActive) fail(403, 'Tài khoản đã bị khóa');
  user.lastLoginAt = new Date().toISOString();
  audit(user.id, 'login', 'app_user', user.id);
  return { token: `mock-${user.id}`, user: toCurrentUser(user.id) };
});

mock('GET', '/auth/me', ({ userId }) => {
  if (!userId) fail(401, 'Chưa đăng nhập');
  return toCurrentUser(userId);
});

mock('POST', '/auth/logout', () => undefined);
