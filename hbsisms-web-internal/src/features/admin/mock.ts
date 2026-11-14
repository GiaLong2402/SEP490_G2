import { fail, matches, mock, paginate } from '@/shared/api/mockServer';
import { db, nextId } from '@/shared/mock/db';
import { audit, userName } from '@/shared/mock/services';

const publicUser = ({ password: _password, ...u }: (typeof db.users)[number]) => ({ ...u, roleName: db.roles.find((r) => r.id === u.roleId)?.name ?? null });

function checkUnique(body: any, id: number | null) {
  const username = String(body.username ?? '').trim().toLowerCase();
  if (!username) fail(400, 'Vui lòng nhập tên đăng nhập');
  if (db.users.some((u) => u.username === username && u.id !== id)) fail(409, `Tên đăng nhập ${username} đã tồn tại`);
  if (body.email && db.users.some((u) => u.email === body.email && u.id !== id)) fail(409, `Email ${body.email} đã tồn tại`);
  return username;
}

mock('GET', '/admin/users', ({ query }) => {
  const rows = db.users.filter((u) => matches(query.q, u.fullName, u.username, u.email, u.employeeCode)).filter((u) => !query.roleId || u.roleId === Number(query.roleId));
  return paginate(rows.map(publicUser), query);
});

mock('POST', '/admin/users', ({ body, userId }) => {
  const now = new Date().toISOString();
  const row = { ...body, username: checkUnique(body, null), id: nextId(db.users), avatarUrl: null, userType: 'staff' as const, hiredAt: null, lastLoginAt: null, createdAt: now, updatedAt: now, password: body.password };
  db.users.push(row);
  audit(userId, 'create', 'app_user', row.id);
  return publicUser(row);
});

mock('PUT', '/admin/users/:id', ({ params, body, userId }) => {
  const row = db.users.find((u) => u.id === Number(params.id));
  if (!row) fail(404, 'Không tìm thấy người dùng');
  if (row.id === userId && (!body.isActive || body.roleId !== row.roleId)) fail(409, 'Không thể tự khóa hoặc đổi vai trò của chính mình');
  const { password, ...rest } = body;
  Object.assign(row, rest, { username: checkUnique(body, row.id), updatedAt: new Date().toISOString() });
  if (password != null && String(password).trim()) {
    if (String(password).trim().length < 8) fail(400, 'Mật khẩu phải từ 8 ký tự');
    row.password = String(password).trim();
  }
  audit(userId, 'update', 'app_user', row.id);
  return publicUser(row);
});

mock('GET', '/admin/roles', () => db.roles.filter((r) => r.code !== 'customer'));

mock('PUT', '/admin/roles/:id', ({ params, body, userId }) => {
  const role = db.roles.find((r) => r.id === Number(params.id));
  if (!role) fail(404, 'Không tìm thấy vai trò');
  if (role.code === 'admin' && !body.permissions.includes('admin')) fail(409, 'Vai trò Quản trị phải giữ quyền quản trị');
  role.permissions = body.permissions;
  audit(userId, 'update', 'app_role', role.id, { permissions: body.permissions });
});

mock('GET', '/admin/config', () => db.config);

mock('PUT', '/admin/config/:key', ({ params, body, userId }) => {
  const row = db.config.find((c) => c.key === params.key);
  if (!row) fail(404, 'Không tìm thấy cấu hình');
  if (row.valueType === 'int' && (!/^\d+$/.test(String(body.value)))) fail(400, 'Giá trị phải là số nguyên không âm');
  row.value = String(body.value);
  row.updatedBy = userId;
  row.updatedAt = new Date().toISOString();
  audit(userId, 'update', 'app_config', null, { key: row.key, value: row.value });
});

mock('GET', '/admin/audit-log', ({ query }) => {
  const rows = db.auditLog
    .map((a) => ({ ...a, userName: userName(a.userId) }))
    .filter((a) => matches(query.q, a.userName, a.entityType, a.action))
    .filter((a) => !query.action || a.action === query.action)
    .sort((a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id);
  return paginate(rows, query);
});
