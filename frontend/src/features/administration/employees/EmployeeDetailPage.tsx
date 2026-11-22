import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useParams } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { EmploymentStatus } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField } from '../../../shared/components/Field';
import { Icon } from '../../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { cn } from '../../../shared/utils/cn';
import { formatDateTime } from '../../../shared/utils/format';
import {
  createEmployeeAccount,
  getEmployee,
  linkEmployeeAccount,
  listActiveUnits,
  listUnlinkedAccounts,
  transferEmployee,
  updateEmployee,
} from '../api';
import { employmentStatusLabels } from './labels';

const profileSchema = z.object({
  nip: z.string().trim().min(1, 'NIP wajib diisi.').max(30, 'NIP maksimal 30 karakter.'),
  fullName: z.string().trim().min(1, 'Nama lengkap wajib diisi.').max(160),
  email: z.email('Format email tidak valid.').max(254),
  employmentStatus: z.enum(['ACTIVE', 'INACTIVE', 'TERMINATED']),
});

const transferSchema = z.object({
  divisionUnitId: z.string().min(1, 'Pilih Biro/Division tujuan.'),
});

const accountFields = z.object({
  username: z
    .string()
    .trim()
    .min(3, 'Username minimal 3 karakter.')
    .max(100, 'Username maksimal 100 karakter.')
    .regex(/^[A-Za-z0-9._-]+$/, 'Gunakan huruf, angka, titik, garis bawah, atau tanda hubung.'),
  temporaryPassword: z.string().min(12, 'Password sementara minimal 12 karakter.'),
  confirmPassword: z.string().min(1, 'Konfirmasi password wajib diisi.'),
});

const accountSchema = accountFields.refine(
  (values) => values.temporaryPassword === values.confirmPassword,
  { path: ['confirmPassword'], message: 'Konfirmasi password tidak sama.' },
);

type ProfileForm = z.infer<typeof profileSchema>;
type TransferForm = z.infer<typeof transferSchema>;
type AccountForm = z.infer<typeof accountSchema>;

export function EmployeeDetailPage() {
  const { employeeId = '' } = useParams();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [editOpen, setEditOpen] = useState(false);
  const [transferOpen, setTransferOpen] = useState(false);
  const [accountOpen, setAccountOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [editError, setEditError] = useState<string | null>(null);
  const [transferError, setTransferError] = useState<string | null>(null);
  const [accountError, setAccountError] = useState<string | null>(null);
  const [linkError, setLinkError] = useState<string | null>(null);
  const [linkKeyword, setLinkKeyword] = useState('');
  const [linkInput, setLinkInput] = useState('');
  const [selectedUserId, setSelectedUserId] = useState<string | null>(null);

  const employeeQuery = useQuery({
    queryKey: ['admin-employee', employeeId],
    queryFn: () => getEmployee(employeeId),
    enabled: employeeId.length > 0,
  });

  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'active'],
    queryFn: listActiveUnits,
  });

  const unlinkedQuery = useQuery({
    queryKey: ['unlinked-accounts', linkKeyword],
    queryFn: () =>
      listUnlinkedAccounts({ keyword: linkKeyword || undefined, page: 0, size: 20 }),
    enabled: linkOpen,
  });

  const editForm = useForm<ProfileForm>({ resolver: zodResolver(profileSchema) });
  const transferForm = useForm<TransferForm>({ resolver: zodResolver(transferSchema) });
  const accountForm = useForm<AccountForm>({ resolver: zodResolver(accountSchema) });

  const refresh = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin-employee', employeeId] });
    void queryClient.invalidateQueries({ queryKey: ['admin-employees'] });
    void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
    void queryClient.invalidateQueries({ queryKey: ['unlinked-accounts'] });
  };

  const editMutation = useMutation({
    mutationFn: (values: ProfileForm) => updateEmployee(employeeId, values),
    onSuccess: () => {
      notify('Data employee berhasil diperbarui.');
      setEditOpen(false);
      refresh();
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setEditError(error.message);
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in profileSchema.shape) {
            editForm.setError(fieldError.field as keyof ProfileForm, {
              message: fieldError.message,
            });
          }
        }
      } else {
        setEditError('Data employee tidak dapat diperbarui. Coba lagi.');
      }
    },
  });

  const transferMutation = useMutation({
    mutationFn: (values: TransferForm) =>
      transferEmployee(employeeId, {
        divisionUnitId: values.divisionUnitId,
        expectedPlacementId: employeeQuery.data?.employee.activePlacementId,
      }),
    onSuccess: (updated) => {
      notify(`Employee dipindahkan ke ${updated.employee.divisionName ?? 'Biro/Division baru'}.`);
      setTransferOpen(false);
      transferForm.reset();
      refresh();
    },
    onError: (error) => {
      setTransferError(
        error instanceof ApiError
          ? error.message
          : 'Placement tidak dapat dipindahkan. Coba lagi.',
      );
    },
  });

  const accountMutation = useMutation({
    mutationFn: (values: AccountForm) =>
      createEmployeeAccount(employeeId, {
        username: values.username,
        temporaryPassword: values.temporaryPassword,
      }),
    onSuccess: (account) => {
      notify(`Akun ${account.username} dibuat. Pengguna wajib mengganti password saat login.`);
      setAccountOpen(false);
      accountForm.reset();
      refresh();
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setAccountError(error.message);
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in accountFields.shape) {
            accountForm.setError(fieldError.field as keyof AccountForm, {
              message: fieldError.message,
            });
          }
        }
      } else {
        setAccountError('Akun tidak dapat dibuat. Coba lagi.');
      }
    },
  });

  const linkMutation = useMutation({
    mutationFn: (userId: string) => linkEmployeeAccount(employeeId, userId),
    onSuccess: (account) => {
      notify(`Akun ${account.username} berhasil dihubungkan ke employee ini.`);
      setLinkOpen(false);
      setSelectedUserId(null);
      setLinkKeyword('');
      setLinkInput('');
      refresh();
    },
    onError: (error) => {
      setLinkError(
        error instanceof ApiError ? error.message : 'Akun tidak dapat dihubungkan. Coba lagi.',
      );
    },
  });

  const openCreateAccount = () => {
    accountForm.reset({ username: '', temporaryPassword: '', confirmPassword: '' });
    setAccountError(null);
    setAccountOpen(true);
  };

  const openLinkAccount = () => {
    setSelectedUserId(null);
    setLinkKeyword('');
    setLinkInput('');
    setLinkError(null);
    setLinkOpen(true);
  };

  const openEdit = () => {
    const employee = employeeQuery.data?.employee;
    if (!employee) {
      return;
    }
    editForm.reset({
      nip: employee.nip,
      fullName: employee.fullName,
      email: employee.email,
      employmentStatus: employee.employmentStatus,
    });
    setEditError(null);
    setEditOpen(true);
  };

  const openTransfer = () => {
    transferForm.reset({ divisionUnitId: '' });
    setTransferError(null);
    setTransferOpen(true);
  };

  if (employeeQuery.isPending) {
    return (
      <div className="py-6">
        <LoadingBlock label="Memuat detail employee…" />
      </div>
    );
  }

  if (employeeQuery.isError) {
    const notFound =
      employeeQuery.error instanceof ApiError &&
      employeeQuery.error.code === 'RESOURCE_NOT_FOUND';
    return (
      <ErrorState
        title={notFound ? 'Employee tidak ditemukan' : 'Detail employee tidak dapat dimuat'}
        message={
          employeeQuery.error instanceof ApiError
            ? employeeQuery.error.message
            : 'Terjadi kesalahan saat memuat employee.'
        }
        onRetry={notFound ? undefined : () => void employeeQuery.refetch()}
      />
    );
  }

  const detail = employeeQuery.data;
  const employee = detail.employee;
  const activePlacement = detail.placements.find((placement) => placement.active) ?? null;
  const linkedAccount = detail.linkedAccount;
  const canCreateAccount = employee.employmentStatus === 'ACTIVE';
  const divisions = (unitsQuery.data ?? []).filter(
    (unit) =>
      unit.unitType === 'DIVISION' &&
      unit.status === 'ACTIVE' &&
      unit.id !== activePlacement?.divisionUnitId,
  );

  return (
    <>
      <Link
        to="/admin/employees"
        className="mb-4 inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
      >
        <Icon name="arrow-left" size={15} />
        Kembali ke daftar employee
      </Link>

      <PageHeader
        title={employee.fullName}
        description="Data employee, placement aktif, dan riwayat perpindahan Biro/Division."
        meta={
          <>
            <StatusBadge
              status={employee.employmentStatus}
              label={employmentStatusLabels[employee.employmentStatus]}
            />
            <Badge tone="gold">{employee.divisionName ?? 'Belum ditempatkan'}</Badge>
          </>
        }
        action={
          <>
            <Button variant="secondary" onClick={openEdit}>
              <Icon name="edit" size={16} />
              Ubah data
            </Button>
            <Button
              onClick={openTransfer}
              disabled={employee.employmentStatus === 'TERMINATED' || !activePlacement}
            >
              <Icon name="building" size={16} />
              Pindahkan Biro/Division
            </Button>
          </>
        }
      />

      <div className="grid gap-5 lg:grid-cols-2">
        <Card>
          <CardHeader title="Profil kepegawaian" />
          <dl className="px-5 py-2">
            <DetailRow label="NIP">
              <span className="font-mono">{employee.nip}</span>
            </DetailRow>
            <DetailRow label="Nama lengkap">{employee.fullName}</DetailRow>
            <DetailRow label="Email">{employee.email}</DetailRow>
            <DetailRow label="Status">
              <StatusBadge
                status={employee.employmentStatus}
                label={employmentStatusLabels[employee.employmentStatus]}
              />
            </DetailRow>
            <DetailRow label="Diperbarui">{formatDateTime(employee.updatedAt)}</DetailRow>
          </dl>
        </Card>

        <Card>
          <CardHeader
            title="Placement aktif"
            description="Directorate dan Department diturunkan dari hierarki Biro/Division."
          />
          {activePlacement ? (
            <dl className="px-5 py-2">
              <DetailRow label="Biro/Division">
                {activePlacement.divisionName ?? '—'}
                {activePlacement.divisionCode ? (
                  <span className="ml-2 font-mono text-xs text-ink-secondary">
                    {activePlacement.divisionCode}
                  </span>
                ) : null}
              </DetailRow>
              <DetailRow label="Department">{activePlacement.departmentName ?? '—'}</DetailRow>
              <DetailRow label="Directorate">{activePlacement.directorateName ?? '—'}</DetailRow>
              <DetailRow label="Berlaku sejak">{formatDateTime(activePlacement.validFrom)}</DetailRow>
            </dl>
          ) : (
            <EmptyState
              title="Belum ada placement aktif"
              description="Employee ini tidak memiliki placement pada Biro/Division. Periksa data HR sebelum mengaktifkan akses."
            />
          )}
        </Card>
      </div>

      <Card className="mt-5">
        <CardHeader
          title="Akun pengguna"
          description="Satu employee hanya dapat dihubungkan ke satu akun dan sebaliknya."
          action={
            linkedAccount ? null : (
              <div className="flex flex-wrap gap-2">
                <Button size="compact" variant="secondary" onClick={openLinkAccount}>
                  <Icon name="link" size={15} />
                  Hubungkan akun
                </Button>
                <Button
                  size="compact"
                  onClick={openCreateAccount}
                  disabled={!canCreateAccount}
                  title={canCreateAccount ? undefined : 'Akun baru hanya untuk employee ACTIVE.'}
                >
                  <Icon name="plus" size={15} />
                  Buat akun
                </Button>
              </div>
            )
          }
        />
        {linkedAccount ? (
          <dl className="px-5 py-2">
            <DetailRow label="Username">
              <Link
                to={`/admin/users/${linkedAccount.userId}`}
                className="font-semibold text-primary hover:underline"
              >
                {linkedAccount.username}
              </Link>
            </DetailRow>
            <DetailRow label="Status akun">
              <StatusBadge status={linkedAccount.status} />
            </DetailRow>
            <DetailRow label="Password awal">
              {linkedAccount.mustChangePassword ? (
                <Badge tone="gold">Wajib mengganti password</Badge>
              ) : (
                <Badge tone="neutral">Sudah diganti</Badge>
              )}
            </DetailRow>
            <DetailRow label="Login terakhir">
              {linkedAccount.lastLoginAt ? formatDateTime(linkedAccount.lastLoginAt) : 'Belum pernah'}
            </DetailRow>
          </dl>
        ) : (
          <EmptyState
            title="Employee belum memiliki akun"
            description={
              canCreateAccount
                ? 'Buat akun baru dengan username sendiri atau hubungkan akun yang sudah ada dan belum terhubung ke employee lain.'
                : 'Akun baru hanya untuk employee ACTIVE. Untuk menautkan akun lama, nonaktifkan akun tersebut terlebih dahulu.'
            }
            action={
              <div className="flex flex-wrap justify-center gap-2">
                <Button size="compact" variant="secondary" onClick={openLinkAccount}>
                  <Icon name="link" size={15} />
                  Hubungkan akun
                </Button>
                <Button
                  size="compact"
                  onClick={openCreateAccount}
                  disabled={!canCreateAccount}
                  title={canCreateAccount ? undefined : 'Akun baru hanya untuk employee ACTIVE.'}
                >
                  <Icon name="plus" size={15} />
                  Buat akun
                </Button>
              </div>
            }
          />
        )}
      </Card>

      <Card className="mt-5">
        <CardHeader
          title="Riwayat placement"
          description="Placement lama ditutup, tidak dihapus, sehingga histori tetap utuh."
        />
        {detail.placements.length === 0 ? (
          <EmptyState
            title="Belum ada riwayat"
            description="Riwayat akan muncul setelah employee ditempatkan pada Biro/Division."
          />
        ) : (
          <Table>
            <THead>
              <Th>Biro/Division</Th>
              <Th className="hidden lg:table-cell">Directorate / Department</Th>
              <Th>Periode</Th>
              <Th>Status</Th>
            </THead>
            <tbody>
              {detail.placements.map((placement) => (
                <Tr key={placement.id} hover={false}>
                  <Td>
                    <p className="font-semibold text-ink">{placement.divisionName ?? '—'}</p>
                    <p className="font-mono text-xs text-ink-secondary">
                      {placement.divisionCode ?? '—'}
                    </p>
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary lg:table-cell">
                    <p>{placement.directorateName ?? '—'}</p>
                    <p>{placement.departmentName ?? '—'}</p>
                  </Td>
                  <Td className="text-xs text-ink-secondary tabular-nums">
                    {formatDateTime(placement.validFrom)} —{' '}
                    {placement.validUntil ? formatDateTime(placement.validUntil) : 'sekarang'}
                  </Td>
                  <Td>
                    {placement.active ? (
                      <Badge tone="success">Aktif</Badge>
                    ) : (
                      <Badge tone="neutral">Selesai</Badge>
                    )}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <Dialog
        open={editOpen}
        title="Ubah data employee"
        description="Perubahan status kepegawaian tidak mengubah status akun secara otomatis."
        onClose={() => {
          setEditOpen(false);
          setEditError(null);
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditOpen(false)}>
              Batal
            </Button>
            <Button
              onClick={() => void editForm.handleSubmit((values) => {
                setEditError(null);
                editMutation.mutate(values);
              })()}
              loading={editMutation.isPending}
            >
              Simpan perubahan
            </Button>
          </>
        }
      >
        <form
          className="space-y-4"
          onSubmit={(event) =>
            void editForm.handleSubmit((values) => {
              setEditError(null);
              editMutation.mutate(values);
            })(event)
          }
          noValidate
        >
          {editError ? (
            <div role="alert" className="rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
              {editError}
            </div>
          ) : null}
          <InputField
            label="NIP"
            required
            autoComplete="off"
            error={editForm.formState.errors.nip?.message}
            {...editForm.register('nip')}
          />
          <InputField
            label="Nama lengkap"
            required
            autoComplete="off"
            error={editForm.formState.errors.fullName?.message}
            {...editForm.register('fullName')}
          />
          <InputField
            label="Email"
            type="email"
            required
            autoComplete="off"
            error={editForm.formState.errors.email?.message}
            {...editForm.register('email')}
          />
          <SelectField
            label="Status kepegawaian"
            required
            error={editForm.formState.errors.employmentStatus?.message}
            {...editForm.register('employmentStatus')}
          >
            {(['ACTIVE', 'INACTIVE', 'TERMINATED'] as EmploymentStatus[]).map((status) => (
              <option key={status} value={status}>
                {employmentStatusLabels[status]}
              </option>
            ))}
          </SelectField>
        </form>
      </Dialog>

      <Dialog
        open={transferOpen}
        title="Pindahkan Biro/Division"
        description="Placement lama ditutup dan placement baru dibuat dalam satu transaksi."
        onClose={() => {
          setTransferOpen(false);
          setTransferError(null);
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setTransferOpen(false)}>
              Batal
            </Button>
            <Button
              onClick={() =>
                void transferForm.handleSubmit((values) => {
                  setTransferError(null);
                  transferMutation.mutate(values);
                })()
              }
              loading={transferMutation.isPending}
            >
              Pindahkan
            </Button>
          </>
        }
      >
        <form
          className="space-y-4"
          onSubmit={(event) =>
            void transferForm.handleSubmit((values) => {
              setTransferError(null);
              transferMutation.mutate(values);
            })(event)
          }
          noValidate
        >
          {transferError ? (
            <div role="alert" className="space-y-2 rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
              <p>{transferError}</p>
              <Button size="compact" variant="secondary" onClick={() => void employeeQuery.refetch()}>
                Muat ulang data
              </Button>
            </div>
          ) : null}
          <p className="text-sm text-ink-secondary">
            Saat ini:{' '}
            <span className="font-semibold text-ink">
              {activePlacement?.divisionName ?? 'Belum ditempatkan'}
            </span>
          </p>
          <SelectField
            label="Biro/Division tujuan"
            required
            helper={
              divisions.length === 0
                ? 'Belum ada Biro/Division aktif lain yang dapat dipilih.'
                : 'Akses lama pada unit sebelumnya tidak otomatis berpindah.'
            }
            error={transferForm.formState.errors.divisionUnitId?.message}
            disabled={unitsQuery.isPending || divisions.length === 0}
            {...transferForm.register('divisionUnitId')}
          >
            <option value="">Pilih Biro/Division</option>
            {divisions.map((division) => (
              <option key={division.id} value={division.id}>
                {division.unitCode} — {division.unitName}
              </option>
            ))}
          </SelectField>
        </form>
      </Dialog>

      <Dialog
        open={accountOpen}
        title="Buat akun employee"
        description={`Akun baru memakai email ${employee.email} dan wajib mengganti password saat login pertama.`}
        onClose={() => {
          setAccountOpen(false);
          setAccountError(null);
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setAccountOpen(false)}>
              Batal
            </Button>
            <Button
              onClick={() =>
                void accountForm.handleSubmit((values) => {
                  setAccountError(null);
                  accountMutation.mutate(values);
                })()
              }
              loading={accountMutation.isPending}
            >
              Buat akun
            </Button>
          </>
        }
      >
        <form
          className="space-y-4"
          onSubmit={(event) =>
            void accountForm.handleSubmit((values) => {
              setAccountError(null);
              accountMutation.mutate(values);
            })(event)
          }
          noValidate
        >
          {accountError ? (
            <div role="alert" className="rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
              {accountError}
            </div>
          ) : null}
          <InputField
            label="Username"
            required
            autoComplete="off"
            helper="3-100 karakter: huruf, angka, titik, garis bawah, atau tanda hubung."
            error={accountForm.formState.errors.username?.message}
            {...accountForm.register('username')}
          />
          <InputField
            label="Password sementara"
            type="password"
            required
            autoComplete="new-password"
            helper="Minimal 12 karakter. Sampaikan kepada pengguna melalui kanal yang aman."
            error={accountForm.formState.errors.temporaryPassword?.message}
            {...accountForm.register('temporaryPassword')}
          />
          <InputField
            label="Konfirmasi password sementara"
            type="password"
            required
            autoComplete="new-password"
            error={accountForm.formState.errors.confirmPassword?.message}
            {...accountForm.register('confirmPassword')}
          />
        </form>
      </Dialog>

      <Dialog
        open={linkOpen}
        title="Hubungkan akun yang sudah ada"
        description="Hanya akun yang belum terhubung ke employee mana pun yang dapat dipilih."
        onClose={() => {
          setLinkOpen(false);
          setLinkError(null);
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setLinkOpen(false)}>
              Batal
            </Button>
            <Button
              disabled={!selectedUserId}
              loading={linkMutation.isPending}
              onClick={() => {
                if (selectedUserId) {
                  setLinkError(null);
                  linkMutation.mutate(selectedUserId);
                }
              }}
            >
              Hubungkan akun
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {linkError ? (
            <div role="alert" className="rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
              {linkError}
            </div>
          ) : null}
          <form
            className="flex items-end gap-2"
            onSubmit={(event) => {
              event.preventDefault();
              setSelectedUserId(null);
              setLinkKeyword(linkInput.trim());
            }}
          >
            <div className="flex-1">
              <InputField
                label="Cari akun"
                value={linkInput}
                onChange={(event) => setLinkInput(event.target.value)}
                placeholder="Username atau nama"
                autoComplete="off"
              />
            </div>
            <Button type="submit" variant="secondary">
              <Icon name="search" size={15} />
              Cari
            </Button>
          </form>

          {unlinkedQuery.isPending ? (
            <LoadingBlock label="Memuat akun tanpa employee…" />
          ) : unlinkedQuery.isError ? (
            <ErrorState
              title="Akun tidak dapat dimuat"
              message={
                unlinkedQuery.error instanceof ApiError
                  ? unlinkedQuery.error.message
                  : 'Terjadi kesalahan saat memuat daftar akun.'
              }
              onRetry={() => void unlinkedQuery.refetch()}
            />
          ) : unlinkedQuery.data.items.length === 0 ? (
            <EmptyState
              title="Tidak ada akun tersedia"
              description="Semua akun sudah terhubung atau tidak ada akun yang cocok dengan pencarian."
            />
          ) : (
            <fieldset className="max-h-72 space-y-1 overflow-y-auto rounded-lg border border-line p-2">
              <legend className="sr-only">Pilih akun yang akan dihubungkan</legend>
              {unlinkedQuery.data.items.map((account) => (
                <label
                  key={account.id}
                  className={cn(
                    'flex cursor-pointer items-start gap-2.5 rounded-lg border px-3 py-2.5 transition-colors',
                    selectedUserId === account.id
                      ? 'border-primary bg-primary-soft'
                      : 'border-transparent hover:bg-surface-muted',
                  )}
                >
                  <input
                    type="radio"
                    name="unlinked-account"
                    value={account.id}
                    checked={selectedUserId === account.id}
                    onChange={() => setSelectedUserId(account.id)}
                    className="mt-0.5 accent-primary"
                  />
                  <span className="min-w-0 flex-1">
                    <span className="flex flex-wrap items-center gap-2">
                      <span className="text-sm font-semibold text-ink">{account.username}</span>
                      <StatusBadge status={account.status} />
                    </span>
                    <span className="mt-0.5 block truncate text-xs text-ink-secondary">
                      {account.displayName}
                      {account.email ? ` · ${account.email}` : ''}
                    </span>
                  </span>
                </label>
              ))}
            </fieldset>
          )}
        </div>
      </Dialog>
    </>
  );
}
