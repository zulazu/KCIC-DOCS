import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../../shared/api/client';
import type { OrganizationUnit } from '../../../shared/api/types';
import { Button } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { Alert } from '../../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../../shared/components/Field';
import { useToast } from '../../../shared/components/Toast';
import { listAllUnits, updateUnit } from '../api';
import { UNIT_TYPE_LABELS, expectedParentType, parentCandidates, typeLabel } from './unitLabels';

const editSchema = z.object({
  parentId: z.string(),
  unitName: z.string().trim().min(1, 'Nama unit wajib diisi.').max(160),
  description: z.string().max(4000),
  status: z.enum(['ACTIVE', 'INACTIVE']),
});
type EditUnitForm = z.infer<typeof editSchema>;

export function UnitEditDialog({
  unit,
  onClose,
}: {
  unit: OrganizationUnit | null;
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const unitsQuery = useQuery({ queryKey: ['organization-units', 'all'], queryFn: listAllUnits });

  const form = useForm<EditUnitForm>({
    resolver: zodResolver(editSchema),
    values: unit
      ? {
          parentId: unit.parentId ?? '',
          unitName: unit.unitName,
          description: unit.description ?? '',
          status: unit.status,
        }
      : { parentId: '', unitName: '', description: '', status: 'ACTIVE' },
  });

  const mutation = useMutation({
    mutationFn: (values: EditUnitForm) =>
      updateUnit(unit?.id ?? '', {
        parentId: values.parentId || null,
        unitType: unit?.unitType ?? null,
        unitName: values.unitName,
        description: values.description || undefined,
        status: values.status,
      }),
    onSuccess: (saved) => {
      notify(`Unit ${saved.unitName} diperbarui.`);
      void queryClient.invalidateQueries({ queryKey: ['organization-units'] });
      void queryClient.invalidateQueries({ queryKey: ['organization-unit-members'] });
      onClose();
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'parentId' || fieldError.field === 'unitName') {
            form.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  const requiredParent = unit?.unitType ? expectedParentType(unit.unitType) : null;
  const options = parentCandidates(unitsQuery.data ?? [], unit?.unitType ?? '', unit?.id);
  const apiError =
    mutation.error instanceof ApiError
      ? mutation.error.message
      : mutation.error
        ? 'Tindakan tidak dapat diproses. Coba lagi.'
        : null;

  return (
    <Dialog
      open={unit !== null}
      title="Ubah unit organisasi"
      description={unit ? `Kode ${unit.unitCode} bersifat tetap.` : undefined}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            Batal
          </Button>
          <Button
            loading={mutation.isPending}
            onClick={() => void form.handleSubmit((values) => mutation.mutate(values))()}
          >
            Simpan perubahan
          </Button>
        </>
      }
    >
      <form noValidate className="space-y-4">
        {apiError ? <Alert tone="danger">{apiError}</Alert> : null}
        <SelectField
          label="Unit induk"
          helper={
            requiredParent
              ? `Wajib berada di bawah ${UNIT_TYPE_LABELS[requiredParent]}.`
              : 'Kosongkan untuk unit tingkat atas atau pilih induk aktif.'
          }
          error={form.formState.errors.parentId?.message}
          {...form.register('parentId')}
        >
          <option value="">Tanpa unit induk</option>
          {options.map((option) => (
            <option key={option.id} value={option.id}>
              {option.unitName} ({option.unitCode}) · {typeLabel(option.unitType)}
            </option>
          ))}
        </SelectField>
        <InputField
          label="Nama unit"
          required
          error={form.formState.errors.unitName?.message}
          {...form.register('unitName')}
        />
        <TextareaField label="Deskripsi" {...form.register('description')} />
        <SelectField label="Status" required {...form.register('status')}>
          <option value="ACTIVE">Aktif</option>
          <option value="INACTIVE">Nonaktif</option>
        </SelectField>
        <p className="text-xs text-ink-secondary">
          Unit nonaktif tidak dapat dipilih untuk penugasan baru; data historis tetap tersimpan.
        </p>
      </form>
    </Dialog>
  );
}