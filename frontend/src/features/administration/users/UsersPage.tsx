import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { AccountStatus } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField } from '../../../shared/components/Field';
import { FilterPanel } from '../../../shared/components/FilterPanel';
import { Icon } from '../../../shared/components/Icon';
import { PaginationBar } from '../../../shared/components/PaginationBar';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { formatDateTime } from '../../../shared/utils/format';
import { createUser, listUsers } from '../api';

const createSchema = z.object({
  username: z
    .string()
    .trim()
    .regex(
      /^[A-Za-z0-9._-]{3,100}$/,
      'Username 3-100 karakter: huruf, angka, titik, garis bawah, atau strip.',
    ),
  displayName: z.string().trim().min(1, 'Nama tampilan wajib diisi.').max(160),
  email: z.union([z.literal(''), z.email('Format email tidak valid.')]),
  temporaryPassword: z.string().min(12, 'Password sementara minimal 12 karakter.'),
});

type CreateUserForm = z.infer<typeof createSchema>;

const DEFAULT_PAGE_SIZE = 10;

function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]!.toUpperCase())
    .join('');
}

const statusLabels: Record<AccountStatus, string> = {
  ACTIVE: 'Aktif',
  INACTIVE: 'Nonaktif',
  LOCKED: 'Terkunci',
};

export function UsersPage() {
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<AccountStatus | ''>('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { notify } = useToast();
  const activeFilterCount = (keyword ? 1 : 0) + (status ? 1 : 0);

  const usersQuery = useQuery({
    queryKey: ['admin-users', { keyword, status, page, pageSize }],
    queryFn: () => listUsers({ keyword, status, page, size: pageSize }),
    placeholderData: keepPreviousData,
  });

  const createForm = useForm<CreateUserForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { username: '', displayName: '', email: '', temporaryPassword: '' },
  });
  const [createError, setCreateError] = useState<string | null>(null);

  const createMutation = useMutation({
    mutationFn: createUser,
    onSuccess: (created) => {
      notify(`Akun ${created.user.username} berhasil dibuat.`);
      setCreateOpen(false);
      createForm.reset();
      void queryClient.invalidateQueries({ queryKey: ['admin-users'] });
      navigate(`/admin/users/${created.user.id}`);
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setCreateError(error.message);
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in createSchema.shape) {
            createForm.setError(fieldError.field as keyof CreateUserForm, {
              message: fieldError.message,
            });
          }
        }
      } else {
        setCreateError('Akun tidak dapat dibuat. Coba lagi.');
      }
    },
  });

  const submitCreate = createForm.handleSubmit((values) => {
    setCreateError(null);
    createMutation.mutate({
      username: values.username,
      displayName: values.displayName,
      email: values.email || undefined,
      temporaryPassword: values.temporaryPassword,
    });
  });

  const applySearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  const data = usersQuery.data;

  return (
    <>
      <PageHeader
        title="User Management"
        action={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Tambah pengguna
          </Button>
        }
      />

      <FilterPanel activeCount={activeFilterCount}>
        <form onSubmit={applySearch} className="flex flex-wrap items-end gap-2">
            <div className="min-w-[220px] flex-1">
              <label htmlFor="user-search" className="mb-1.5 block text-xs font-semibold text-ink">
                Cari pengguna
              </label>
              <div className="relative">
                <Icon
                  name="search"
                  className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-disabled"
                  size={16}
                />
                <input
                  id="user-search"
                  type="search"
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  placeholder="Username, nama, atau email"
                  className="h-10 w-full rounded-lg border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
                />
              </div>
            </div>
            <div>
              <label htmlFor="user-status" className="mb-1.5 block text-xs font-semibold text-ink">
                Status
              </label>
              <select
                id="user-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as AccountStatus | '');
                  setPage(0);
                }}
                className="h-10 rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              >
                <option value="">Semua status</option>
                <option value="ACTIVE">Aktif</option>
                <option value="INACTIVE">Nonaktif</option>
                <option value="LOCKED">Terkunci</option>
              </select>
            </div>
            <Button type="submit" variant="secondary">
              Terapkan
            </Button>
            {keyword || status ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setKeywordInput('');
                  setKeyword('');
                  setStatus('');
                  setPage(0);
                }}
              >
                Reset
              </Button>
            ) : null}
        </form>
      </FilterPanel>

      <Card>
        {usersQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label="Memuat daftar pengguna…" />
          </div>
        ) : usersQuery.isError ? (
          <ErrorState
            message={
              usersQuery.error instanceof ApiError
                ? usersQuery.error.message
                : 'Daftar pengguna tidak dapat dimuat.'
            }
            onRetry={() => void usersQuery.refetch()}
          />
        ) : !data || data.items.length === 0 ? (
          <EmptyState
            title="Tidak ada pengguna"
            description="Ubah filter pencarian atau tambah akun baru untuk mulai mengelola akses."
            action={<Button onClick={() => setCreateOpen(true)}>Tambah pengguna</Button>}
          />
        ) : (
          <Table>
            <THead>
              <Th>Pengguna</Th>
              <Th>Status</Th>
              <Th className="hidden lg:table-cell">Login terakhir</Th>
              <Th className="hidden lg:table-cell">Dibuat</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {data.items.map((user) => (
                <Tr key={user.id}>
                  <Td>
                    <div className="flex items-center gap-3">
                      <span
                        aria-hidden="true"
                        className="flex size-9 shrink-0 items-center justify-center rounded-full bg-primary-subtle text-xs font-bold text-primary"
                      >
                        {initials(user.displayName)}
                      </span>
                      <div className="min-w-0">
                        <Link
                          to={`/admin/users/${user.id}`}
                          className="font-semibold text-ink hover:text-primary"
                        >
                          {user.displayName}
                        </Link>
                        <p className="truncate text-xs text-ink-secondary">
                          <span className="font-mono">{user.username}</span>
                          {user.email ? ` · ${user.email}` : ''}
                        </p>
                      </div>
                    </div>
                  </Td>
                  <Td>
                    <div className="flex flex-wrap items-center gap-1.5 whitespace-nowrap">
                      <StatusBadge status={user.status} label={statusLabels[user.status]} />
                      {user.mustChangePassword ? <Badge tone="warning">Ganti password</Badge> : null}
                      {user.employeeId ? (
                        <Link to={`/admin/employees/${user.employeeId}`} title="Buka data employee">
                          <Badge tone="gold">Employee</Badge>
                        </Link>
                      ) : null}
                    </div>
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary tabular-nums lg:table-cell">
                    {formatDateTime(user.lastLoginAt)}
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary tabular-nums lg:table-cell">
                    {formatDateTime(user.createdAt)}
                  </Td>
                  <Td className="text-right">
                    <ButtonLink to={`/admin/users/${user.id}`} variant="secondary" size="compact">
                      Kelola
                      <Icon name="chevron-right" size={14} />
                    </ButtonLink>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}

        {data && data.totalItems > 0 ? (
          <PaginationBar
            className="mx-5 mb-3 mt-0"
            page={page}
            size={pageSize}
            totalItems={data.totalItems}
            totalPages={data.totalPages}
            itemLabel="pengguna"
            onPageChange={setPage}
            onSizeChange={(size) => {
              setPageSize(size);
              setPage(0);
            }}
          />
        ) : null}
      </Card>

      <Dialog
        open={createOpen}
        title="Tambah pengguna"
        description="Akun dibuat dengan password sementara dan wajib diganti saat login pertama."
        onClose={() => {
          setCreateOpen(false);
          setCreateError(null);
          createForm.reset();
        }}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Batal
            </Button>
            <Button onClick={() => void submitCreate()} loading={createMutation.isPending}>
              Buat akun
            </Button>
          </>
        }
      >
        <form className="space-y-4" onSubmit={(event) => void submitCreate(event)} noValidate>
          {createError ? (
            <div role="alert" className="rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
              {createError}
            </div>
          ) : null}
          <InputField
            label="Username"
            required
            autoComplete="off"
            helper="Dipakai untuk login. Contoh: budi.santoso"
            error={createForm.formState.errors.username?.message}
            {...createForm.register('username')}
          />
          <InputField
            label="Nama tampilan"
            required
            autoComplete="off"
            error={createForm.formState.errors.displayName?.message}
            {...createForm.register('displayName')}
          />
          <InputField
            label="Email"
            type="email"
            autoComplete="off"
            error={createForm.formState.errors.email?.message}
            {...createForm.register('email')}
          />
          <InputField
            label="Password sementara"
            type="password"
            required
            autoComplete="new-password"
            helper="Minimal 12 karakter."
            error={createForm.formState.errors.temporaryPassword?.message}
            {...createForm.register('temporaryPassword')}
          />
        </form>
      </Dialog>
    </>
  );
}
