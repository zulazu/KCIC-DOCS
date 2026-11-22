import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import { StatusBadge } from '../../shared/components/Badge';
import { Button, ButtonLink } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { Card, PageHeader } from '../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../shared/components/Table';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDateTime } from '../../shared/utils/format';
import { useAuth } from '../auth/AuthContext';
import { createWorkspace, listActiveUnits, listWorkspaces } from './api';

const createSchema = z.object({
  workspaceCode: z
    .string()
    .trim()
    .regex(
      /^[A-Za-z0-9._-]{2,80}$/,
      'Kode workspace 2-80 karakter: huruf, angka, titik, garis bawah, atau strip.',
    ),
  workspaceName: z.string().trim().min(1, 'Nama workspace wajib diisi.').max(160),
  description: z.string().max(4000),
  ownerOrganizationUnitId: z.string().min(1, 'Unit pemilik wajib dipilih.'),
});
type CreateWorkspaceForm = z.infer<typeof createSchema>;

export function WorkspacesPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [includeArchived, setIncludeArchived] = useState(false);
  const [filter, setFilter] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const workspacesQuery = useQuery({
    queryKey: ['workspaces', includeArchived],
    queryFn: () => listWorkspaces(includeArchived),
  });
  const unitsQuery = useQuery({ queryKey: ['organization-units', 'active'], queryFn: listActiveUnits });

  const form = useForm<CreateWorkspaceForm>({
    resolver: zodResolver(createSchema),
    defaultValues: {
      workspaceCode: '',
      workspaceName: '',
      description: '',
      ownerOrganizationUnitId: '',
    },
  });

  const createMutation = useMutation({
    mutationFn: (values: CreateWorkspaceForm) =>
      createWorkspace({
        workspaceCode: values.workspaceCode,
        workspaceName: values.workspaceName,
        description: values.description || undefined,
        ownerOrganizationUnitId: values.ownerOrganizationUnitId,
      }),
    onSuccess: (workspace) => {
      notify(`Workspace ${workspace.workspaceName} berhasil dibuat.`);
      setCreateOpen(false);
      setActionError(null);
      form.reset();
      void queryClient.invalidateQueries({ queryKey: ['workspaces'] });
      navigate(`/workspaces/${workspace.id}`);
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (
            fieldError.field === 'workspaceCode' ||
            fieldError.field === 'workspaceName' ||
            fieldError.field === 'ownerOrganizationUnitId'
          ) {
            form.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  const canCreate = me?.capabilities.includes('workspace.manage') ?? false;
  const workspaces = useMemo(() => {
    const term = filter.trim().toLowerCase();
    return (workspacesQuery.data ?? []).filter(
      (workspace) =>
        !term ||
        workspace.workspaceName.toLowerCase().includes(term) ||
        workspace.workspaceCode.toLowerCase().includes(term) ||
        (workspace.ownerOrganizationUnitName ?? '').toLowerCase().includes(term),
    );
  }, [workspacesQuery.data, filter]);

  return (
    <>
      <PageHeader
        title="Workspaces"
        description="Katalog workspace dan folder; akses mengikuti scope unit dan ACL."
        action={
          canCreate ? (
            <Button onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={16} />
              Workspace baru
            </Button>
          ) : undefined
        }
      />

      {actionError && !createOpen ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line px-5 py-4">
          <div className="w-full max-w-sm">
            <label htmlFor="workspace-search" className="mb-1.5 block text-xs font-semibold text-ink">
              Cari workspace
            </label>
            <div className="relative">
              <Icon
                name="search"
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-disabled"
                size={16}
              />
              <input
                id="workspace-search"
                type="search"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                placeholder="Nama, kode, atau unit"
                className="h-10 w-full rounded-lg border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              />
            </div>
          </div>
          <label className="flex items-center gap-2 pb-1 text-xs font-medium text-ink-secondary">
            <input
              type="checkbox"
              checked={includeArchived}
              onChange={(event) => setIncludeArchived(event.target.checked)}
              className="size-4 rounded border-line-strong accent-[#8B1A1A]"
            />
            Tampilkan workspace diarsipkan
          </label>
        </div>

        {workspacesQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label="Memuat workspace…" />
          </div>
        ) : workspacesQuery.isError ? (
          <ErrorState
            message={apiMessage(workspacesQuery.error)}
            onRetry={() => void workspacesQuery.refetch()}
          />
        ) : workspaces.length === 0 ? (
          <EmptyState
            title="Belum ada workspace"
            description={
              canCreate
                ? 'Buat workspace pertama atau tampilkan workspace yang sudah diarsipkan.'
                : 'Belum ada workspace dalam scope unit Anda.'
            }
            action={
              canCreate ? (
                <Button onClick={() => setCreateOpen(true)}>
                  <Icon name="plus" size={16} />
                  Buat workspace
                </Button>
              ) : undefined
            }
          />
        ) : (
          <Table>
            <THead>
              <Th>Workspace</Th>
              <Th className="hidden md:table-cell">Unit pemilik</Th>
              <Th className="hidden lg:table-cell">Folder</Th>
              <Th>Status</Th>
              <Th className="hidden xl:table-cell">Diperbarui</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {workspaces.map((workspace) => (
                <Tr key={workspace.id}>
                  <Td>
                    <div className="flex items-center gap-2.5">
                      <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-subtle text-primary">
                        <Icon name="folder" size={16} />
                      </span>
                      <div className="min-w-0">
                        <p className="truncate font-semibold text-ink">{workspace.workspaceName}</p>
                        <p className="font-mono text-[11px] text-ink-secondary">
                          {workspace.workspaceCode}
                        </p>
                      </div>
                    </div>
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary md:table-cell">
                    {workspace.ownerOrganizationUnitName ?? '—'}
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary tabular-nums lg:table-cell">
                    {workspace.activeFolderCount}
                  </Td>
                  <Td>
                    <StatusBadge
                      status={workspace.status}
                      label={
                        workspace.status === 'ACTIVE'
                          ? 'Aktif'
                          : workspace.status === 'INACTIVE'
                            ? 'Nonaktif'
                            : 'Diarsipkan'
                      }
                    />
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary tabular-nums xl:table-cell">
                    {formatDateTime(workspace.updatedAt)}
                  </Td>
                  <Td>
                    <div className="flex justify-end whitespace-nowrap">
                      <ButtonLink
                        to={`/workspaces/${workspace.id}`}
                        variant="secondary"
                        size="compact"
                      >
                        Buka
                        <Icon name="chevron-right" size={14} />
                      </ButtonLink>
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      <Dialog
        open={createOpen}
        title="Workspace baru"
        description="Kode workspace bersifat tetap. Unit pemilik menentukan scope akses dasar."
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Batal
            </Button>
            <Button
              loading={createMutation.isPending}
              onClick={() => void form.handleSubmit((values) => createMutation.mutate(values))()}
            >
              Buat workspace
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
          <InputField
            label="Kode workspace"
            required
            helper="Contoh: OPS-KEB. Tidak dapat diubah setelah dibuat."
            error={form.formState.errors.workspaceCode?.message}
            {...form.register('workspaceCode')}
          />
          <InputField
            label="Nama workspace"
            required
            error={form.formState.errors.workspaceName?.message}
            {...form.register('workspaceName')}
          />
          <SelectField
            label="Unit pemilik"
            required
            helper={
              unitsQuery.isPending
                ? 'Memuat unit…'
                : unitsQuery.isError
                  ? 'Unit tidak dapat dimuat.'
                  : 'Hanya unit aktif yang dapat dipilih.'
            }
            error={form.formState.errors.ownerOrganizationUnitId?.message}
            {...form.register('ownerOrganizationUnitId')}
          >
            <option value="">Pilih unit</option>
            {(unitsQuery.data ?? []).map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName} ({unit.unitCode})
              </option>
            ))}
          </SelectField>
          <TextareaField
            label="Deskripsi"
            error={form.formState.errors.description?.message}
            {...form.register('description')}
          />
        </form>
      </Dialog>
    </>
  );
}
