import { Suspense, useEffect, useState, type ReactNode } from 'react';
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
    <nav aria-label="Điều hướng chính" className="px-2 py-2">
      {navigation.map((group, index) => {
        const items = group.items.filter((item) => can(item.permission));
        if (items.length === 0) return null;
        return (
          <div
            key={group.label ?? index}
            className={`mt-3 first:mt-0 ${collapsed && index > 0 ? 'border-t border-white/10 pt-3' : ''}`}
          >
            {group.label && !collapsed && (
              <div className="mb-1.5 px-2.5 text-micro font-semibold uppercase tracking-[0.08em] text-white/45">
                {group.label}
              </div>
            )}
            <ul className="space-y-0.5">
              {items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.to === '/'}
                    title={collapsed ? item.label : undefined}
                    aria-label={collapsed ? item.label : undefined}
                    className={({ isActive }) =>
                      [
                        'group/nav flex h-9 items-center gap-2.5 rounded-md px-2.5 text-body transition-colors focus-visible:outline-white',
                        collapsed ? 'justify-center px-0' : '',
                        isActive
                          ? 'bg-sidebar-active font-semibold text-white shadow-sm'
                          : 'text-white/75 hover:bg-white/10 hover:text-white',
                      ].join(' ')
                    }
                  >
                    <item.icon size={16} aria-hidden className="shrink-0 opacity-90" />
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

function Brand({
  collapsed,
  trailing,
}: {
  collapsed: boolean;
  trailing?: ReactNode;
}) {
  return (
    <div
      className={`flex shrink-0 items-center gap-2 border-b border-white/10 text-white ${
        collapsed ? 'h-auto flex-col justify-center gap-1.5 px-2 py-2.5' : 'h-[52px] px-3'
      }`}
    >
      <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary shadow-sm">
        <Fish size={16} aria-hidden />
      </span>
      {!collapsed && (
        <div className="min-w-0 flex-1 leading-tight">
          <div className="truncate text-h2">Hải sản Hòa Bình</div>
          <div className="truncate text-micro text-white/45">Quản lý nội bộ</div>
        </div>
      )}
      {trailing}
    </div>
  );
}

function SidebarShell({
  collapsed,
  onToggle,
  mobile,
  onClose,
}: {
  collapsed: boolean;
  onToggle?: () => void;
  mobile?: boolean;
  onClose?: () => void;
}) {
  const showCollapsed = !mobile && collapsed;
  const trailing = mobile ? (
    <IconButton
      label="Đóng menu"
      className="shrink-0 text-white hover:bg-white/10 hover:text-white"
      onClick={onClose}
    >
      <X size={18} />
    </IconButton>
  ) : onToggle ? (
    <IconButton
      label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'}
      aria-expanded={!collapsed}
      className="shrink-0 text-white/75 hover:bg-white/10 hover:text-white"
      onClick={onToggle}
    >
      {collapsed ? <PanelLeftOpen size={17} /> : <PanelLeftClose size={17} />}
    </IconButton>
  ) : undefined;

  return (
    <div className="flex h-full min-h-0 flex-col">
      <Brand collapsed={showCollapsed} trailing={trailing} />

      <div className="sidebar-scroll relative min-h-0 flex-1 overflow-y-auto overflow-x-hidden overscroll-contain">
        <SidebarContent collapsed={showCollapsed} />
        <div
          aria-hidden
          className="pointer-events-none sticky bottom-0 h-6 bg-gradient-to-t from-[var(--sidebar)] to-transparent"
        />
      </div>
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
    <div className="flex h-full min-h-0">
      <aside
        className={`hidden h-full min-h-0 shrink-0 flex-col bg-sidebar transition-[width] duration-200 ease-out md:flex ${
          collapsed ? 'w-[64px]' : 'w-[240px]'
        }`}
      >
        <SidebarShell collapsed={collapsed} onToggle={() => setCollapsed((value) => !value)} />
      </aside>

      {mobileOpen && (
        <div className="fixed inset-0 z-40 md:hidden">
          <button
            type="button"
            aria-label="Đóng menu"
            className="absolute inset-0 bg-[var(--overlay)]"
            onClick={() => setMobileOpen(false)}
          />
          <aside className="relative flex h-full w-[272px] max-w-[85vw] flex-col bg-sidebar shadow-xl">
            <SidebarShell collapsed={false} mobile onClose={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-h-0 min-w-0 flex-1 flex-col">
        <header className="z-20 flex h-[52px] shrink-0 items-center gap-2 border-b border-line bg-card px-3">
          <IconButton label="Mở menu" className="md:hidden" onClick={() => setMobileOpen(true)}>
            <Menu size={18} />
          </IconButton>
          {isMockMode && (
            <span className="rounded-full bg-amber-bg px-2 py-0.5 text-micro font-semibold text-amber-fg">
              Dữ liệu demo
            </span>
          )}
          <div className="ml-auto flex items-center gap-1.5">
            <NotificationBell />
            <div className="ml-1 flex items-center gap-2 border-l border-line pl-3">
              <span
                aria-hidden
                className="flex h-8 w-8 items-center justify-center rounded-full bg-primary-soft text-small font-semibold text-primary"
              >
                {initials}
              </span>
              <div className="hidden leading-tight sm:block">
                <div className="text-small font-semibold">{user?.fullName}</div>
                <div className="text-micro font-normal text-ink-muted">{user?.position ?? user?.roleName}</div>
              </div>
              <IconButton label="Đăng xuất" onClick={logout}>
                <LogOut size={16} />
              </IconButton>
            </div>
          </div>
        </header>
        <main className="min-h-0 min-w-0 flex-1 overflow-y-auto p-4">
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
