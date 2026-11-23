import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { Role, RoleHolderView, UnitType } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button } from '../../../shared/components/Button';
import { ConfirmDialog, Dialog } from '../../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField } from '../../../shared/components/Field';
import { Card, CardHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { formatDate } from '../../../shared/utils/format';
import { useAuth } from '../../auth/AuthContext';
import { listWorkspaces } from '../../workspaces/api';
import { assignRole, listActiveUnits, listRoleHolders, listUsers, revokeRoleAssignment } from '../api';
import { typeLabel } from '../organization/unitLabels';

const assignSchema = z
  .object({
    userId: z.string().min(1, 'Pengguna wajib dipilih.'),
    scopeType: z.enum(['GLOBAL', 'ORGANIZATION_UNIT', 'WORKSPACE']),
    organizationUnitId: z.string(),
    workspaceId: z.string(),
    includeDescendants: z.boolean(),
    validUntil: z.string(),
  })
  .refine((values) => values.scopeType !== 'ORGANIZATION_UNIT' || values.organizationUnitId.length > 0, {
    path: ['organizationUnitId'],
    message: 'Pilih unit untuk scope unit organisasi.',
  })
  .refine((values) => values.scopeType !== 'WORKSPACE' || values.workspaceId.length > 0, {
    path: ['workspaceId'],
    message: 'Pilih workspace untuk scope workspace.',
  });
type AssignForm = z.infer<typeof assignSchema>;

const UNIT_TYPE_ORDER: UnitType[] = ['COMPANY', 'DIRECTORATE', 'DEPARTMENT', 'DIVISION'];

function toIsoDate(value: string): string | null {
  return value ? new Date(`${value}T23:59:59Z`).toISOString() : null;
}

export function RoleAssignmentsCard({ role }: { role: Role }) {
  const queryClient = useQueryClient();
  const { refresh } = useAuth();
  const { notify } = useToast();
  const [assignOpen, setAssignOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<RoleHolderView | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const holdersQuery = useQuery({
    queryKey: ['role-holders', role.id],
    queryFn: () => listRoleHolders(role.id),
  });
  const usersQuery = useQuery({
    queryKey: ['admin-users', 'active-picker'],
    queryFn: () => listUsers({ page: 0, size: 100, status: 'ACTIVE' }),
    enabled: assignOpen,
  });
  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'active'],
    queryFn: listActiveUnits,
    enabled: assignOpen,
  });
  const workspacesQuery = useQuery({
    queryKey: ['workspaces', 'picker'],
    queryFn: () => listWorkspaces(false),
    enabled: assignOpen,
  });

  const form = useForm<AssignForm>({
    resolver: zodResolver(assignSchema),
    defaultValues: {
      userId: '',
      scopeType: 'ORGANIZATION_UNIT',
      organizationUnitId: '',
      workspaceId: '',
      includeDescendants: false,
      validUntil: '',
    },
  });
  const scopeType = form.watch('scopeType');

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['role-holders', role.id] });
    void queryClient.invalidateQueries({ queryKey: ['role-assignments'] });
    void refresh();
  };

  const assignMutation = useMutation({
    mutationFn: (values: AssignForm) =>
      assignRole(values.userId, {
        roleCode: role.roleCode,
        scopeType: values.scopeType,
        organizationUnitId: values.scopeType === 'ORGANIZATION_UNIT' ? values.organizationUnitId : null,
        workspaceId: values.scopeType === 'WORKSPACE' ? values.workspaceId : null,
        includeDescendants: values.scopeType === 'ORGANIZATION_UNIT' && values.includeDescendants,
        validUntil: toIsoDate(values.validUntil),
      }),
    onSuccess: () => {
      notify(`Assignment role ${role.roleName} ditambahkan.`);
      setActionError(null);
      setAssignOpen(false);
      form.reset();
      invalidate();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const revokeMutation = useMutation({
    mutationFn: (assignmentId: string) => revokeRoleAssignment(assignmentId),
    onSuccess: () => {
      notify('Assignment role dicabut.');
      setActionError(null);
      setRevokeTarget(null);
      invalidate();
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      setRevokeTarget(null);
    },
  });

  const holders = holdersQuery.data ?? [];
  const activeCount = holders.filter((holder) => holder.status === 'ACTIVE').length;

  return (
    <Card>
      <CardHeader
        title="Assignment & scope akses"
        description="Pemegang role beserta batas aksesnya. Scope unit membatasi peran (mis. PIC) per Direktorat, Departemen, atau Biro/Division."
        action={
          <Button onClick={() => setAssignOpen(true)} disabled={role.status !== 'ACTIVE'}>
            Tambah assignment
          </Button>
        }
      />

      {actionError ? (
        <div className="px-5 pt-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}
      {role.status !== 'ACTIVE' ? (
        <div className="px-5 pt-4">
          <Alert tone="warning">
            Role nonaktif tidak dapat menerima assignment baru. Assignment lama tetap tercatat sebagai riwayat.
          </Alert>
        </div>
      ) : null}

      {holdersQuery.isPending ? (
        <LoadingBlock label="Memuat assignment role…" />
      ) : holdersQuery.isError ? (
        <ErrorState message={apiMessage(holdersQuery.error)} onRetry={() => void holdersQuery.refetch()} />
      ) : holders.length === 0 ? (
        <EmptyState
          title="Belum ada pemegang role"
          description="Tugaskan role ini ke pengguna dengan scope global, unit organisasi, atau workspace."
        />
      ) : (
        <>
          <Table caption={`Daftar pemegang role ${role.roleName}`}>
            <THead>
              <Th>Pengguna</Th>
              <Th>Scope</Th>
              <Th>Turunan</Th>
              <Th>Berlaku sampai</Th>
              <Th>Status</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {holders.map((holder) => (
                <Tr key={holder.id}>
                  <Td>
                    <Link
                      to={`/admin/users/${holder.userId}`}
                      className="font-semibold text-ink hover:text-primary"
                    >
                      {holder.displayName}
                    </Link>
                    <span className="block font-mono text-xs text-ink-secondary">{holder.username}</span>
                  </Td>
                  <Td>
                    <ScopeCell holder={holder} />
                  </Td>
                  <Td>
                    {holder.scopeType === 'ORGANIZATION_UNIT' ? (
                      holder.includeDescendants ? (
                        <Badge tone="info">Termasuk turunan</Badge>
                      ) : (
                        <Badge tone="neutral">Hanya unit ini</Badge>
                      )
                    ) : (
                      <span className="text-xs text-ink-muted">—</span>
                    )}
                  </Td>
                  <Td>
                    {holder.validUntil ? (
                      <span className="text-sm">{formatDate(holder.validUntil)}</span>
                    ) : (
                      <span className="text-xs text-ink-muted">Tanpa batas</span>
                    )}
                  </Td>
                  <Td>
                    <StatusBadge
                      status={holder.status}
                      label={holder.status === 'ACTIVE' ? 'Aktif' : holder.status === 'REVOKED' ? 'Dicabut' : undefined}
                    />
                  </Td>
                  <Td className="text-right">
                    {holder.status === 'ACTIVE' ? (
                      <Button variant="danger" size="compact" onClick={() => setRevokeTarget(holder)}>
                        Cabut
                      </Button>
                    ) : null}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <p className="border-t border-line bg-background px-5 py-3 text-xs text-ink-secondary">
            {activeCount} assignment aktif · {holders.length - activeCount} riwayat dicabut
          </p>
        </>
      )}

      <Dialog
        open={assignOpen}
        title={`Tugaskan role ${role.roleName}`}
        description="Tentukan batas akses: global, per unit organisasi, atau per workspace."
        size="md"
        onClose={() => setAssignOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAssignOpen(false)}>
              Batal
            </Button>
            <Button
              loading={assignMutation.isPending}
              onClick={() => void form.handleSubmit((values) => assignMutation.mutate(values))()}
            >
              Tugaskan role
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4" onSubmit={(event) => event.preventDefault()}>
          <SelectField
            label="Pengguna"
            required
            error={form.formState.errors.userId?.message}
            helper={
              usersQuery.data && usersQuery.data.totalItems > usersQuery.data.items.length
                ? 'Menampilkan 100 akun aktif pertama.'
                : undefined
            }
            {...form.register('userId')}
          >
            <option value="">Pilih pengguna</option>
            {usersQuery.data?.items.map((user) => (
              <option key={user.id} value={user.id}>
                {user.displayName} ({user.username})
              </option>
            ))}
          </SelectField>
          <SelectField label="Scope" required {...form.register('scopeType')}>
            <option value="ORGANIZATION_UNIT">Unit organisasi</option>
            <option value="WORKSPACE">Workspace</option>
            <option value="GLOBAL">Global</option>
          </SelectField>
          {scopeType === 'ORGANIZATION_UNIT' ? (
            <>
              <SelectField
                label="Unit"
                required
                error={form.formState.errors.organizationUnitId?.message}
                {...form.register('organizationUnitId')}
              >
                <option value="">Pilih unit</option>
                {[...UNIT_TYPE_ORDER, null].map((type) => {
                  const units = unitsQuery.data?.filter((unit) => unit.unitType === type) ?? [];
                  if (units.length === 0) {
                    return null;
                  }
                  return (
                    <optgroup key={type ?? 'UNTYPED'} label={typeLabel(type)}>
                      {units.map((unit) => (
                        <option key={unit.id} value={unit.id}>
                          {unit.unitName} ({unit.unitCode})
                        </option>
                      ))}
                    </optgroup>
                  );
                })}
              </SelectField>
              <label className="flex items-start gap-2 rounded-lg border border-line px-3 py-2 text-sm text-ink-secondary">
                <input
                  type="checkbox"
                  className="mt-0.5 size-4 rounded border-line-strong accent-primary"
                  {...form.register('includeDescendants')}
                />
                <span>
                  Berlaku juga untuk unit turunan
                  <span className="mt-0.5 block text-xs text-ink-muted">
                    Contoh: PIC pada Direktorat dengan opsi ini mencakup seluruh Departemen dan
                    Biro/Division di bawahnya. Tanpa opsi ini, akses hanya pada unit terpilih.
                  </span>
                </span>
              </label>
            </>
          ) : null}
          {scopeType === 'WORKSPACE' ? (
            <SelectField
              label="Workspace"
              required
              error={form.formState.errors.workspaceId?.message}
              {...form.register('workspaceId')}
            >
              <option value="">Pilih workspace</option>
              {workspacesQuery.data?.map((workspace) => (
                <option key={workspace.id} value={workspace.id}>
                  {workspace.workspaceName} ({workspace.workspaceCode})
                </option>
              ))}
            </SelectField>
          ) : null}
          {scopeType === 'GLOBAL' ? (
            <Alert tone="warning">
              Scope global memberi capability role ini di seluruh organisasi. Gunakan hanya untuk
              peran governance.
            </Alert>
          ) : null}
          <InputField
            label="Berlaku sampai"
            type="date"
            helper="Kosongkan bila tanpa batas waktu."
            {...form.register('validUntil')}
          />
        </form>
      </Dialog>

      <ConfirmDialog
        open={revokeTarget !== null}
        title="Cabut assignment role"
        message={
          revokeTarget
            ? `Cabut role ${role.roleName} dari ${revokeTarget.displayName}? Akses yang berasal dari assignment ini berhenti berlaku; grant dari sumber lain tidak terpengaruh.`
            : ''
        }
        confirmLabel="Cabut assignment"
        loading={revokeMutation.isPending}
        onConfirm={() => revokeTarget && revokeMutation.mutate(revokeTarget.id)}
        onClose={() => setRevokeTarget(null)}
      />
    </Card>
  );
}

function ScopeCell({ holder }: { holder: RoleHolderView }) {
  if (holder.scopeType === 'GLOBAL') {
    return <Badge tone="gold">Global</Badge>;
  }
  if (holder.scopeType === 'WORKSPACE') {
    return (
      <span>
        <span className="block text-sm font-semibold text-ink">
          {holder.workspaceName ?? 'Workspace tidak ditemukan'}
        </span>
        <span className="block text-xs text-ink-secondary">Workspace</span>
      </span>
    );
  }
  return (
    <span>
      <span className="block text-sm font-semibold text-ink">
        {holder.organizationUnitName ?? 'Unit tidak ditemukan'}
      </span>
      <span className="block text-xs text-ink-secondary">
        {typeLabel(holder.organizationUnitType)}
        {holder.organizationUnitCode ? (
          <span className="font-mono"> · {holder.organizationUnitCode}</span>
        ) : null}
      </span>
    </span>
  );
}

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}