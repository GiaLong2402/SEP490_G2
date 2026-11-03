import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { ShieldX } from 'lucide-react';
import { Navigate, useLocation } from 'react-router-dom';
import { setUnauthorizedHandler, tokenStore } from '@/shared/api/client';
import type { Permission } from '@/shared/domain/enums';
import { LinkButton, PageLoading } from '@/shared/ui';
import { authApi } from './api';
import type { CurrentUser, LoginRequest } from './types';

interface AuthValue {
  user: CurrentUser | null;
  loading: boolean;
  login: (body: LoginRequest) => Promise<void>;
  logout: () => void;
  can: (permission?: Permission | Permission[]) => boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(!!tokenStore.get());

  const clearSession = useCallback(() => {
    tokenStore.set(null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  useEffect(() => {
    setUnauthorizedHandler(clearSession);
    if (!tokenStore.get()) return;
    authApi
      .me()
      .then(setUser)
      .catch(() => tokenStore.set(null))
      .finally(() => setLoading(false));
  }, [clearSession]);

  const value = useMemo<AuthValue>(
    () => ({
      user,
      loading,
      login: async (body) => {
        const result = await authApi.login(body);
        tokenStore.set(result.token);
        setUser(result.user);
      },
      logout: () => {
        authApi.logout().catch(() => {});
        clearSession();
      },
      can: (permission) => {
        if (!permission) return true;
        const needed = Array.isArray(permission) ? permission : [permission];
        return needed.some((p) => user?.permissions.includes(p));
      },
    }),
    [user, loading, clearSession],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth phải nằm trong AuthProvider');
  return ctx;
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { user, loading } = useAuth();
  const location = useLocation();
  if (loading) {
    return (
      <div className="p-6">
        <PageLoading />
      </div>
    );
  }
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  return <>{children}</>;
}

export function RequirePermission({ permission, children }: { permission?: Permission | Permission[]; children: ReactNode }) {
  const { can } = useAuth();
  if (can(permission)) return <>{children}</>;
  return (
    <div className="mx-auto mt-16 max-w-sm text-center">
      <ShieldX size={32} className="mx-auto mb-3 text-ink-faint" aria-hidden />
      <h1 className="text-h1">Bạn không có quyền truy cập</h1>
      <p className="mb-4 mt-1 text-ink-muted">Liên hệ quản trị viên nếu bạn cần dùng chức năng này.</p>
      <LinkButton to="/">Về trang tổng quan</LinkButton>
    </div>
  );
}
