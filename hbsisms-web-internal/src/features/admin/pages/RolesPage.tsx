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

  return (
    <>
      <PageHeader title="Vai trò và phân quyền" subtitle="Mỗi nhân viên có một vai trò" actions={<Button variant="primary" disabled={!dirtyIds.length} loading={save.isPending} onClick={() => save.mutate()}>Lưu thay đổi</Button>} />
      <div className="mb-4"><Alert tone="info">Thay đổi có hiệu lực từ lần đăng nhập kế tiếp của nhân viên. Vai trò Quản trị luôn giữ quyền quản trị.</Alert></div>
      <Card flush>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[720px] text-small">
            <thead>
              <tr className="bg-thead text-micro font-semibold uppercase tracking-wide text-ink-muted">
                <th scope="col" className="h-[34px] px-3 text-left">Vai trò</th>
                {PERMISSIONS.map((p) => <th key={p} scope="col" className="px-2 text-center">{permissionLabels[p]}</th>)}
              </tr>
            </thead>
            <tbody>
              {roles.map((role) => (
                <tr key={role.id} className="border-t border-line">
                  <th scope="row" className="h-[42px] px-3 text-left font-medium">{role.name}<div className="num font-normal text-ink-faint">{role.code}</div></th>
                  {PERMISSIONS.map((p) => (
                    <td key={p} className="text-center">
                      <input
                        type="checkbox"
                        aria-label={`${role.name}: ${permissionLabels[p]}`}
                        className="h-4 w-4 accent-[var(--primary)]"
                        disabled={role.code === 'admin' && p === 'admin'}
                        checked={draft[role.id]?.includes(p) ?? false}
                        onChange={() => toggle(role.id, p)}
                      />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
