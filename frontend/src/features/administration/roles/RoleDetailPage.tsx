import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../../shared/api/client';
import type { Permission, Role } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button } from '../../../shared/components/Button';
import { Alert, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { Icon } from '../../../shared/components/Icon';
import { Card, CardHeader, PageHeader } from '../../../shared/components/Surface';
import { useToast } from '../../../shared/components/Toast';
import { useAuth } from '../../auth/AuthContext';
import { listPermissions, listRoles, setRolePermissions } from '../api';

export function RoleDetailPage() {
  const { roleId = '' } = useParams();
  const rolesQuery = useQuery({ queryKey: ['roles'], queryFn: listRoles });
  const permissionsQuery = useQuery({ queryKey: ['permissions'], queryFn: listPermissions });

  const role = rolesQuery.data?.find((item) => item.id === roleId);

  if (rolesQuery.isPending || permissionsQuery.isPending) {
    return <LoadingBlock label="Memuat role dan permission…" />;
  }

  if (rolesQuery.isError || permissionsQuery.isError) {
    return (
      <ErrorState
        message={apiMessage(rolesQuery.error ?? permissionsQuery.error)}
        onRetry={() => {
          void rolesQuery.refetch();
          void permissionsQuery.refetch();
        }}
      />
    );
  }

  if (!role) {
    return (
      <ErrorState
        title="Role tidak ditemukan"
        message="Role mungkin telah dihapus atau Anda tidak memiliki akses."
      />
    );
  }

  return (
    <PermissionEditor
      key={role.id}
      role={role}
      permissions={permissionsQuery.data ?? []}
    />
  );
}

function PermissionEditor({ role, permissions }: { role: Role; permissions: Permission[] }) {
  const queryClient = useQueryClient();
  const { refresh } = useAuth();
  const { notify } = useToast();
  const [selected, setSelected] = useState<Set<string>>(() => new Set(role.permissions));
  const [actionError, setActionError] = useState<string | null>(null);

  const grouped = useMemo(() => {
    const groups = new Map<string, Permission[]>();
    for (const permission of permissions) {
      const list = groups.get(permission.moduleCode) ?? [];
      list.push(permission);
      groups.set(permission.moduleCode, list);
    }
    return [...groups.entries()].sort(([a], [b]) => a.localeCompare(b));
  }, [permissions]);

  const saveMutation = useMutation({
    mutationFn: () =>
      setRolePermissions(
        role.id,
        permissions
          .filter((permission) => selected.has(permission.permissionCode))
          .map((permission) => permission.id),
      ),
    onSuccess: (updated: Role) => {
      notify(`Permission role ${updated.roleName} disimpan.`);
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['roles'] });
      void refresh();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const locked = role.systemRole;

  const toggle = (code: string) => {
    if (locked) {
      return;
    }
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(code)) {
        next.delete(code);
      } else {
        next.add(code);
      }
      return next;
    });
  };

  const toggleModule = (modulePermissions: Permission[], checked: boolean) => {
    if (locked) {
      return;
    }
    setSelected((current) => {
      const next = new Set(current);
      for (const permission of modulePermissions) {
        if (checked) {
          next.add(permission.permissionCode);
        } else {
          next.delete(permission.permissionCode);
        }
      }
      return next;
    });
  };

  const dirty =
    role.permissions.length !== selected.size ||
    role.permissions.some((code) => !selected.has(code));

  return (
    <>
      <PageHeader
        title={role.roleName}
        description={role.description ?? 'Role tanpa deskripsi.'}
        meta={
          <>
            <span className="font-mono text-xs text-ink-secondary">{role.roleCode}</span>
            <StatusBadge status={role.status} label={role.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'} />
            {role.systemRole ? <Badge tone="gold">Role sistem</Badge> : null}
            <span className="text-xs text-ink-secondary">
              {role.permissions.length} dari {permissions.length} permission
            </span>
          </>
        }
        action={
          <Link
            to="/admin/roles"
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-line-strong bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            <Icon name="arrow-left" size={16} />
            Kembali
          </Link>
        }
      />

      {locked ? (
        <div className="mb-4">
          <Alert tone="warning" title="Role sistem dilindungi">
            Permission role sistem dikunci untuk mencegah administrator mencabut akses
            fundamental secara tidak sengaja.
          </Alert>
        </div>
      ) : null}

      {actionError ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <Card>
        <CardHeader
          title="Permission"
          description="Permission menentukan capability yang dihitung server pada setiap request."
        />
        <div className="divide-y divide-line">
          {grouped.map(([moduleCode, modulePermissions]) => {
            const allChecked = modulePermissions.every((permission) =>
              selected.has(permission.permissionCode),
            );
            return (
              <section key={moduleCode} className="px-5 py-4">
                <div className="mb-3 flex items-center justify-between gap-3">
                  <h3 className="text-sm font-semibold text-ink">{humanizeModule(moduleCode)}</h3>
                  <label className="flex items-center gap-2 text-xs text-ink-secondary">
                    <input
                      type="checkbox"
                      checked={allChecked}
                      disabled={locked}
                      onChange={(event) => toggleModule(modulePermissions, event.target.checked)}
                      className="size-4 accent-primary"
                    />
                    Pilih semua modul
                  </label>
                </div>
                <div className="grid gap-2 sm:grid-cols-2 xl:grid-cols-3">
                  {modulePermissions.map((permission) => {
                    const checked = selected.has(permission.permissionCode);
                    return (
                      <label
                        key={permission.id}
                        className={
                          checked
                            ? 'flex cursor-pointer items-start gap-2.5 rounded-lg border border-primary/35 bg-primary-subtle/60 px-3 py-2.5'
                            : 'flex cursor-pointer items-start gap-2.5 rounded-lg border border-line px-3 py-2.5 hover:bg-background'
                        }
                      >
                        <input
                          type="checkbox"
                          checked={checked}
                          disabled={locked}
                          onChange={() => toggle(permission.permissionCode)}
                          className="mt-0.5 size-4 accent-primary"
                        />
                        <span>
                          <span className="block text-xs font-semibold text-ink">
                            {permission.permissionName}
                          </span>
                          <span className="block font-mono text-[11px] text-ink-secondary">
                            {permission.permissionCode}
                          </span>
                          {permission.description ? (
                            <span className="mt-0.5 block text-[11px] text-ink-secondary">
                              {permission.description}
                            </span>
                          ) : null}
                        </span>
                      </label>
                    );
                  })}
                </div>
              </section>
            );
          })}
        </div>
        <div className="flex flex-wrap items-center justify-between gap-3 border-t border-line bg-background px-5 py-3">
          <p className="text-xs text-ink-secondary" aria-live="polite">
            {selected.size} permission dipilih{dirty ? ' · perubahan belum disimpan' : ''}
          </p>
          <div className="flex gap-2">
            <Button
              variant="secondary"
              disabled={!dirty || locked}
              onClick={() => setSelected(new Set(role.permissions))}
            >
              Kembalikan
            </Button>
            <Button
              loading={saveMutation.isPending}
              disabled={!dirty || locked}
              onClick={() => saveMutation.mutate()}
            >
              Simpan permission
            </Button>
          </div>
        </div>
      </Card>
    </>
  );
}

function humanizeModule(code: string): string {
  return code
    .split('_')
    .map((part) => part.charAt(0) + part.slice(1).toLowerCase())
    .join(' ');
}

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}
