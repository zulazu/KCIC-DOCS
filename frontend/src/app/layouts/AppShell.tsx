import { Suspense, useEffect, useState } from 'react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../../features/auth/AuthContext';
import { NotificationBell } from '../../features/notifications/NotificationBell';
import { LoadingBlock } from '../../shared/components/Feedback';
import { Icon } from '../../shared/components/Icon';
import { cn } from '../../shared/utils/cn';
import { initials } from '../../shared/utils/format';
import { activeNavLabel } from '../navigation';
import { DesktopSidebar, MobileDrawer } from './Sidebar';

const STORAGE_KEY = 'kcic.sidebar.collapsed';

function initialCollapsed(): boolean {
  const stored = window.localStorage.getItem(STORAGE_KEY);
  if (stored === 'true' || stored === 'false') {
    return stored === 'true';
  }
  return window.innerWidth < 1280;
}

export function AppShell() {
  const { me, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [collapsed, setCollapsed] = useState(initialCollapsed);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);

  useEffect(() => {
    window.localStorage.setItem(STORAGE_KEY, String(collapsed));
  }, [collapsed]);

  if (!me) {
    return null;
  }

  const handleLogout = async () => {
    setLoggingOut(true);
    try {
      await logout();
      navigate('/login', { replace: true });
    } finally {
      setLoggingOut(false);
    }
  };

  return (
    <div className="min-h-dvh bg-background">
      <a
        href="#main-content"
        className="sr-only focus:not-sr-only focus:fixed focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-surface focus:px-4 focus:py-2 focus:text-sm focus:font-semibold focus:text-primary focus:shadow-lg"
      >
        Lewati ke konten utama
      </a>

      <DesktopSidebar me={me} collapsed={collapsed} onToggle={() => setCollapsed((v) => !v)} />
      <MobileDrawer me={me} open={drawerOpen} onClose={() => setDrawerOpen(false)} />

      <div
        className={cn(
          'flex min-h-dvh flex-col transition-[padding] duration-200',
          collapsed ? 'lg:pl-[76px]' : 'lg:pl-[264px]',
        )}
      >
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between gap-3 border-b border-line bg-surface px-4 lg:px-8">
          <div className="flex min-w-0 flex-1 items-center gap-3">
            <button
              type="button"
              onClick={() => setDrawerOpen(true)}
              aria-label="Buka navigasi"
              className="shrink-0 rounded-md p-2 text-ink-secondary hover:bg-surface-muted hover:text-ink lg:hidden"
            >
              <Icon name="menu" size={20} />
            </button>
            <nav aria-label="Breadcrumb" className="min-w-0">
              <ol className="flex items-center gap-1.5 text-sm whitespace-nowrap">
                <li className="hidden sm:block">
                  <Link to="/" className="text-ink-secondary hover:text-primary">
                    KCIC Connect
                  </Link>
                </li>
                <li aria-hidden="true" className="hidden text-ink-disabled sm:block">
                  /
                </li>
                <li aria-current="page" className="truncate font-semibold text-ink">
                  {activeNavLabel(location.pathname)}
                </li>
              </ol>
            </nav>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <NotificationBell />
            <div className="hidden h-6 w-px bg-line sm:block" aria-hidden="true" />
            <div className="flex items-center gap-2">
              <span
                className="flex size-8 items-center justify-center rounded-full bg-primary-subtle text-xs font-bold text-primary"
                aria-hidden="true"
              >
                {initials(me.displayName)}
              </span>
              <span className="hidden text-right sm:block">
                <span className="block max-w-[160px] truncate text-xs font-semibold text-ink">
                  {me.displayName}
                </span>
                <span className="block max-w-[160px] truncate text-[11px] text-ink-secondary">
                  {me.username}
                </span>
              </span>
            </div>
            <button
              type="button"
              onClick={() => void handleLogout()}
              disabled={loggingOut}
              aria-label="Keluar dari aplikasi"
              title="Keluar"
              className="rounded-md p-2 text-ink-secondary hover:bg-danger-bg hover:text-danger disabled:opacity-50"
            >
              <Icon name="logout" size={18} />
            </button>
          </div>
        </header>

        <main id="main-content" className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 lg:px-8 lg:py-8">
          <Suspense fallback={<LoadingBlock label="Memuat halaman…" />}>
            <Outlet />
          </Suspense>
        </main>

        <footer className="mx-auto w-full max-w-[1440px] px-4 pb-5 text-[11px] text-ink-secondary lg:px-8">
          <span>KCIC Connect — pilot internal</span>
        </footer>
      </div>
    </div>
  );
}
