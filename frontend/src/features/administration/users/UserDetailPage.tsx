import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { AccountStatus } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button } from '../../../shared/components/Button';
import { ConfirmDialog, Dialog } from '../../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField } from '../../../shared/components/Field';
import { Icon } from '../../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../../shared/components/Surface';
import { useToast } from '../../../shared/components/Toast';
import { formatDateTime, formatDate } from '../../../shared/utils/format';
import { useAuth, useCapability } from '../../auth/AuthContext';
import {
  assignRole,
  assignUnit,
  changeUserStatus,
  endUnitAssignment,
  getUser,
  listActiveUnits,
  listRoleAssignments,
  listRoles,
  resetUserPassword,
  revokeRoleAssignment,
  unlockUser,
  updateUser,
} from '../api';

const profileSchema = z.object({
  displayName: z.string().trim().min(1, 'Nama tampilan wajib diisi.').max(160),
  email: z.union([z.literal(''), z.email('Format email tidak valid.')]),
});
type ProfileForm = z.infer<typeof profileSchema>;

const resetSchema = z.object({
  temporaryPassword: z.string().min(12, 'Password sementara minimal 12 karakter.'),
});
type ResetForm = z.infer<typeof resetSchema>;

const assignUnitSchema = z.object({
  organizationUnitId: z.string().min(1, 'Unit wajib dipilih.'),
  assignmentType: z.enum(['PRIMARY', 'SECONDARY']),
  validUntil: z.string(),
});
type AssignUnitForm = z.infer<typeof assignUnitSchema>;

const assignRoleSchema = z
  .object({
    roleCode: z.string().min(1, 'Role wajib dipilih.'),
    scopeType: z.enum(['GLOBAL', 'ORGANIZATION_UNIT']),
    organizationUnitId: z.string(),
    includeDescendants: z.boolean(),
    validUntil: z.string(),
  })
  .refine(
    (values) => values.scopeType !== 'ORGANIZATION_UNIT' || values.organizationUnitId.length > 0,
    { path: ['organizationUnitId'], message: 'Pilih unit untuk scope unit organisasi.' },
  );
type AssignRoleForm = z.infer<typeof assignRoleSchema>;

const statusLabels: Record<AccountStatus, string> = {
  ACTIVE: 'Aktif',
  INACTIVE: 'Nonaktif',
  LOCKED: 'Terkunci',
};

function toIsoDate(value: string): string | null {
  return value ? new Date(`${value}T23:59:59Z`).toISOString() : null;
}

export function UserDetailPage() {
  const { userId = '' } = useParams();
  const { me, refresh } = useAuth();
  const canManageRoles = useCapability('role.manage');
  const canManageOrg = useCapability('organization.manage');
  const queryClient = useQueryClient();
  const { notify } = useToast();

  const [statusConfirm, setStatusConfirm] = useState<AccountStatus | null>(null);
  const [resetOpen, setResetOpen] = useState(false);
  const [assignUnitOpen, setAssignUnitOpen] = useState(false);
  const [assignRoleOpen, setAssignRoleOpen] = useState(false);
  const [endAssignment, setEndAssignment] = useState<{ id: string; label: string } | null>(null);
  const [revokeTarget, setRevokeTarget] = useState<{ id: string; label: string } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const detailQuery = useQuery({
    queryKey: ['admin-user', userId],
    queryFn: () => getUser(userId),
    enabled: Boolean(userId),
  });

  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'active'],
    queryFn: listActiveUnits,
  });

  const rolesQuery = useQuery({
    queryKey: ['roles'],
    queryFn: listRoles,
    enabled: canManageRoles,
  });

  const roleAssignmentsQuery = useQuery({
    queryKey: ['role-assignments', userId],
    queryFn: () => listRoleAssignments(userId),
    enabled: canManageRoles && Boolean(userId),
  });

  const detail = detailQuery.data;
  const isSelf = me?.userId === userId;

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-user', userId] });
    void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    void queryClient.invalidateQueries({ queryKey: ['role-assignments', userId] });
    if (isSelf) {
      void refresh();
    }
  };

  const profileForm = useForm<ProfileForm>({
    resolver: zodResolver(profileSchema),
    values: detail
      ? { displayName: detail.user.displayName, email: detail.user.email ?? '' }
      : { displayName: '', email: '' },
  });
  const [profileError, setProfileError] = useState<string | null>(null);

  const profileMutation = useMutation({
    mutationFn: (values: ProfileForm) =>
      updateUser(userId, { displayName: values.displayName, email: values.email || undefined }),
    onSuccess: () => {
      notify('Profil pengguna diperbarui.');
      setProfileError(null);
      invalidate();
    },
    onError: (error) => setProfileError(apiMessage(error)),
  });

  const statusMutation = useMutation({
    mutationFn: (status: AccountStatus) => changeUserStatus(userId, status),
    onSuccess: (updated) => {
      notify(
        updated.status === 'ACTIVE'
          ? 'Akun diaktifkan kembali.'
          : 'Akun dinonaktifkan dan session aktifnya diakhiri.',
      );
      setStatusConfirm(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => {
      setStatusConfirm(null);
      setActionError(apiMessage(error));
    },
  });

  const unlockMutation = useMutation({
    mutationFn: () => unlockUser(userId),
    onSuccess: () => {
      notify('Kunci akun dibuka.');
      setActionError(null);
      invalidate();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const resetForm = useForm<ResetForm>({
    resolver: zodResolver(resetSchema),
    defaultValues: { temporaryPassword: '' },
  });
  const resetMutation = useMutation({
    mutationFn: (values: ResetForm) => resetUserPassword(userId, values.temporaryPassword),
    onSuccess: () => {
      notify('Password sementara dibuat. Sampaikan melalui kanal aman.');
      setResetOpen(false);
      resetForm.reset();
      invalidate();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const unitForm = useForm<AssignUnitForm>({
    resolver: zodResolver(assignUnitSchema),
    defaultValues: { organizationUnitId: '', assignmentType: 'SECONDARY', validUntil: '' },
  });
  const unitMutation = useMutation({
    mutationFn: (values: AssignUnitForm) =>
      assignUnit(userId, {
        organizationUnitId: values.organizationUnitId,
        assignmentType: values.assignmentType,
        validUntil: toIsoDate(values.validUntil),
      }),
    onSuccess: () => {
      notify('Keanggotaan unit ditambahkan.');
      setAssignUnitOpen(false);
      unitForm.reset();
      setActionError(null);
      invalidate();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const endUnitMutation = useMutation({
    mutationFn: (assignmentId: string) => endUnitAssignment(userId, assignmentId),
    onSuccess: () => {
      notify('Keanggotaan unit diakhiri.');
      setEndAssignment(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => {
      setEndAssignment(null);
      setActionError(apiMessage(error));
    },
  });

  const roleForm = useForm<AssignRoleForm>({
    resolver: zodResolver(assignRoleSchema),
    defaultValues: {
      roleCode: '',
      scopeType: 'GLOBAL',
      organizationUnitId: '',
      includeDescendants: false,
      validUntil: '',
    },
  });
  const roleMutation = useMutation({
    mutationFn: (values: AssignRoleForm) =>
      assignRole(userId, {
        roleCode: values.roleCode,
        scopeType: values.scopeType,
        organizationUnitId:
          values.scopeType === 'ORGANIZATION_UNIT' ? values.organizationUnitId : null,
        includeDescendants: values.scopeType === 'ORGANIZATION_UNIT' && values.includeDescendants,
        validUntil: toIsoDate(values.validUntil),
      }),
    onSuccess: () => {
      notify('Role berhasil ditugaskan.');
      setAssignRoleOpen(false);
      roleForm.reset();
      setActionError(null);
      invalidate();
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const revokeMutation = useMutation({
    mutationFn: (assignmentId: string) => revokeRoleAssignment(assignmentId),
    onSuccess: () => {
      notify('Penugasan role dicabut.');
      setRevokeTarget(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => {
      setRevokeTarget(null);
      setActionError(apiMessage(error));
    },
  });

  if (detailQuery.isPending) {
    return <LoadingBlock label="Memuat detail pengguna…" />;
  }

  if (detailQuery.isError || !detail) {
    return (
      <ErrorState
        message={apiMessage(detailQuery.error)}
        onRetry={() => void detailQuery.refetch()}
      />
    );
  }

  const unitName = (id: string | null) =>
    id ? (unitsQuery.data?.find((unit) => unit.id === id)?.unitName ?? id) : '—';

  const scopeLabel = (scopeType: string, organizationUnitId: string | null, workspaceId: string | null) => {
    if (scopeType === 'GLOBAL') {
      return 'Global';
    }
    if (scopeType === 'ORGANIZATION_UNIT') {
      return `Unit: ${unitName(organizationUnitId)}`;
    }
    if (scopeType === 'WORKSPACE') {
      return `Workspace: ${workspaceId ?? '—'}`;
    }
    return scopeType;
  };

  const nextStatus: AccountStatus = detail.user.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';

  return (
    <>
      <PageHeader
        title={detail.user.displayName}
        description={`@${detail.user.username}${detail.user.email ? ` · ${detail.user.email}` : ''}`}
        meta={
          <>
            <StatusBadge status={detail.user.status} label={statusLabels[detail.user.status]} />
            {detail.user.mustChangePassword ? (
              <Badge tone="warning">Wajib ganti password</Badge>
            ) : null}
            {isSelf ? <Badge tone="info">Akun Anda</Badge> : null}
          </>
        }
        action={
          <Link
            to="/admin/users"
            className="inline-flex h-10 items-center gap-2 rounded-lg border border-line-strong bg-surface px-4 text-sm font-semibold text-ink hover:bg-surface-muted"
          >
            <Icon name="arrow-left" size={16} />
            Kembali
          </Link>
        }
      />

      {actionError ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-3">
        <Card className="xl:col-span-2">
          <CardHeader title="Profil" description="Nama tampilan dan email kontak akun." />
          <form
            className="space-y-4 px-5 py-4"
            onSubmit={profileForm.handleSubmit((values) => profileMutation.mutate(values))}
            noValidate
          >
            {profileError ? <Alert tone="danger">{profileError}</Alert> : null}
            <div className="grid gap-4 sm:grid-cols-2">
              <InputField
                label="Nama tampilan"
                required
                error={profileForm.formState.errors.displayName?.message}
                {...profileForm.register('displayName')}
              />
              <InputField
                label="Email"
                type="email"
                error={profileForm.formState.errors.email?.message}
                {...profileForm.register('email')}
              />
            </div>
            <div className="flex justify-end">
              <Button type="submit" loading={profileMutation.isPending}>
                Simpan profil
              </Button>
            </div>
          </form>

          <div className="border-t border-line">
            <dl className="px-5 py-2">
              <DetailRow label="Employee">
                {detail.user.employeeId ? (
                  <Link
                    to={`/admin/employees/${detail.user.employeeId}`}
                    className="font-semibold text-primary hover:underline"
                  >
                    Lihat data employee terhubung
                  </Link>
                ) : (
                  <span className="text-ink-secondary">Belum terhubung ke employee</span>
                )}
              </DetailRow>
              <DetailRow label="User ID">
                <span className="font-mono text-xs">{detail.user.id}</span>
              </DetailRow>
              <DetailRow label="Dibuat">{formatDateTime(detail.user.createdAt)}</DetailRow>
              <DetailRow label="Login terakhir">{formatDateTime(detail.user.lastLoginAt)}</DetailRow>
            </dl>
          </div>
        </Card>

        <Card>
          <CardHeader title="Status akun" description="Perubahan status berlaku segera." />
          <div className="space-y-3 px-5 py-4">
            <p className="text-xs text-ink-secondary">
              Akun nonaktif tidak dapat login dan session aktifnya diakhiri. Akun terkunci dapat
              dibuka tanpa mengganti password.
            </p>
            {detail.user.status === 'LOCKED' ? (
              <Button
                variant="secondary"
                className="w-full"
                onClick={() => unlockMutation.mutate()}
                loading={unlockMutation.isPending}
                disabled={isSelf}
              >
                Buka kunci akun
              </Button>
            ) : (
              <Button
                variant={nextStatus === 'INACTIVE' ? 'danger' : 'primary'}
                className="w-full"
                onClick={() => setStatusConfirm(nextStatus)}
                disabled={isSelf}
              >
                {nextStatus === 'INACTIVE' ? 'Nonaktifkan akun' : 'Aktifkan akun'}
              </Button>
            )}
            <Button
              variant="secondary"
              className="w-full"
              onClick={() => setResetOpen(true)}
              disabled={isSelf}
            >
              Reset password
            </Button>
            {isSelf ? (
              <p className="text-[11px] text-ink-secondary">
                Anda tidak dapat mengubah status atau mereset password akun sendiri dari halaman
                ini.
              </p>
            ) : null}
          </div>
        </Card>
      </div>

      <div className="mt-5 grid gap-5 xl:grid-cols-2">
        <Card>
          <CardHeader
            title="Keanggotaan unit"
            description="Unit menentukan scope akses organisasi."
            action={
              canManageOrg ? (
                <Button size="compact" variant="secondary" onClick={() => setAssignUnitOpen(true)}>
                  <Icon name="plus" size={14} />
                  Tambah unit
                </Button>
              ) : undefined
            }
          />
          {detail.organizationUnits.length === 0 ? (
            <EmptyState
              title="Belum ada keanggotaan unit"
              description={
                canManageOrg
                  ? 'Tambahkan unit agar scope organisasi pengguna dapat dievaluasi.'
                  : 'Pengguna belum tergabung pada unit organisasi.'
              }
            />
          ) : (
            <ul className="divide-y divide-line">
              {detail.organizationUnits.map((membership) => (
                <li
                  key={membership.id}
                  className="flex flex-wrap items-center justify-between gap-2 px-5 py-3"
                >
                  <div>
                    <p className="text-sm font-semibold text-ink">{membership.unitName}</p>
                    <p className="text-xs text-ink-secondary">
                      <span className="font-mono">{membership.unitCode}</span> ·{' '}
                      {membership.assignmentType === 'PRIMARY' ? 'Utama' : 'Tambahan'} ·{' '}
                      {membership.active ? 'Aktif' : 'Tidak aktif'}
                    </p>
                    {membership.validUntil ? (
                      <p className="text-[11px] text-ink-secondary">
                        Berlaku sampai {formatDate(membership.validUntil)}
                      </p>
                    ) : null}
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge tone={membership.active ? 'success' : 'neutral'}>
                      {membership.active ? 'Aktif' : 'Berakhir'}
                    </Badge>
                    {canManageOrg && membership.active ? (
                      <Button
                        size="compact"
                        variant="ghost"
                        className="text-danger hover:bg-danger-bg"
                        onClick={() =>
                          setEndAssignment({ id: membership.id, label: membership.unitName })
                        }
                      >
                        Akhiri
                      </Button>
                    ) : null}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader
            title="Penugasan role"
            description="Role dengan scope unit tidak dianggap global."
            action={
              canManageRoles ? (
                <Button size="compact" variant="secondary" onClick={() => setAssignRoleOpen(true)}>
                  <Icon name="plus" size={14} />
                  Tambah role
                </Button>
              ) : undefined
            }
          />
          {!canManageRoles ? (
            <EmptyState
              title="Role tidak dapat dikelola"
              description="Akun Anda tidak memiliki capability role.manage."
            />
          ) : roleAssignmentsQuery.isPending ? (
            <div className="px-5 py-4">
              <LoadingBlock label="Memuat penugasan role…" />
            </div>
          ) : roleAssignmentsQuery.isError ? (
            <ErrorState
              message={apiMessage(roleAssignmentsQuery.error)}
              onRetry={() => void roleAssignmentsQuery.refetch()}
            />
          ) : !roleAssignmentsQuery.data || roleAssignmentsQuery.data.length === 0 ? (
            <EmptyState
              title="Belum ada role"
              description="Pengguna tanpa role tidak memperoleh capability apa pun."
            />
          ) : (
            <ul className="divide-y divide-line">
              {roleAssignmentsQuery.data.map((assignment) => (
                <li
                  key={assignment.id}
                  className="flex flex-wrap items-center justify-between gap-2 px-5 py-3"
                >
                  <div>
                    <p className="text-sm font-semibold text-ink">{assignment.roleName}</p>
                    <p className="text-xs text-ink-secondary">
                      {scopeLabel(
                        assignment.scopeType,
                        assignment.organizationUnitId,
                        assignment.workspaceId,
                      )}
                      {assignment.includeDescendants ? ' · termasuk unit turunan' : ''}
                      {assignment.validUntil
                        ? ` · berlaku sampai ${formatDate(assignment.validUntil)}`
                        : ''}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <Badge
                      tone={
                        assignment.status === 'ACTIVE'
                          ? 'success'
                          : assignment.status === 'EXPIRED'
                            ? 'warning'
                            : 'neutral'
                      }
                    >
                      {assignment.status}
                    </Badge>
                    <Button
                      size="compact"
                      variant="ghost"
                      className="text-danger hover:bg-danger-bg"
                      onClick={() =>
                        setRevokeTarget({ id: assignment.id, label: assignment.roleName })
                      }
                    >
                      Cabut
                    </Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      <ConfirmDialog
        open={statusConfirm !== null}
        title={nextStatus === 'INACTIVE' ? 'Nonaktifkan akun?' : 'Aktifkan akun?'}
        message={
          nextStatus === 'INACTIVE'
            ? `Akun ${detail.user.username} tidak akan dapat login dan seluruh session aktifnya diakhiri.`
            : `Akun ${detail.user.username} akan dapat login kembali.`
        }
        confirmLabel={nextStatus === 'INACTIVE' ? 'Nonaktifkan' : 'Aktifkan'}
        tone={nextStatus === 'INACTIVE' ? 'danger' : 'primary'}
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusConfirm) {
            statusMutation.mutate(statusConfirm);
          }
        }}
        onClose={() => setStatusConfirm(null)}
      />

      <ConfirmDialog
        open={endAssignment !== null}
        title="Akhiri keanggotaan unit?"
        message={`Keanggotaan ${endAssignment?.label ?? ''} akan diakhiri. Akses yang bergantung pada scope unit ini dapat hilang.`}
        confirmLabel="Akhiri keanggotaan"
        loading={endUnitMutation.isPending}
        onConfirm={() => {
          if (endAssignment) {
            endUnitMutation.mutate(endAssignment.id);
          }
        }}
        onClose={() => setEndAssignment(null)}
      />

      <ConfirmDialog
        open={revokeTarget !== null}
        title="Cabut penugasan role?"
        message={`Penugasan role ${revokeTarget?.label ?? ''} akan dicabut. Grant independent dari sumber lain tidak terpengaruh.`}
        confirmLabel="Cabut role"
        loading={revokeMutation.isPending}
        onConfirm={() => {
          if (revokeTarget) {
            revokeMutation.mutate(revokeTarget.id);
          }
        }}
        onClose={() => setRevokeTarget(null)}
      />

      <Dialog
        open={resetOpen}
        title="Reset password"
        description="Password sementara wajib diganti pengguna pada login berikutnya."
        size="sm"
        onClose={() => setResetOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setResetOpen(false)}>
              Batal
            </Button>
            <Button
              loading={resetMutation.isPending}
              onClick={() => void resetForm.handleSubmit((values) => resetMutation.mutate(values))()}
            >
              Reset password
            </Button>
          </>
        }
      >
        <form noValidate>
          <InputField
            label="Password sementara"
            type="password"
            required
            autoComplete="new-password"
            helper="Minimal 12 karakter. Session aktif pengguna akan diakhiri."
            error={resetForm.formState.errors.temporaryPassword?.message}
            {...resetForm.register('temporaryPassword')}
          />
        </form>
      </Dialog>

      <Dialog
        open={assignUnitOpen}
        title="Tambah keanggotaan unit"
        size="sm"
        onClose={() => setAssignUnitOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAssignUnitOpen(false)}>
              Batal
            </Button>
            <Button
              loading={unitMutation.isPending}
              onClick={() => void unitForm.handleSubmit((values) => unitMutation.mutate(values))()}
            >
              Tambahkan
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <SelectField
            label="Unit organisasi"
            required
            error={unitForm.formState.errors.organizationUnitId?.message}
            {...unitForm.register('organizationUnitId')}
          >
            <option value="">Pilih unit</option>
            {unitsQuery.data?.map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName} ({unit.unitCode})
              </option>
            ))}
          </SelectField>
          <SelectField label="Tipe assignment" required {...unitForm.register('assignmentType')}>
            <option value="SECONDARY">Tambahan</option>
            <option value="PRIMARY">Utama</option>
          </SelectField>
          <InputField
            label="Berlaku sampai"
            type="date"
            helper="Kosongkan bila tidak ada batas waktu."
            {...unitForm.register('validUntil')}
          />
          <p className="text-xs text-ink-secondary">
            Menetapkan assignment utama akan menurunkan assignment utama lain pada unit yang sama.
          </p>
        </form>
      </Dialog>

      <Dialog
        open={assignRoleOpen}
        title="Tambah penugasan role"
        size="sm"
        onClose={() => setAssignRoleOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAssignRoleOpen(false)}>
              Batal
            </Button>
            <Button
              loading={roleMutation.isPending}
              onClick={() => void roleForm.handleSubmit((values) => roleMutation.mutate(values))()}
            >
              Tugaskan role
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <SelectField
            label="Role"
            required
            error={roleForm.formState.errors.roleCode?.message}
            {...roleForm.register('roleCode')}
          >
            <option value="">Pilih role</option>
            {rolesQuery.data
              ?.filter((role) => role.status === 'ACTIVE')
              .map((role) => (
                <option key={role.id} value={role.roleCode}>
                  {role.roleName} ({role.roleCode})
                </option>
              ))}
          </SelectField>
          <SelectField label="Scope" required {...roleForm.register('scopeType')}>
            <option value="GLOBAL">Global</option>
            <option value="ORGANIZATION_UNIT">Unit organisasi</option>
          </SelectField>
          {roleForm.watch('scopeType') === 'ORGANIZATION_UNIT' ? (
            <SelectField
              label="Unit"
              required
              error={roleForm.formState.errors.organizationUnitId?.message}
              {...roleForm.register('organizationUnitId')}
            >
              <option value="">Pilih unit</option>
              {unitsQuery.data?.map((unit) => (
                <option key={unit.id} value={unit.id}>
                  {unit.unitName} ({unit.unitCode})
                </option>
              ))}
            </SelectField>
          ) : null}
          {roleForm.watch('scopeType') === 'ORGANIZATION_UNIT' ? (
            <label className="flex items-start gap-2 rounded-lg border border-line px-3 py-2 text-sm text-ink-secondary">
              <input
                type="checkbox"
                className="mt-0.5 size-4 rounded border-line-strong accent-[#8B1A1A]"
                {...roleForm.register('includeDescendants')}
              />
              <span>
                Berlaku juga untuk unit turunan
                <span className="mt-0.5 block text-xs text-ink-muted">
                  Opt-in. Tanpa opsi ini, role hanya berlaku pada unit yang dipilih.
                </span>
              </span>
            </label>
          ) : null}
          <InputField
            label="Berlaku sampai"
            type="date"
            helper="Kosongkan bila tanpa batas waktu."
            {...roleForm.register('validUntil')}
          />
          <p className="text-xs text-ink-secondary">
            Penugasan scope workspace akan tersedia setelah modul Repository aktif.
          </p>
        </form>
      </Dialog>
    </>
  );
}

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}

