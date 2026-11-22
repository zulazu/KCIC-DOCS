import type { IconName } from '../shared/components/Icon';

export interface NavItem {
  label: string;
  to: string;
  icon: IconName;
  capability?: string;
  capabilities?: string[];
  end?: boolean;
}

export interface NavGroup {
  label: string | null;
  items: NavItem[];
}

export const navigation: NavGroup[] = [
  {
    label: null,
    items: [{ label: 'Dashboard', to: '/', icon: 'home', end: true }],
  },
  {
    label: 'Workspace',
    items: [
      {
        label: 'Dokumen',
        to: '/documents',
        icon: 'document',
        capability: 'document.view',
      },
      {
        label: 'Workspaces',
        to: '/workspaces',
        icon: 'folder',
        capability: 'workspace.view',
      },
    ],
  },
  {
    label: 'Requests',
    items: [
      {
        label: 'Requests',
        to: '/requests',
        icon: 'document',
        capabilities: [
          'request.view_own',
          'request.view_assigned',
          'request.approve',
          'request.fulfill',
        ],
      },
    ],
  },
  {
    label: 'Distribution',
    items: [
      {
        label: 'Distribusi',
        to: '/distributions',
        icon: 'link',
        capabilities: ['distribution.view_own', 'distribution.view_received'],
      },
    ],
  },
  {
    label: 'Templates',
    items: [
      {
        label: 'Template',
        to: '/templates',
        icon: 'template',
        capability: 'template.view',
      },
    ],
  },
  {
    label: 'Administration',
    items: [
      {
        label: 'User Management',
        to: '/admin/users',
        icon: 'users',
        capability: 'user.manage',
      },
      {
        label: 'Employee',
        to: '/admin/employees',
        icon: 'users',
        capability: 'employee.view',
      },
      {
        label: 'Migrasi Akun',
        to: '/admin/migration',
        icon: 'restore',
        capability: 'employee.view',
      },
      {
        label: 'Authorization',
        to: '/admin/roles',
        icon: 'shield',
        capability: 'role.manage',
      },
      {
        label: 'Organization',
        to: '/admin/organization',
        icon: 'building',
        capability: 'organization.manage',
      },
      {
        label: 'Master Data',
        to: '/admin/master-data',
        icon: 'document',
        capability: 'master_data.manage',
      },
      {
        label: 'Audit Trail',
        to: '/admin/audit',
        icon: 'shield',
        capability: 'audit.view',
      },
    ],
  },
];

export function visibleNavigation(capabilities: string[]): NavGroup[] {
  return navigation
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => {
        if (!item.capability && !item.capabilities) {
          return true;
        }
        if (item.capability && capabilities.includes(item.capability)) {
          return true;
        }
        return item.capabilities?.some((capability) => capabilities.includes(capability)) ?? false;
      }),
    }))
    .filter((group) => group.items.length > 0);
}

export function activeNavLabel(pathname: string): string {
  if (pathname === '/notifications') {
    return 'Notifikasi';
  }
  const items = navigation.flatMap((group) => group.items);
  const matches = items.filter((item) =>
    item.end ? pathname === item.to : pathname === item.to || pathname.startsWith(`${item.to}/`),
  );
  return matches.sort((a, b) => b.to.length - a.to.length)[0]?.label ?? 'Workspace';
}
