import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, useLocation } from 'react-router-dom';
import type { MeResponse } from '../../shared/api/types';
import { Icon } from '../../shared/components/Icon';
import { cn } from '../../shared/utils/cn';
import { initials, roleLabel } from '../../shared/utils/format';
import { visibleNavigation } from '../../app/navigation';
import type { NavItem } from '../../app/navigation';

function SidebarToggle({ collapsed, onToggle }: { collapsed: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      aria-label={collapsed ? 'Perluas sidebar' : 'Ciutkan sidebar'}
      aria-expanded={!collapsed}
      title={collapsed ? 'Perluas sidebar' : 'Ciutkan sidebar'}
      className="flex size-9 shrink-0 items-center justify-center rounded-lg text-white/75 hover:bg-white/10 hover:text-white"
    >
      <Icon name="menu" size={18} />
    </button>
  );
}

function BrandMark({
  collapsed,
  onToggle,
}: {
  collapsed: boolean;
  onToggle?: () => void;
}) {
  return (
    <div
      className={cn(
        'border-b border-white/15',
        collapsed ? 'flex flex-col items-center gap-2 px-3 py-3' : 'flex h-16 items-center gap-3 px-4',
      )}
    >
      {collapsed && onToggle ? <SidebarToggle collapsed={collapsed} onToggle={onToggle} /> : null}
      <span
        className="flex size-9 shrink-0 items-center justify-center rounded-lg border border-gold/60 bg-primary-pressed text-sm font-bold tracking-wide text-gold"
        aria-hidden="true"
      >
        KC
      </span>
      {!collapsed ? (
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-bold tracking-[0.08em] text-white">KCIC DOCS</p>
          <p className="truncate text-[11px] text-white/75">Workspace dokumen</p>
        </div>
      ) : null}
      {!collapsed && onToggle ? <SidebarToggle collapsed={collapsed} onToggle={onToggle} /> : null}
    </div>
  );
}

function NavLeaf({
  item,
  collapsed,
  onNavigate,
  sub = false,
}: {
  item: NavItem;
  collapsed: boolean;
  onNavigate?: () => void;
  sub?: boolean;
}) {
  return (
    <NavLink
      to={item.to}
      end={item.end}
      onClick={onNavigate}
      title={collapsed ? item.label : undefined}
      className={({ isActive }) =>
        cn(
          'relative flex items-center gap-3 rounded-lg font-medium transition-colors duration-150',
          sub ? 'h-8 pl-9 pr-3 text-[13px]' : 'h-9 px-3 text-sm',
          collapsed && 'justify-center px-0',
          isActive
            ? 'bg-white/15 text-white'
            : 'text-white/80 hover:bg-white/10 hover:text-white',
        )
      }
    >
      {({ isActive }) => (
        <>
          {isActive && !sub ? (
            <span
              aria-hidden="true"
              className="absolute top-2 left-0 h-6 w-[3px] rounded-r-full bg-gold"
            />
          ) : null}
          {sub ? (
            <span
              aria-hidden="true"
              className={cn(
                'absolute left-[22px] size-1.5 rounded-full',
                isActive ? 'bg-gold' : 'bg-white/35',
              )}
            />
          ) : (
            <Icon name={item.icon} size={18} className="shrink-0" />
          )}
          {collapsed ? (
            <span className="sr-only">{item.label}</span>
          ) : (
            <span className="truncate">{item.label}</span>
          )}
        </>
      )}
    </NavLink>
  );
}

function NavBranch({
  item,
  collapsed,
  onNavigate,
}: {
  item: NavItem;
  collapsed: boolean;
  onNavigate?: () => void;
}) {
  const location = useLocation();
  const childActive = (item.children ?? []).some(
    (child) => location.pathname === child.to || location.pathname.startsWith(`${child.to}/`),
  );
  const [manualOpen, setManualOpen] = useState<boolean | null>(null);
  const open = manualOpen ?? childActive;
  const listId = `nav-sub-${item.to.replace(/\W+/g, '-')}`;

  if (collapsed) {
    return <NavLeaf item={item} collapsed onNavigate={onNavigate} />;
  }

  return (
    <div>
      <div className="flex items-center gap-1">
        <div className="min-w-0 flex-1">
          <NavLeaf item={item} collapsed={false} onNavigate={onNavigate} />
        </div>
        <button
          type="button"
          onClick={() => setManualOpen(!open)}
          aria-expanded={open}
          aria-controls={listId}
          aria-label={`${open ? 'Tutup' : 'Buka'} submenu ${item.label}`}
          className="flex size-7 shrink-0 items-center justify-center rounded-md text-white/70 hover:bg-white/10 hover:text-white"
        >
          <Icon
            name="chevron-down"
            size={15}
            className={cn('transition-transform duration-150', open && 'rotate-180')}
          />
        </button>
      </div>
      {open ? (
        <ul id={listId} className="mt-0.5 space-y-0.5">
          {(item.children ?? []).map((child) => (
            <li key={child.to}>
              <NavLeaf item={child} collapsed={false} onNavigate={onNavigate} sub />
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  );
}

function NavigationLinks({
  me,
  collapsed,
  onNavigate,
}: {
  me: MeResponse;
  collapsed: boolean;
  onNavigate?: () => void;
}) {
  const groups = visibleNavigation(me.capabilities);

  return (
    <nav aria-label="Navigasi utama" className="flex-1 overflow-y-auto px-3 py-4 sidebar-scroll">
      {groups.map((group, index) => (
        <div key={group.label ?? `group-${index}`} className={index > 0 ? 'mt-4' : undefined}>
          {group.label ? (
            collapsed ? (
              <div className="mx-2 mb-2 border-t border-white/15" aria-hidden="true" />
            ) : (
              <p className="mb-2 px-2 text-[11px] font-semibold tracking-[0.08em] text-white/65">
                {group.label}
              </p>
            )
          ) : null}
          <ul className="space-y-1">
            {group.items.map((item) => (
              <li key={item.to}>
                {item.children && item.children.length > 0 ? (
                  <NavBranch item={item} collapsed={collapsed} onNavigate={onNavigate} />
                ) : (
                  <NavLeaf item={item} collapsed={collapsed} onNavigate={onNavigate} />
                )}
              </li>
            ))}
          </ul>
        </div>
      ))}
    </nav>
  );
}

function UserFooter({ me, collapsed }: { me: MeResponse; collapsed: boolean }) {
  return (
    <div className="border-t border-white/15 p-3">
      <Link
        to="/profile"
        title="Lihat profil saya"
        className={cn(
          'flex items-center gap-2.5 rounded-lg px-2 py-2 hover:bg-white/10',
          collapsed && 'justify-center px-0',
        )}
      >
        <span
          className="flex size-8 shrink-0 items-center justify-center rounded-full bg-gold-soft text-xs font-bold text-gold-foreground"
          aria-hidden="true"
        >
          {initials(me.displayName)}
        </span>
        {!collapsed ? (
          <div className="min-w-0">
            <p className="truncate text-xs font-semibold text-white">{me.displayName}</p>
            <p className="truncate text-[11px] text-white/70">
              {me.roles.length > 0 ? roleLabel(me.roles[0] ?? '') : 'Tanpa role'}
            </p>
          </div>
        ) : (
          <span className="sr-only">Lihat profil saya</span>
        )}
      </Link>
    </div>
  );
}

export function DesktopSidebar({
  me,
  collapsed,
  onToggle,
}: {
  me: MeResponse;
  collapsed: boolean;
  onToggle: () => void;
}) {
  return (
    <aside
      aria-label="Sidebar aplikasi"
      className={cn(
        'fixed inset-y-0 left-0 z-30 hidden flex-col bg-primary lg:flex',
        'transition-[width] duration-200',
        collapsed ? 'w-[76px]' : 'w-[264px]',
      )}
    >
      <BrandMark collapsed={collapsed} onToggle={onToggle} />
      <NavigationLinks me={me} collapsed={collapsed} />
      <UserFooter me={me} collapsed={collapsed} />
    </aside>
  );
}

export function MobileDrawer({
  me,
  open,
  onClose,
}: {
  me: MeResponse;
  open: boolean;
  onClose: () => void;
}) {
  const ref = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) {
      return;
    }
    if (open && !dialog.open) {
      dialog.showModal();
    } else if (!open && dialog.open) {
      dialog.close();
    }
  }, [open]);

  return (
    <dialog
      ref={ref}
      aria-label="Navigasi aplikasi"
      onClose={onClose}
      className="m-0 h-dvh max-h-none w-[280px] max-w-[85vw] rounded-none bg-primary p-0 text-white backdrop:bg-ink/50 lg:hidden"
    >
      <div className="flex h-full flex-col">
        <div className="relative">
          <BrandMark collapsed={false} />
          <button
            type="button"
            onClick={onClose}
            aria-label="Tutup navigasi"
            className="absolute top-1/2 right-3 -translate-y-1/2 rounded-md p-2 text-white/75 hover:bg-white/10 hover:text-white"
          >
            <Icon name="close" size={18} />
          </button>
        </div>
        <NavigationLinks me={me} collapsed={false} onNavigate={onClose} />
        <UserFooter me={me} collapsed={false} />
      </div>
    </dialog>
  );
}
