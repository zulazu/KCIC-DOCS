import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { OrganizationUnit, UnitType } from '../../../shared/api/types';
import { Badge, StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../../shared/components/Field';
import { Icon } from '../../../shared/components/Icon';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { formatDateTime } from '../../../shared/utils/format';
import {
  createUnit,
  listAllUnits,
  listUnitMembers,
  updateUnit,
} from '../api';

const UNIT_TYPE_LABELS: Record<UnitType, string> = {
  COMPANY: 'Perusahaan',
  DIRECTORATE: 'Direktorat',
  DEPARTMENT: 'Departemen',
  DIVISION: 'Biro/Division',
};

function expectedParentType(type: UnitType): UnitType | null {
  switch (type) {
    case 'DIRECTORATE':
      return 'COMPANY';
    case 'DEPARTMENT':
      return 'DIRECTORATE';
    case 'DIVISION':
      return 'DEPARTMENT';
    default:
      return null;
  }
}

function typeLabel(type: UnitType | null): string {
  return type ? UNIT_TYPE_LABELS[type] : 'Belum bertipe';
}

function parentCandidates(
  units: OrganizationUnit[],
  typeValue: string,
  excludeId?: string,
): OrganizationUnit[] {
  if (typeValue === 'COMPANY') {
    return [];
  }
  const required = typeValue === '' ? null : expectedParentType(typeValue as UnitType);
  return units.filter(
    (unit) =>
      unit.id !== excludeId &&
      unit.status === 'ACTIVE' &&
      (required === null || unit.unitType === required),
  );
}

const createSchema = z.object({
  parentId: z.string(),
  unitType: z.enum(['', 'COMPANY', 'DIRECTORATE', 'DEPARTMENT', 'DIVISION']),
  unitCode: z
    .string()
    .trim()
    .regex(
      /^[A-Za-z0-9._-]{2,50}$/,
      'Kode unit 2-50 karakter: huruf, angka, titik, garis bawah, atau strip.',
    ),
  unitName: z.string().trim().min(1, 'Nama unit wajib diisi.').max(160),
  description: z.string().max(4000),
});
type CreateUnitForm = z.infer<typeof createSchema>;

const editSchema = z.object({
  parentId: z.string(),
  unitType: z.enum(['', 'COMPANY', 'DIRECTORATE', 'DEPARTMENT', 'DIVISION']),
  unitName: z.string().trim().min(1, 'Nama unit wajib diisi.').max(160),
  description: z.string().max(4000),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});
type EditUnitForm = z.infer<typeof editSchema>;

function depthOf(unit: OrganizationUnit, byId: Map<string, OrganizationUnit>): number {
  let depth = 0;
  let current = unit;
  const seen = new Set<string>([unit.id]);
  while (current.parentId) {
    if (seen.has(current.parentId)) {
      break;
    }
    seen.add(current.parentId);
    const parent = byId.get(current.parentId);
    if (!parent) {
      break;
    }
    current = parent;
    depth += 1;
    if (depth > 20) {
      break;
    }
  }
  return depth;
}

export function OrganizationPage() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [filter, setFilter] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<OrganizationUnit | null>(null);
  const [membersTarget, setMembersTarget] = useState<OrganizationUnit | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const unitsQuery = useQuery({ queryKey: ['organization-units', 'all'], queryFn: listAllUnits });

  const membersQuery = useQuery({
    queryKey: ['organization-unit-members', membersTarget?.id],
    queryFn: () => listUnitMembers(membersTarget?.id ?? ''),
    enabled: Boolean(membersTarget),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['organization-units'] });
    void queryClient.invalidateQueries({ queryKey: ['organization-unit-members'] });
  };

  const createForm = useForm<CreateUnitForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { parentId: '', unitType: '', unitCode: '', unitName: '', description: '' },
  });
  const createType = createForm.watch('unitType');
  const createMutation = useMutation({
    mutationFn: (values: CreateUnitForm) =>
      createUnit({
        parentId: values.parentId || null,
        unitType: values.unitType === '' ? null : values.unitType,
        unitCode: values.unitCode,
        unitName: values.unitName,
        description: values.description || undefined,
      }),
    onSuccess: (unit) => {
      notify(`Unit ${unit.unitName} berhasil dibuat.`);
      setCreateOpen(false);
      createForm.reset();
      setActionError(null);
      invalidate();
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (
            fieldError.field === 'unitCode' ||
            fieldError.field === 'unitName' ||
            fieldError.field === 'parentId'
          ) {
            createForm.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  const editForm = useForm<EditUnitForm>({
    resolver: zodResolver(editSchema),
    values: editTarget
      ? {
          parentId: editTarget.parentId ?? '',
          unitType: editTarget.unitType ?? '',
          unitName: editTarget.unitName,
          description: editTarget.description ?? '',
          status: editTarget.status,
        }
      : { parentId: '', unitType: '', unitName: '', description: '', status: 'ACTIVE' },
  });
  const editType = editForm.watch('unitType');
  const editMutation = useMutation({
    mutationFn: (values: EditUnitForm) =>
      updateUnit(editTarget?.id ?? '', {
        parentId: values.parentId || null,
        unitType: values.unitType === '' ? null : values.unitType,
        unitName: values.unitName,
        description: values.description || undefined,
        status: values.status,
      }),
    onSuccess: (unit) => {
      notify(`Unit ${unit.unitName} diperbarui.`);
      setEditTarget(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'parentId') {
            editForm.setError('parentId', { message: fieldError.message });
          }
        }
      }
    },
  });

  const byId = useMemo(
    () => new Map((unitsQuery.data ?? []).map((unit) => [unit.id, unit])),
    [unitsQuery.data],
  );

  const orderedUnits = useMemo(() => {
    const term = filter.trim().toLowerCase();
    return [...(unitsQuery.data ?? [])]
      .filter(
        (unit) =>
          !term ||
          unit.unitName.toLowerCase().includes(term) ||
          unit.unitCode.toLowerCase().includes(term),
      )
      .sort((a, b) => a.unitName.localeCompare(b.unitName, 'id'));
  }, [unitsQuery.data, filter]);

  const parentOptions = (typeValue: string, excludeId?: string) =>
    parentCandidates(unitsQuery.data ?? [], typeValue, excludeId);

  return (
    <>
      <PageHeader
        title="Organization"
        description="Hierarki unit sebagai scope penugasan role dan akses dokumen."
        action={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Tambah unit
          </Button>
        }
      />

      {actionError ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line px-5 py-4">
          <div className="w-full max-w-sm">
            <label htmlFor="unit-search" className="mb-1.5 block text-xs font-semibold text-ink">
              Cari unit
            </label>
            <div className="relative">
              <Icon
                name="search"
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-disabled"
                size={16}
              />
              <input
                id="unit-search"
                type="search"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                placeholder="Nama atau kode unit"
                className="h-10 w-full rounded-lg border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              />
            </div>
          </div>
          <p className="text-xs text-ink-secondary">
            {unitsQuery.data ? `${unitsQuery.data.length} unit terdaftar` : null}
          </p>
        </div>

        {unitsQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label="Memuat struktur organisasi…" />
          </div>
        ) : unitsQuery.isError ? (
          <ErrorState
            message={apiMessage(unitsQuery.error)}
            onRetry={() => void unitsQuery.refetch()}
          />
        ) : orderedUnits.length === 0 ? (
          <EmptyState
            title="Tidak ada unit"
            description="Ubah kata kunci pencarian atau tambah unit organisasi pertama."
          />
        ) : (
          <Table>
            <THead>
              <Th>Unit</Th>
              <Th>Status</Th>
              <Th className="hidden md:table-cell">Diperbarui</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {orderedUnits.map((unit) => {
                const depth = depthOf(unit, byId);
                return (
                  <Tr key={unit.id}>
                    <Td>
                      <div style={{ paddingLeft: `${Math.min(depth, 8) * 18}px` }}>
                        <div className="flex flex-wrap items-center gap-2">
                          {depth > 0 ? (
                            <Icon name="chevron-right" size={13} className="text-ink-disabled" />
                          ) : null}
                          <span className="font-semibold text-ink">{unit.unitName}</span>
                          <Badge tone={unit.unitType ? 'gold' : 'neutral'}>
                            {typeLabel(unit.unitType)}
                          </Badge>
                          <span className="font-mono text-[11px] text-ink-secondary">
                            {unit.unitCode}
                          </span>
                        </div>
                        <p className="text-xs text-ink-secondary">
                          {unit.parentId
                            ? `Di bawah ${byId.get(unit.parentId)?.unitName ?? 'unit tidak ditemukan'}`
                            : 'Unit tingkat atas'}
                          {unit.description ? ` · ${unit.description}` : ''}
                        </p>
                      </div>
                    </Td>
                    <Td>
                      <StatusBadge
                        status={unit.status}
                        label={unit.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}
                      />
                    </Td>
                    <Td className="hidden text-xs text-ink-secondary tabular-nums md:table-cell">
                      {formatDateTime(unit.updatedAt)}
                    </Td>
                    <Td>
                      <div className="flex justify-end gap-1.5 whitespace-nowrap">
                        <Button
                          size="compact"
                          variant="secondary"
                          onClick={() => setMembersTarget(unit)}
                        >
                          Anggota
                        </Button>
                        <Button size="compact" variant="secondary" onClick={() => setEditTarget(unit)}>
                          Ubah
                        </Button>
                      </div>
                    </Td>
                  </Tr>
                );
              })}
            </tbody>
          </Table>
        )}
      </Card>

      <Dialog
        open={createOpen}
        title="Tambah unit organisasi"
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
              Buat unit
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <SelectField
            label="Tipe unit"
            helper="Biro/Division adalah tingkat terbawah. Pilih “Belum bertipe” hanya untuk unit lama yang belum dipetakan."
            {...createForm.register('unitType', {
              onChange: () => createForm.setValue('parentId', ''),
            })}
          >
            <option value="">Belum bertipe (unit lama)</option>
            <option value="COMPANY">Perusahaan</option>
            <option value="DIRECTORATE">Direktorat</option>
            <option value="DEPARTMENT">Departemen</option>
            <option value="DIVISION">Biro/Division</option>
          </SelectField>
          <SelectField
            label="Unit induk"
            helper={
              createType === 'COMPANY'
                ? 'Perusahaan harus menjadi unit akar tanpa induk.'
                : createType === ''
                  ? 'Kosongkan untuk unit tingkat atas atau pilih induk aktif.'
                  : `Wajib berada di bawah ${UNIT_TYPE_LABELS[expectedParentType(createType) as UnitType]}.`
            }
            error={createForm.formState.errors.parentId?.message}
            {...createForm.register('parentId')}
          >
            <option value="">Tanpa unit induk</option>
            {parentOptions(createType).map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName} ({unit.unitCode}) · {typeLabel(unit.unitType)}
              </option>
            ))}
          </SelectField>
          <InputField
            label="Kode unit"
            required
            helper="Kode tidak dapat diubah setelah dibuat. Contoh: OPS-SEC"
            error={createForm.formState.errors.unitCode?.message}
            {...createForm.register('unitCode')}
          />
          <InputField
            label="Nama unit"
            required
            error={createForm.formState.errors.unitName?.message}
            {...createForm.register('unitName')}
          />
          <TextareaField
            label="Deskripsi"
            error={createForm.formState.errors.description?.message}
            {...createForm.register('description')}
          />
        </form>
      </Dialog>

      <Dialog
        open={editTarget !== null}
        title="Ubah unit organisasi"
        description={editTarget ? `Kode ${editTarget.unitCode} bersifat tetap.` : undefined}
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
          <SelectField
            label="Tipe unit"
            helper="Tipe hanya dapat diubah selama belum ada anggota aktif dan tipe turunan sesuai hierarki."
            {...editForm.register('unitType', {
              onChange: () => editForm.setValue('parentId', ''),
            })}
          >
            <option value="">Belum bertipe (unit lama)</option>
            <option value="COMPANY">Perusahaan</option>
            <option value="DIRECTORATE">Direktorat</option>
            <option value="DEPARTMENT">Departemen</option>
            <option value="DIVISION">Biro/Division</option>
          </SelectField>
          <SelectField
            label="Unit induk"
            helper={
              editType === 'COMPANY'
                ? 'Perusahaan harus menjadi unit akar tanpa induk.'
                : editType === ''
                  ? 'Kosongkan untuk unit tingkat atas atau pilih induk aktif.'
                  : `Wajib berada di bawah ${UNIT_TYPE_LABELS[expectedParentType(editType) as UnitType]}.`
            }
            error={editForm.formState.errors.parentId?.message}
            {...editForm.register('parentId')}
          >
            <option value="">Tanpa unit induk</option>
            {parentOptions(editType, editTarget?.id).map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName} ({unit.unitCode}) · {typeLabel(unit.unitType)}
              </option>
            ))}
          </SelectField>
          <InputField
            label="Nama unit"
            required
            error={editForm.formState.errors.unitName?.message}
            {...editForm.register('unitName')}
          />
          <TextareaField label="Deskripsi" {...editForm.register('description')} />
          <SelectField label="Status" required {...editForm.register('status')}>
            <option value="ACTIVE">Aktif</option>
            <option value="INACTIVE">Nonaktif</option>
          </SelectField>
          <p className="text-xs text-ink-secondary">
            Unit nonaktif tidak dapat dipilih untuk penugasan baru; data historis tetap tersimpan.
          </p>
        </form>
      </Dialog>

      <Dialog
        open={membersTarget !== null}
        title="Anggota unit"
        description={membersTarget ? `${membersTarget.unitName} (${membersTarget.unitCode})` : undefined}
        size="lg"
        onClose={() => setMembersTarget(null)}
      >
        {membersQuery.isPending ? (
          <LoadingBlock label="Memuat anggota unit…" />
        ) : membersQuery.isError ? (
          <ErrorState
            message={apiMessage(membersQuery.error)}
            onRetry={() => void membersQuery.refetch()}
          />
        ) : !membersQuery.data || membersQuery.data.length === 0 ? (
          <EmptyState
            title="Belum ada anggota"
            description="Tambahkan keanggotaan melalui halaman detail pengguna."
          />
        ) : (
          <ul className="divide-y divide-line rounded-lg border border-line">
            {membersQuery.data.map((member) => (
              <li key={member.assignmentId} className="flex flex-wrap items-center justify-between gap-2 px-4 py-3">
                <div>
                  <p className="text-sm font-semibold text-ink">{member.displayName}</p>
                  <p className="text-xs text-ink-secondary">
                    <span className="font-mono">{member.username}</span> ·{' '}
                    {member.assignmentType === 'PRIMARY' ? 'Utama' : 'Tambahan'}
                  </p>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={member.active ? 'success' : 'neutral'}>
                    {member.active ? 'Aktif' : 'Berakhir'}
                  </Badge>
                  <LinkToUser userId={member.userId} />
                </div>
              </li>
            ))}
          </ul>
        )}
      </Dialog>
    </>
  );
}

function LinkToUser({ userId }: { userId: string }) {
  return (
    <ButtonLink to={`/admin/users/${userId}`} variant="secondary" size="compact">
      Kelola
      <Icon name="chevron-right" size={14} />
    </ButtonLink>
  );
}

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}
