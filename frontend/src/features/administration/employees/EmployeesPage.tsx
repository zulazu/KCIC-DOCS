import { zodResolver } from '@hookform/resolvers/zod';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { EmploymentStatus } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField } from '../../../shared/components/Field';
import { FilterPanel } from '../../../shared/components/FilterPanel';
import { Icon } from '../../../shared/components/Icon';
import { PaginationBar } from '../../../shared/components/PaginationBar';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { createEmployee, listActiveUnits, listEmployees } from '../api';
import { employmentStatusLabels } from './labels';

const createSchema = z.object({
  nip: z.string().trim().min(1, 'NIP wajib diisi.').max(30, 'NIP maksimal 30 karakter.'),
  fullName: z.string().trim().min(1, 'Nama lengkap wajib diisi.').max(160),
  email: z.email('Format email tidak valid.').max(254),
  employmentStatus: z.enum(['ACTIVE', 'INACTIVE', 'TERMINATED']),
  divisionUnitId: z.string().min(1, 'Pilih Biro/Division.'),
});

type CreateEmployeeForm = z.infer<typeof createSchema>;

const DEFAULT_PAGE_SIZE = 10;

export function EmployeesPage() {
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<EmploymentStatus | ''>('');
  const [accountLink, setAccountLink] = useState<'' | 'true' | 'false'>('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { notify } = useToast();
  const activeFilterCount = [keyword, status, accountLink].filter(Boolean).length;

  const employeesQuery = useQuery({
    queryKey: ['admin-employees', { keyword, status, accountLink, page, pageSize }],
    queryFn: () =>
      listEmployees({
        keyword,
        status,
        linked: accountLink === '' ? undefined : accountLink === 'true',
        page,
        size: pageSize,
      }),
    placeholderData: keepPreviousData,
  });

  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'active'],
    queryFn: listActiveUnits,
  });
  const divisions = (unitsQuery.data ?? []).filter(
    (unit) => unit.unitType === 'DIVISION' && unit.status === 'ACTIVE',
  );

  const createForm = useForm<CreateEmployeeForm>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      nip: '',
      fullName: '',
      email: '',
      employmentStatus: 'ACTIVE',
      divisionUnitId: '',
    },
  });

  const createMutation = useMutation({
    mutationFn: createEmployee,
    onSuccess: (created) => {
      notify(`Employee ${created.employee.fullName} berhasil dibuat.`);
      setCreateOpen(false);
      createForm.reset();
      void queryClient.invalidateQueries({ queryKey: ['admin-employees'] });
      navigate(`/admin/employees/${created.employee.id}`);
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setCreateError(error.message);
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field in createSchema.shape) {
            createForm.setError(fieldError.field as keyof CreateEmployeeForm, {
              message: fieldError.message,
            });
          }
        }
      } else {
        setCreateError('Employee tidak dapat dibuat. Coba lagi.');
      }
    },
  });

  const submitCreate = createForm.handleSubmit((values) => {
    setCreateError(null);
    createMutation.mutate(values);
  });

  const applySearch = (event: FormEvent) => {
    event.preventDefault();
    setPage(0);
    setKeyword(keywordInput.trim());
  };

  const data = employeesQuery.data;

  return (
    <>
      <PageHeader
        title="Employee"
        action={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Tambah employee
          </Button>
        }
      />

      <FilterPanel activeCount={activeFilterCount}>
        <form onSubmit={applySearch} className="flex flex-wrap items-end gap-2">
            <div className="min-w-[220px] flex-1">
              <label htmlFor="employee-search" className="mb-1.5 block text-xs font-semibold text-ink">
                Cari employee
              </label>
              <div className="relative">
                <Icon
                  name="search"
                  className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-disabled"
                  size={16}
                />
                <input
                  id="employee-search"
                  type="search"
                  value={keywordInput}
                  onChange={(event) => setKeywordInput(event.target.value)}
                  placeholder="NIP, nama, atau email"
                  className="h-10 w-full rounded-lg border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
                />
              </div>
            </div>
            <div>
              <label htmlFor="employee-status" className="mb-1.5 block text-xs font-semibold text-ink">
                Status kepegawaian
              </label>
              <select
                id="employee-status"
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as EmploymentStatus | '');
                  setPage(0);
                }}
                className="h-10 rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              >
                <option value="">Semua status</option>
                <option value="ACTIVE">Aktif</option>
                <option value="INACTIVE">Nonaktif</option>
                <option value="TERMINATED">Berhenti</option>
              </select>
            </div>
            <div>
              <label htmlFor="employee-account" className="mb-1.5 block text-xs font-semibold text-ink">
                Akun pengguna
              </label>
              <select
                id="employee-account"
                value={accountLink}
                onChange={(event) => {
                  setAccountLink(event.target.value as '' | 'true' | 'false');
                  setPage(0);
                }}
                className="h-10 rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              >
                <option value="">Semua</option>
                <option value="true">Sudah terhubung</option>
                <option value="false">Belum terhubung</option>
              </select>
            </div>
            <Button type="submit" variant="secondary">
              Terapkan
            </Button>
            {keyword || status || accountLink ? (
              <Button
                variant="ghost"
                onClick={() => {
                  setKeywordInput('');
                  setKeyword('');
                  setStatus('');
                  setAccountLink('');
                  setPage(0);
                }}
              >
                Reset
              </Button>
            ) : null}
        </form>
      </FilterPanel>

      <Card>
        {employeesQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label="Memuat daftar employee…" />
          </div>
        ) : employeesQuery.isError ? (
          <ErrorState
            message={
              employeesQuery.error instanceof ApiError
                ? employeesQuery.error.message
                : 'Daftar employee tidak dapat dimuat.'
            }
            onRetry={() => void employeesQuery.refetch()}
          />
        ) : !data || data.items.length === 0 ? (
          <EmptyState
            title="Tidak ada employee"
            description="Ubah filter pencarian atau tambahkan employee dari sumber data HR resmi."
            action={<Button onClick={() => setCreateOpen(true)}>Tambah employee</Button>}
          />
        ) : (
          <Table>
            <THead>
              <Th>Employee</Th>
              <Th>Biro/Division</Th>
              <Th className="hidden lg:table-cell">Directorate / Department</Th>
              <Th>Status</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {data.items.map((employee) => (
                <Tr key={employee.id}>
                  <Td>
                    <Link
                      to={`/admin/employees/${employee.id}`}
                      className="font-semibold text-ink hover:text-primary"
                    >
                      {employee.fullName}
                    </Link>
                    <p className="truncate text-xs text-ink-secondary">
                      <span className="font-mono">{employee.nip}</span> · {employee.email}
                    </p>
                  </Td>
                  <Td>
                    {employee.divisionName ? (
                      <div className="flex flex-col gap-1">
                        <Badge tone="gold">{employee.divisionName}</Badge>
                        <span className="text-xs text-ink-secondary">{employee.divisionCode}</span>
                      </div>
                    ) : (
                      <span className="text-xs text-ink-secondary">Belum ditempatkan</span>
                    )}
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary lg:table-cell">
                    <p>{employee.directorateName ?? '—'}</p>
                    <p>{employee.departmentName ?? '—'}</p>
                  </Td>
                  <Td>
                    <div className="flex flex-wrap items-center gap-1.5">
                      <StatusBadge
                        status={employee.employmentStatus}
                        label={employmentStatusLabels[employee.employmentStatus]}
                      />
                      <Badge tone={employee.hasAccount ? 'success' : 'warning'}>
                        {employee.hasAccount ? 'Akun terhubung' : 'Belum punya akun'}
                      </Badge>
                    </div>
                  </Td>
                  <Td className="text-right">
                    <ButtonLink
                      to={`/admin/employees/${employee.id}`}
                      variant="secondary"
                      size="compact"
                    >
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
            itemLabel="employee"
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
        title="Tambah employee"
        description="Employee dan placement awal pada Biro/Division disimpan dalam satu transaksi."
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
              Simpan employee
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
            label="NIP"
            required
            autoComplete="off"
            helper="Gunakan NIP resmi dari sumber HR; format final mengikuti kebijakan HR."
            error={createForm.formState.errors.nip?.message}
            {...createForm.register('nip')}
          />
          <InputField
            label="Nama lengkap"
            required
            autoComplete="off"
            error={createForm.formState.errors.fullName?.message}
            {...createForm.register('fullName')}
          />
          <InputField
            label="Email"
            type="email"
            required
            autoComplete="off"
            error={createForm.formState.errors.email?.message}
            {...createForm.register('email')}
          />
          <SelectField
            label="Status kepegawaian"
            required
            error={createForm.formState.errors.employmentStatus?.message}
            {...createForm.register('employmentStatus')}
          >
            <option value="ACTIVE">Aktif</option>
            <option value="INACTIVE">Nonaktif</option>
            <option value="TERMINATED">Berhenti</option>
          </SelectField>
          <SelectField
            label="Biro/Division"
            required
            helper={
              unitsQuery.isPending
                ? 'Memuat unit organisasi…'
                : divisions.length === 0
                  ? 'Belum ada Biro/Division aktif. Pemetaan unit resmi diperlukan sebelum menambah employee.'
                  : 'Directorate dan Department diturunkan dari hierarki unit ini.'
            }
            error={createForm.formState.errors.divisionUnitId?.message}
            disabled={unitsQuery.isPending || divisions.length === 0}
            {...createForm.register('divisionUnitId')}
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
    </>
  );
}
