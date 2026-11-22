import { describe, expect, it } from 'vitest';
import { activeNavLabel, visibleNavigation } from './navigation';

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
    expect(activeNavLabel('/unknown')).toBe('Workspace');
  });
});
