import { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Pencil, Plus } from 'lucide-react';
import { useAction } from '@/shared/api/useAction';
import { dateTime } from '@/shared/lib/format';
import { Button, Card, Checkbox, Chip, DataTable, Field, FilterBar, FilterSelect, IconButton, Input, Modal, PageHeader, Pagination, SearchBox, Select, useListParams, type Column } from '@/shared/ui';
import { adminApi } from '../api';
import type { UserListItem } from '../types';

const empty = { username: '', fullName: '', email: '', phone: '', roleId: '', position: '', employeeCode: '', isActive: true, password: '' };

export default function UsersPage() {
  const list = useListParams(['roleId'] as const);
  const query = { q: list.q, page: list.page, roleId: list.filters.roleId };
  const { data, isLoading, error, refetch } = useQuery({ queryKey: ['admin-users', query], queryFn: ({ signal }) => adminApi.users(query, signal), placeholderData: keepPreviousData });
  const roles = useQuery({ queryKey: ['admin-roles'], queryFn: adminApi.roles }).data ?? [];
  const [editing, setEditing] = useState<UserListItem | 'new' | null>(null);
  const [form, setForm] = useState(empty);
  const [touched, setTouched] = useState(false);
  const isNew = editing === 'new';

  const openForm = (user: UserListItem | 'new') => {
    setTouched(false);
    setEditing(user);
    setForm(user === 'new' ? empty : { username: user.username ?? '', fullName: user.fullName, email: user.email ?? '', phone: user.phone ?? '', roleId: String(user.roleId ?? ''), position: user.position ?? '', employeeCode: user.employeeCode ?? '', isActive: user.isActive, password: '' });
  };
  const errors = {
    username: !form.username.trim() ? 'Bắt buộc' : undefined,
    fullName: !form.fullName.trim() ? 'Bắt buộc' : undefined,
    phone: !form.phone.trim() ? 'Bắt buộc (đăng nhập OTP)' : undefined,
    roleId: !form.roleId ? 'Vui lòng chọn vai trò' : undefined,
    password: form.password && form.password.length < 8 ? 'Tối thiểu 8 ký tự' : (isNew && !form.password ? 'Tối thiểu 8 ký tự' : undefined),
  };
  const save = useAction(
    () => {
      const body = {
        username: form.username.trim(),
        fullName: form.fullName.trim(),
        email: form.email.trim() || null,
        phone: form.phone.trim() || null,
        roleId: Number(form.roleId),
        position: form.position.trim() || null,
        employeeCode: form.employeeCode.trim() || null,
        isActive: form.isActive,
      };
      const password = form.password.trim();
      if (isNew) {
        return adminApi.createUser({ ...body, password });
      }
      return adminApi.updateUser(
        (editing as UserListItem).id,
        password ? { ...body, password } : body,
      );
    },
    { success: 'Đã lưu tài khoản', onDone: () => setEditing(null) },
  );

  const columns: Column<UserListItem>[] = [
    { key: 'code', header: 'Mã NV', hideOnMobile: true, cell: (u) => <span className="num">{u.employeeCode ?? '—'}</span> },
    { key: 'name', header: 'Họ tên', cell: (u) => <div><div className="font-medium">{u.fullName}</div><div className="text-ink-faint">{u.username}</div></div> },
    { key: 'email', header: 'Email', hideOnMobile: true, cell: (u) => u.email ?? '—' },
    { key: 'role', header: 'Vai trò', cell: (u) => u.roleName ?? '—' },
    { key: 'position', header: 'Chức vụ', hideOnMobile: true, cell: (u) => u.position ?? '—' },
    { key: 'login', header: 'Đăng nhập gần nhất', hideOnMobile: true, cell: (u) => dateTime(u.lastLoginAt) },
    { key: 'active', header: 'Trạng thái', cell: (u) => <Chip tone={u.isActive ? 'green' : 'neutral'}>{u.isActive ? 'Hoạt động' : 'Đã khóa'}</Chip> },
    { key: 'edit', header: '', align: 'right', cell: (u) => <IconButton label={`Sửa ${u.fullName}`} onClick={() => openForm(u)}><Pencil size={14} /></IconButton> },
  ];

  return (
    <>
      <PageHeader title="Người dùng" subtitle="Tài khoản nhân viên và vai trò" actions={<Button variant="primary" icon={<Plus size={15} />} onClick={() => openForm('new')}>Thêm tài khoản</Button>} />
      <Card flush>
        <FilterBar>
          <SearchBox value={list.q} onChange={list.setQ} placeholder="Tìm tên, tài khoản, email..." />
          <FilterSelect label="Vai trò" value={list.filters.roleId} onChange={(v) => list.setFilter('roleId', v)}>{roles.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}</FilterSelect>
        </FilterBar>
        <DataTable columns={columns} rows={data?.items} rowKey={(u) => u.id} loading={isLoading} error={error} onRetry={refetch} emptyText="Không có tài khoản phù hợp" />
        {data && <Pagination page={data.page} pageSize={data.pageSize} total={data.total} onChange={list.setPage} />}
      </Card>
      <Modal
        open={!!editing}
        title={isNew ? 'Thêm tài khoản' : 'Sửa tài khoản'}
        width="lg"
        onClose={() => setEditing(null)}
        onSubmit={() => {
          setTouched(true);
          if (!Object.values(errors).some(Boolean)) save.mutate();
        }}
        footer={<><Button onClick={() => setEditing(null)}>Hủy bỏ</Button><Button type="submit" variant="primary" loading={save.isPending}>Lưu</Button></>}
      >
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Họ tên" required error={touched ? errors.fullName : undefined}><Input value={form.fullName} onChange={(e) => setForm({ ...form, fullName: e.target.value })} /></Field>
          <Field label="Tên đăng nhập" required error={touched ? errors.username : undefined}><Input autoComplete="off" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })} /></Field>
          <Field label="Email"><Input type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></Field>
          <Field label="Điện thoại" required hint="SĐT đăng nhập OTP" error={touched ? errors.phone : undefined}><Input value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} placeholder="090xxxxxxx" /></Field>
          <Field label="Vai trò" required error={touched ? errors.roleId : undefined}>
            <Select value={form.roleId} onChange={(e) => setForm({ ...form, roleId: e.target.value })}>
              <option value="">Chọn...</option>
              {roles.map((r) => <option key={r.id} value={r.id}>{r.name}</option>)}
            </Select>
          </Field>
          <Field label="Chức vụ" hint="Chức vụ “Giao hàng” sẽ có trong danh sách phân công giao"><Input value={form.position} onChange={(e) => setForm({ ...form, position: e.target.value })} /></Field>
          <Field label="Mã nhân viên"><Input value={form.employeeCode} onChange={(e) => setForm({ ...form, employeeCode: e.target.value })} /></Field>
          <Field
            label={isNew ? 'Mật khẩu ban đầu' : 'Đặt lại mật khẩu'}
            required={isNew}
            hint={isNew ? undefined : 'Để trống nếu giữ mật khẩu cũ'}
            error={touched ? errors.password : undefined}
          >
            <Input
              type="password"
              autoComplete="new-password"
              value={form.password}
              onChange={(e) => setForm({ ...form, password: e.target.value })}
              placeholder={isNew ? undefined : 'Nhập mật khẩu mới…'}
            />
          </Field>
          <div className="sm:col-span-2"><Checkbox label="Đang hoạt động" checked={form.isActive} onChange={(e) => setForm({ ...form, isActive: e.target.checked })} /></div>
        </div>
      </Modal>
    </>
  );
}
