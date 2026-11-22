import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Link, useNavigate } from 'react-router-dom';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../../shared/components/Field';
import { Icon } from '../../../shared/components/Icon';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { createRole, listRoles, updateRole } from '../api';

const createSchema = z.object({
  roleCode: z
    .string()
    .trim()
    .regex(
      /^[A-Za-z][A-Za-z0-9_]{2,79}$/,
      'Kode role 3-80 karakter: huruf, angka, atau garis bawah, diawali huruf.',
    ),
  roleName: z.string().trim().min(1, 'Nama role wajib diisi.').max(120),
  description: z.string().max(4000),
});
type CreateRoleForm = z.infer<typeof createSchema>;

const editSchema = z.object({
  roleName: z.string().trim().min(1, 'Nama role wajib diisi.').max(120),
  description: z.string().max(4000),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});
type EditRoleForm = z.infer<typeof editSchema>;

export function RolesPage() {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const { notify } = useToast();
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<{ id: string; roleCode: string } | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const rolesQuery = useQuery({ queryKey: ['roles'], queryFn: listRoles });
  const editingRole = rolesQuery.data?.find((role) => role.id === editTarget?.id);

  const createForm = useForm<CreateRoleForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { roleCode: '', roleName: '', description: '' },
  });
  const createMutation = useMutation({
    mutationFn: (values: CreateRoleForm) =>
      createRole({
        roleCode: values.roleCode,
        roleName: values.roleName,
        description: values.description || undefined,
      }),
    onSuccess: (role) => {
      notify(`Role ${role.roleName} berhasil dibuat.`);
      setCreateOpen(false);
      createForm.reset();
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['roles'] });
      navigate(`/admin/roles/${role.id}`);
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'roleCode' || fieldError.field === 'roleName') {
            createForm.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  const editForm = useForm<EditRoleForm>({
    resolver: zodResolver(editSchema),
    values: editingRole
      ? {
          roleName: editingRole.roleName,
          description: editingRole.description ?? '',
          status: editingRole.status,
        }
      : { roleName: '', description: '', status: 'ACTIVE' },
  });
  const editMutation = useMutation({
    mutationFn: (values: EditRoleForm) =>
      updateRole(editTarget?.id ?? '', {
        roleName: values.roleName,
        description: values.description || undefined,
        status: values.status,
      }),
    onSuccess: (role) => {
      notify(`Role ${role.roleName} diperbarui.`);
      setEditTarget(null);
      setActionError(null);
      void queryClient.invalidateQueries({ queryKey: ['roles'] });
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  return (
    <>
      <PageHeader
        title="Authorization"
        action={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Tambah role
          </Button>
        }
      />

      {actionError ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <Card>
        {rolesQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label="Memuat role…" />
          </div>
        ) : rolesQuery.isError ? (
          <ErrorState
            message={apiMessage(rolesQuery.error)}
            onRetry={() => void rolesQuery.refetch()}
          />
        ) : !rolesQuery.data || rolesQuery.data.length === 0 ? (
          <EmptyState
            title="Belum ada role"
            description="Buat role dan tetapkan permission sebelum menugaskan akses kepada pengguna."
          />
        ) : (
          <Table>
              <THead>
                <Th>Role</Th>
                <Th>Status</Th>
                <Th>Permission</Th>
                <Th align="right">Aksi</Th>
              </THead>
              <tbody>
                {rolesQuery.data.map((role) => (
                  <Tr key={role.id}>
                    <Td>
                      <Link
                        to={`/admin/roles/${role.id}`}
                        className="font-semibold text-ink hover:text-primary"
                      >
                        {role.roleName}
                      </Link>
                      <p className="truncate text-xs text-ink-secondary">
                        <span className="font-mono">{role.roleCode}</span>
                        {role.description ? ` · ${role.description}` : ''}
                      </p>
                    </Td>
                    <Td>
                      <div className="flex flex-wrap items-center gap-1.5">
                        <StatusBadge status={role.status} label={role.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'} />
                        {role.systemRole ? <Badge tone="gold">Sistem</Badge> : null}
                      </div>
                    </Td>
                    <Td className="text-xs text-ink-secondary tabular-nums">
                      {role.permissions.length} permission
                    </Td>
                    <Td>
                      <div className="flex justify-end gap-1.5">
                        <Button
                          size="compact"
                          variant="secondary"
                          onClick={() => setEditTarget({ id: role.id, roleCode: role.roleCode })}
                        >
                          Ubah
                        </Button>
                        <ButtonLink
                          to={`/admin/roles/${role.id}`}
                          variant="secondary"
                          size="compact"
                        >
                          Permission
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
        title="Tambah role"
        onClose={() => setCreateOpen(false)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setCreateOpen(false)}>
              Batal
            </Button>
            <Button
              loading={createMutation.isPending}
              onClick={() => void createForm.handleSubmit((values) => createMutation.mutate(values))()}
            >
              Buat role
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <InputField
            label="Kode role"
            required
            helper="Kode teknis yang stabil. Contoh: DOCUMENT_OWNER"
            error={createForm.formState.errors.roleCode?.message}
            {...createForm.register('roleCode')}
          />
          <InputField
            label="Nama role"
            required
            error={createForm.formState.errors.roleName?.message}
            {...createForm.register('roleName')}
          />
          <TextareaField label="Deskripsi" {...createForm.register('description')} />
        </form>
      </Dialog>

      <Dialog
        open={editTarget !== null}
        title="Ubah role"
        description={editTarget ? `Kode ${editTarget.roleCode} bersifat tetap.` : undefined}
        onClose={() => setEditTarget(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setEditTarget(null)}>
              Batal
            </Button>
            <Button
              loading={editMutation.isPending}
              onClick={() => void editForm.handleSubmit((values) => editMutation.mutate(values))()}
            >
              Simpan perubahan
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <InputField
            label="Nama role"
            required
            error={editForm.formState.errors.roleName?.message}
            {...editForm.register('roleName')}
          />
          <TextareaField label="Deskripsi" {...editForm.register('description')} />
          <SelectField
            label="Status"
            required
            disabled={editingRole?.roleCode === 'SYSTEM_ADMIN'}
            {...editForm.register('status')}
          >
            <option value="ACTIVE">Aktif</option>
            <option value="INACTIVE">Nonaktif</option>
          </SelectField>
          {editingRole?.roleCode === 'SYSTEM_ADMIN' ? (
            <p className="text-xs text-ink-secondary">
              Status role sistem tidak dapat dinonaktifkan dari antarmuka.
            </p>
          ) : null}
          <p className="text-xs text-ink-secondary">
            Role nonaktif tetap tersimpan pada histori penugasan tetapi tidak memberi capability
            baru.
          </p>
        </form>
      </Dialog>
    </>
  );
}

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}

