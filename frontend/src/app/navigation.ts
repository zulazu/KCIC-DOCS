import type { IconName } from '../shared/components/Icon';

export interface NavItem {
  label: string;
  to: string;
  icon: IconName;
  capability?: string;
  capabilities?: string[];
  end?: boolean;
  children?: NavItem[];
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
        end: true,
        children: [
          {
            label: 'Direktorat',
            to: '/admin/organization/directorates',
            icon: 'building',
            capability: 'organization.manage',
          },
          {
            label: 'Departemen',
            to: '/admin/organization/departments',
            icon: 'building',
            capability: 'organization.manage',
          },
          {
            label: 'Biro/Division',
            to: '/admin/organization/divisions',
            icon: 'building',
            capability: 'organization.manage',
          },
          {
            label: 'Jabatan',
            to: '/admin/organization/positions',
            icon: 'users',
            capability: 'master_data.manage',
          },
        ],
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

function itemVisible(item: NavItem, capabilities: string[]): boolean {
  if (!item.capability && !item.capabilities) {
    return true;
  }
  if (item.capability && capabilities.includes(item.capability)) {
    return true;
  }
  return item.capabilities?.some((capability) => capabilities.includes(capability)) ?? false;
}

export function visibleNavigation(capabilities: string[]): NavGroup[] {
  return navigation
    .map((group) => ({
      ...group,
      items: group.items
        .filter((item) => itemVisible(item, capabilities))
        .map((item) =>
          item.children
            ? {
                ...item,
                children: item.children.filter((child) => itemVisible(child, capabilities)),
              }
            : item,
        ),
    }))
    .filter((group) => group.items.length > 0);
}

function allNavItems(): NavItem[] {
  return navigation.flatMap((group) => group.items);
}

function matchesPath(item: NavItem, pathname: string): boolean {
  if (item.end && !item.children) {
    return pathname === item.to;
  }
  return pathname === item.to || pathname.startsWith(`${item.to}/`);
}

export function activeNavLabel(pathname: string): string {
  if (pathname === '/notifications') {
    return 'Notifikasi';
  }
  if (pathname === '/profile') {
    return 'Profil saya';
  }
  const items = allNavItems().flatMap((item) => [item, ...(item.children ?? [])]);
  const matches = items.filter((item) => matchesPath(item, pathname));
  return matches.sort((a, b) => b.to.length - a.to.length)[0]?.label ?? 'Workspace';
}

export function navTrail(pathname: string): { label: string; to: string }[] {
  const items = allNavItems();
  const parents = items.filter(
    (item) => pathname === item.to || pathname.startsWith(`${item.to}/`),
  );
  const parent = parents.sort((a, b) => b.to.length - a.to.length)[0];
  if (!parent) {
    return [];
  }
  const child = (parent.children ?? [])
    .filter((item) => matchesPath(item, pathname))
    .sort((a, b) => b.to.length - a.to.length)[0];
  const trail = [{ label: parent.label, to: parent.to }];
  if (child) {
    trail.push({ label: child.label, to: child.to });
  }
  return trail;
}
