import { Suspense, useEffect, useState } from 'react';
import { Fish, LogOut, Menu, PanelLeftClose, PanelLeftOpen, X } from 'lucide-react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '@/features/auth';
import { NotificationBell } from '@/features/notifications';
import { isMockMode } from '@/shared/api/config';
import { IconButton, PageLoading } from '@/shared/ui';
import { navigation } from './navigation';

const COLLAPSE_KEY = 'hbsisms.sidebar.collapsed';

function readCollapsed() {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1';
  } catch {
    return false;
  }
}

function SidebarContent({ collapsed }: { collapsed: boolean }) {
  const { can } = useAuth();
  return (
    <nav aria-label="Điều hướng chính" className="flex-1 overflow-y-auto px-2 pb-4">
      {navigation.map((group, index) => {
        const items = group.items.filter((item) => can(item.permission));
        if (items.length === 0) return null;
        return (
          <div key={group.label ?? index} className="mt-3 first:mt-1">
            {group.label && !collapsed && <div className="mb-1 px-2.5 text-micro uppercase tracking-wider text-white/50">{group.label}</div>}
            <ul className="space-y-0.5">
              {items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/'}
                    title={collapsed ? item.label : undefined}
                    aria-label={collapsed ? item.label : undefined}
                    className={({ isActive }) => `flex h-[34px] items-center gap-2.5 rounded-md px-2.5 text-body focus-visible:outline-white ${collapsed ? 'justify-center' : ''} ${isActive ? 'bg-sidebar-active font-semibold text-white' : 'text-white/80 hover:bg-white/10 hover:text-white'}`}
                  >
                    <item.icon size={16} aria-hidden className="shrink-0" />
                    {!collapsed && <span className="truncate">{item.label}</span>}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        );
      })}
    </nav>
  );
}

function Brand({ collapsed }: { collapsed: boolean }) {
  return (
    <div className={`flex h-[52px] shrink-0 items-center gap-2.5 px-3.5 text-white ${collapsed ? 'justify-center' : ''}`}>
      <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-md bg-primary"><Fish size={16} aria-hidden /></span>
      {!collapsed && <span className="truncate text-h2">Hải sản Hòa Bình</span>}
    </div>
  );
}

export function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(readCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);

  useEffect(() => setMobileOpen(false), [location.pathname]);
  useEffect(() => {
    try {
      localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0');
    } catch {
      // Chỉ là tiện ích theo người dùng, bỏ qua nếu storage bị chặn.
    }
  }, [collapsed]);

  const initials = user?.fullName.split(' ').slice(-2).map((word) => word[0]).join('') ?? '';

  return (
    <div className="flex h-full">
      <aside className={`hidden shrink-0 flex-col bg-sidebar md:flex ${collapsed ? 'w-[60px]' : 'w-[232px]'}`}>
        <Brand collapsed={collapsed} />
        <SidebarContent collapsed={collapsed} />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <button type="button" aria-label="Đóng menu" className="absolute inset-0 bg-[var(--overlay)]" onClick={() => setMobileOpen(false)} />
          <aside className="relative flex h-full w-[260px] flex-col bg-sidebar">
            <div className="flex items-center justify-between pr-2">
              <Brand collapsed={false} />
              <IconButton label="Đóng menu" className="text-white hover:bg-white/10 hover:text-white" onClick={() => setMobileOpen(false)}><X size={18} /></IconButton>
            </div>
            <SidebarContent collapsed={false} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="z-20 flex h-[52px] shrink-0 items-center gap-2 border-b border-line bg-card px-3">
          <IconButton label="Mở menu" className="md:hidden" onClick={() => setMobileOpen(true)}><Menu size={18} /></IconButton>
          <IconButton label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'} className="hidden md:inline-flex" onClick={() => setCollapsed(!collapsed)}>
            {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
          </IconButton>
          {isMockMode && <span className="rounded-full bg-amber-bg px-2 py-0.5 text-micro font-semibold text-amber-fg">Dữ liệu demo</span>}
          <div className="ml-auto flex items-center gap-1.5">
            <NotificationBell />
            <div className="ml-1 flex items-center gap-2 border-l border-line pl-3">
              <span aria-hidden className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-soft text-small font-semibold text-primary">{initials}</span>
              <div className="hidden leading-tight sm:block">
                <div className="text-small font-semibold">{user?.fullName}</div>
                <div className="text-micro font-normal text-ink-muted">{user?.position ?? user?.roleName}</div>
              </div>
              <IconButton label="Đăng xuất" onClick={logout}><LogOut size={16} /></IconButton>
            </div>
          </div>
        </header>
        <main className="min-w-0 flex-1 overflow-y-auto p-4">
          <div className="mx-auto max-w-[1440px]">
            <Suspense fallback={<PageLoading />}>
              <Outlet />
            </Suspense>
          </div>
        </main>
      </div>
    </div>
  );
}
