import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { OrganizationUnit, UnitType } from '../../../shared/api/types';
import { StatusBadge } from '../../../shared/components/Badge';
import { Button, ButtonLink } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../../shared/components/Field';
import { Icon } from '../../../shared/components/Icon';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { useToast } from '../../../shared/components/Toast';
import { formatDateTime } from '../../../shared/utils/format';
import { createUnit, listUnitsByType } from '../api';
import { UnitEditDialog } from './UnitEditDialog';
import { UNIT_TYPE_LABELS, expectedParentType } from './unitLabels';

type ManageableType = 'DIRECTORATE' | 'DEPARTMENT' | 'DIVISION';

const createSchema = z.object({
  parentId: z.string().min(1, 'Unit induk wajib dipilih.'),
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

function apiMessage(error: unknown): string {
  return error instanceof ApiError ? error.message : 'Tindakan tidak dapat diproses. Coba lagi.';
}

export function UnitTypePage({ unitType }: { unitType: ManageableType }) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const label = UNIT_TYPE_LABELS[unitType];
  const parentType = expectedParentType(unitType) as UnitType;
  const [filter, setFilter] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [editTarget, setEditTarget] = useState<OrganizationUnit | null>(null);

  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'type', unitType],
    queryFn: () => listUnitsByType(unitType),
  });
  const parentsQuery = useQuery({
    queryKey: ['organization-units', 'type', parentType],
    queryFn: () => listUnitsByType(parentType),
  });

  const createForm = useForm<CreateUnitForm>({
    resolver: zodResolver(createSchema),
    defaultValues: { parentId: '', unitCode: '', unitName: '', description: '' },
  });

  const createMutation = useMutation({
    mutationFn: (values: CreateUnitForm) =>
      createUnit({
        parentId: values.parentId,
        unitType,
        unitCode: values.unitCode,
        unitName: values.unitName,
        description: values.description || undefined,
      }),
    onSuccess: (unit) => {
      notify(`${label} ${unit.unitName} berhasil dibuat.`);
      setCreateOpen(false);
      createForm.reset();
      void queryClient.invalidateQueries({ queryKey: ['organization-units'] });
    },
    onError: (error) => {
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

  const parentById = useMemo(
    () => new Map((parentsQuery.data ?? []).map((unit) => [unit.id, unit])),
    [parentsQuery.data],
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

  const parentOptions = (parentsQuery.data ?? []).filter((unit) => unit.status === 'ACTIVE');

  return (
    <>
      <PageHeader
        title={label}
        description={`Daftar ${label.toLowerCase()} beserta unit induk dan statusnya.`}
        action={
          <Button onClick={() => setCreateOpen(true)}>
            <Icon name="plus" size={16} />
            Tambah {label.toLowerCase()}
          </Button>
        }
      />

      {createMutation.isError ? (
        <div className="mb-4">
          <Alert tone="danger">{apiMessage(createMutation.error)}</Alert>
        </div>
      ) : null}

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line px-5 py-4">
          <div className="w-full max-w-sm">
            <label htmlFor="unit-search" className="mb-1.5 block text-xs font-bold text-ink">
              Cari {label.toLowerCase()}
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
            {unitsQuery.data ? `${unitsQuery.data.length} ${label.toLowerCase()} terdaftar` : null}
          </p>
        </div>

        {unitsQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock label={`Memuat ${label.toLowerCase()}…`} />
          </div>
        ) : unitsQuery.isError ? (
          <ErrorState
            message={apiMessage(unitsQuery.error)}
            onRetry={() => void unitsQuery.refetch()}
          />
        ) : orderedUnits.length === 0 ? (
          <EmptyState
            title={`Belum ada ${label.toLowerCase()}`}
            description={
              filter
                ? 'Ubah kata kunci pencarian.'
                : `Tambahkan ${label.toLowerCase()} pertama di bawah ${UNIT_TYPE_LABELS[parentType].toLowerCase()}.`
            }
            action={
              filter ? undefined : (
                <Button onClick={() => setCreateOpen(true)}>
                  <Icon name="plus" size={16} />
                  Tambah {label.toLowerCase()}
                </Button>
              )
            }
          />
        ) : (
          <Table caption={`Daftar ${label.toLowerCase()}`}>
            <THead>
              <Th>Unit</Th>
              <Th className="hidden md:table-cell">Induk</Th>
              <Th>Status</Th>
              <Th className="hidden lg:table-cell">Diperbarui</Th>
              <Th align="right">Aksi</Th>
            </THead>
            <tbody>
              {orderedUnits.map((unit) => (
                <Tr key={unit.id}>
                  <Td>
                    <span className="block font-semibold text-ink">{unit.unitName}</span>
                    <span className="font-mono text-[11px] text-ink-secondary">{unit.unitCode}</span>
                  </Td>
                  <Td className="hidden md:table-cell">
                    {unit.parentId ? (
                      <span className="text-sm text-ink">
                        {parentById.get(unit.parentId)?.unitName ?? 'Unit tidak ditemukan'}
                      </span>
                    ) : (
                      <span className="text-sm text-ink-secondary">Tanpa induk</span>
                    )}
                  </Td>
                  <Td>
                    <StatusBadge
                      status={unit.status}
                      label={unit.status === 'ACTIVE' ? 'Aktif' : 'Nonaktif'}
                    />
                  </Td>
                  <Td className="hidden text-xs text-ink-secondary tabular-nums lg:table-cell">
                    {formatDateTime(unit.updatedAt)}
                  </Td>
                  <Td>
                    <div className="flex justify-end gap-1.5 whitespace-nowrap">
                      <Button
                        size="compact"
                        variant="secondary"
                        onClick={() => setEditTarget(unit)}
                      >
                        Ubah
                      </Button>
                      <ButtonLink
                        to={`/admin/organization/units/${unit.id}`}
                        variant="secondary"
                        size="compact"
                      >
                        Detail
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
        title={`Tambah ${label.toLowerCase()}`}
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
              Buat {label.toLowerCase()}
            </Button>
          </>
        }
      >
        <form noValidate className="space-y-4">
          <SelectField
            label={`Unit induk (${UNIT_TYPE_LABELS[parentType].toLowerCase()})`}
            required
            helper={`${label} wajib berada di bawah ${UNIT_TYPE_LABELS[parentType].toLowerCase()} yang aktif.`}
            error={createForm.formState.errors.parentId?.message}
            {...createForm.register('parentId')}
          >
            <option value="">Pilih unit induk</option>
            {parentOptions.map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName} ({unit.unitCode})
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

      <UnitEditDialog unit={editTarget} onClose={() => setEditTarget(null)} />
    </>
  );
}

export function DirectoratesPage() {
  return <UnitTypePage unitType="DIRECTORATE" />;
}

export function DepartmentsPage() {
  return <UnitTypePage unitType="DEPARTMENT" />;
}

export function DivisionsPage() {
  return <UnitTypePage unitType="DIVISION" />;
}