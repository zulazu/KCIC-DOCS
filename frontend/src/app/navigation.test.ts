import { describe, expect, it } from 'vitest';
import { activeNavLabel, navTrail, visibleNavigation } from './navigation';

describe('visibleNavigation', () => {
  it('hides administration groups when capabilities are missing', () => {
    const groups = visibleNavigation([]);
    expect(groups).toHaveLength(1);
    expect(groups[0]?.items.map((item) => item.label)).toEqual(['Dashboard']);
  });

  it('shows only administration entries allowed by capability', () => {
    const groups = visibleNavigation(['user.manage', 'organization.manage']);
    const labels = groups.flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'User Management', 'Organization']);
  });

  it('shows workspace and master data entries for their capabilities', () => {
    const groups = visibleNavigation(['workspace.view', 'document.view', 'master_data.manage']);
    const labels = groups.flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'Dokumen', 'Workspaces', 'Master Data']);
  });

  it('shows the document catalog for document.view alone', () => {
    const groups = visibleNavigation(['document.view']);
    const labels = groups.flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'Dokumen']);
  });

  it('shows the employee administration entry for employee.view', () => {
    const groups = visibleNavigation(['employee.view']);
    const labels = groups.flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'Employee', 'Migrasi Akun']);
  });

  it('shows the template catalog for template.view', () => {
    const groups = visibleNavigation(['document.view', 'template.view']);
    const labels = groups.flatMap((group) => group.items.map((item) => item.label));
    expect(labels).toEqual(['Dashboard', 'Dokumen', 'Template']);
  });

  it('keeps the three organization submenus under organization.manage', () => {
    const groups = visibleNavigation(['organization.manage']);
    const organization = groups
      .flatMap((group) => group.items)
      .find((item) => item.to === '/admin/organization');
    expect(organization?.children?.map((child) => child.label)).toEqual([
      'Direktorat',
      'Departemen',
      'Biro/Division',
    ]);
  });
});

describe('activeNavLabel', () => {
  it('matches nested administration routes and prefers the deepest item', () => {
    expect(activeNavLabel('/admin/users/123')).toBe('User Management');
    expect(activeNavLabel('/admin/roles/abc')).toBe('Authorization');
    expect(activeNavLabel('/admin/employees/123')).toBe('Employee');
    expect(activeNavLabel('/admin/migration')).toBe('Migrasi Akun');
    expect(activeNavLabel('/admin/master-data')).toBe('Master Data');
    expect(activeNavLabel('/workspaces/123')).toBe('Workspaces');
    expect(activeNavLabel('/documents/123')).toBe('Dokumen');
    expect(activeNavLabel('/templates/123')).toBe('Template');
    expect(activeNavLabel('/')).toBe('Dashboard');
    expect(activeNavLabel('/notifications')).toBe('Notifikasi');
    expect(activeNavLabel('/profile')).toBe('Profil saya');
    expect(activeNavLabel('/unknown')).toBe('Workspace');
  });

  it('labels organization submenus and unit detail routes', () => {
    expect(activeNavLabel('/admin/organization')).toBe('Organization');
    expect(activeNavLabel('/admin/organization/directorates')).toBe('Direktorat');
    expect(activeNavLabel('/admin/organization/departments')).toBe('Departemen');
    expect(activeNavLabel('/admin/organization/divisions')).toBe('Biro/Division');
    expect(activeNavLabel('/admin/organization/units/123')).toBe('Organization');
  });
});

describe('navTrail', () => {
  it('returns parent and child for organization submenu routes', () => {
    expect(navTrail('/admin/organization/departments')).toEqual([
      { label: 'Organization', to: '/admin/organization' },
      { label: 'Departemen', to: '/admin/organization/departments' },
    ]);
  });

  it('returns only the parent for unit detail routes', () => {
    expect(navTrail('/admin/organization/units/abc')).toEqual([
      { label: 'Organization', to: '/admin/organization' },
    ]);
  });

  it('returns a single crumb for flat routes and empty for unknown routes', () => {
    expect(navTrail('/admin/users/123')).toEqual([
      { label: 'User Management', to: '/admin/users' },
    ]);
    expect(navTrail('/unknown')).toEqual([]);
  });
});
