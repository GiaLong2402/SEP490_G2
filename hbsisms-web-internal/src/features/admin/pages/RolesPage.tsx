import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useAction } from '@/shared/api/useAction';
import { permissionLabels, type Permission } from '@/shared/domain/enums';
import { Alert, Button, Card, ErrorState, PageHeader, PageLoading } from '@/shared/ui';
import { adminApi } from '../api';

const PERMISSIONS = Object.keys(permissionLabels) as Permission[];

export default function RolesPage() {
  const { data: roles, isLoading, error, refetch } = useQuery({ queryKey: ['admin-roles'], queryFn: adminApi.roles });
  const [draft, setDraft] = useState<Record<number, Permission[]>>({});
  useEffect(() => {
    if (roles) setDraft(Object.fromEntries(roles.map((r) => [r.id, r.permissions])));
  }, [roles]);

  const dirtyIds = roles?.filter((r) => [...(draft[r.id] ?? [])].sort().join() !== [...r.permissions].sort().join()).map((r) => r.id) ?? [];
  const save = useAction(async () => { await Promise.all(dirtyIds.map((id) => adminApi.updateRole(id, draft[id]))); }, { success: 'Đã lưu phân quyền' });

  if (isLoading) return <PageLoading />;
  if (error || !roles) return <ErrorState error={error} onRetry={refetch} />;

  const toggle = (roleId: number, permission: Permission) =>
    setDraft((prev) => {
      const current = prev[roleId] ?? [];
      return { ...prev, [roleId]: current.includes(permission) ? current.filter((p) => p !== permission) : [...current, permission] };
    });

  const minTableWidth = Math.max(480, 180 + roles.length * 120);

  return (
    <>
      <PageHeader
        title="Vai trò và phân quyền"
        subtitle="Hàng là quyền · cột là vai trò — thêm quyền mới chỉ thêm một hàng"
        actions={<Button variant="primary" disabled={!dirtyIds.length} loading={save.isPending} onClick={() => save.mutate()}>Lưu thay đổi</Button>}
      />
      <div className="mb-4">
        <Alert tone="info">Thay đổi có hiệu lực từ lần đăng nhập kế tiếp của nhân viên. Vai trò Quản trị luôn giữ quyền quản trị.</Alert>
      </div>
      <Card flush>
        <div className="overflow-x-auto">
          <table className="w-full text-small" style={{ minWidth: minTableWidth }}>
            <thead>
              <tr className="bg-thead text-micro font-semibold uppercase tracking-wide text-ink-muted">
                <th scope="col" className="sticky left-0 z-10 h-[42px] bg-thead px-3 text-left shadow-[1px_0_0_var(--line)]">
                  Quyền
                </th>
                {roles.map((role) => (
                  <th key={role.id} scope="col" className="min-w-[110px] px-2 text-center font-semibold normal-case tracking-normal text-ink">
                    <div>{role.name}</div>
                    <div className="num mt-0.5 font-normal normal-case text-ink-faint">{role.code}</div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {PERMISSIONS.map((p) => (
                <tr key={p} className="border-t border-line">
                  <th scope="row" className="sticky left-0 z-10 h-[42px] bg-card px-3 text-left font-medium shadow-[1px_0_0_var(--line)]">
                    {permissionLabels[p]}
                    <div className="num font-normal text-ink-faint">{p}</div>
                  </th>
                  {roles.map((role) => {
                    const locked = role.code === 'admin' && p === 'admin';
                    return (
                      <td key={role.id} className="text-center">
                        <input
                          type="checkbox"
                          aria-label={`${role.name}: ${permissionLabels[p]}`}
                          className="h-4 w-4 accent-[var(--primary)] disabled:opacity-50"
                          disabled={locked}
                          checked={draft[role.id]?.includes(p) ?? false}
                          onChange={() => toggle(role.id, p)}
                        />
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
