import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import type { AclEffect, PrincipalType } from '../../shared/api/types';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Alert } from '../../shared/components/Feedback';
import { InputField, SelectField } from '../../shared/components/Field';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import {
  type AccessTarget,
  createAccessGrant,
  effectLabels,
  isFutureExpiry,
  permissionOptionsFor,
  principalTypeLabels,
  searchAccessDirectory,
  toIsoExpiry,
} from './api';

const grantSchema = z
  .object({
    principalType: z.enum(['USER', 'ROLE', 'ORGANIZATION_UNIT']),
    principalId: z.string().min(1, 'Principal wajib dipilih.'),
    permissionCode: z.string().min(1, 'Permission wajib dipilih.'),
    effect: z.enum(['ALLOW', 'DENY']),
    includeDescendants: z.boolean(),
    expiresAt: z.string(),
  })
  .superRefine((values, ctx) => {
    if (values.expiresAt && !isFutureExpiry(values.expiresAt)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ['expiresAt'],
        message: 'Waktu kedaluwarsa harus di masa depan.',
      });
    }
  });

type GrantForm = z.infer<typeof grantSchema>;

const defaults: GrantForm = {
  principalType: 'USER',
  principalId: '',
  permissionCode: '',
  effect: 'ALLOW',
  includeDescendants: false,
  expiresAt: '',
};

export function GrantDialog({
  target,
  onClose,
  onGranted,
}: {
  target: AccessTarget;
  onClose: () => void;
  onGranted: () => void;
}) {
  const { notify } = useToast();
  const [userSearch, setUserSearch] = useState('');
  const deferredSearch = useDeferredValue(userSearch);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const form = useForm<GrantForm>({ resolver: zodResolver(grantSchema), defaultValues: defaults });
  const principalType = form.watch('principalType');
  const effect = form.watch('effect');

  const directoryQuery = useQuery({
    queryKey: ['access-directory', deferredSearch],
    queryFn: () => searchAccessDirectory(deferredSearch),
  });

  const principalOptions =
    principalType === 'USER'
      ? (directoryQuery.data?.users ?? [])
      : principalType === 'ROLE'
        ? (directoryQuery.data?.roles ?? [])
        : (directoryQuery.data?.units ?? []);

  const mutation = useMutation({
    mutationFn: (values: GrantForm) =>
      createAccessGrant(target, {
        principalType: values.principalType as PrincipalType,
        principalId: values.principalId,
        permissionCode: values.permissionCode,
        effect: values.effect as AclEffect,
        includeDescendants: values.principalType === 'ORGANIZATION_UNIT' && values.includeDescendants,
        expiresAt: toIsoExpiry(values.expiresAt),
      }),
    onSuccess: (grant) => {
      notify(`Akses ${grant.permissionName ?? grant.permissionCode} dicatat.`);
      onGranted();
      onClose();
    },
    onError: (error) => {
      setSubmitError(apiMessage(error));
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (
            fieldError.field === 'principalId' ||
            fieldError.field === 'permissionCode' ||
            fieldError.field === 'effect' ||
            fieldError.field === 'expiresAt'
          ) {
            form.setError(fieldError.field, { message: fieldError.message });
          }
        }
      }
    },
  });

  return (
    <Dialog
      open
      title="Beri akses"
      description="Grant manual dicatat pada audit trail. Penolakan eksplisit mengalahkan izin yang diwarisi."
      size="md"
      onClose={() => {
        if (!mutation.isPending) {
          onClose();
        }
      }}
      footer={
        <>
          <Button variant="secondary" disabled={mutation.isPending} onClick={onClose}>
            Batal
          </Button>
          <Button
            loading={mutation.isPending}
            onClick={() => void form.handleSubmit((values) => mutation.mutate(values))()}
          >
            Simpan grant
          </Button>
        </>
      }
    >
      <form noValidate className="space-y-4">
        {submitError ? <Alert tone="danger">{submitError}</Alert> : null}
        <SelectField
          label="Tipe principal"
          required
          error={form.formState.errors.principalType?.message}
          {...form.register('principalType', {
            onChange: () => {
              form.setValue('principalId', '');
              setSubmitError(null);
            },
          })}
        >
          {Object.entries(principalTypeLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </SelectField>

        {principalType === 'USER' ? (
          <InputField
            label="Cari user"
            placeholder="Nama atau username"
            value={userSearch}
            onChange={(event) => setUserSearch(event.target.value)}
            helper="Maksimal 20 user aktif ditampilkan. Persempit dengan kata kunci."
          />
        ) : null}

        <SelectField
          label="Principal"
          required
          error={form.formState.errors.principalId?.message}
          disabled={directoryQuery.isPending || directoryQuery.isError}
          {...form.register('principalId')}
        >
          <option value="">
            {directoryQuery.isPending ? 'Memuat…' : 'Pilih principal'}
          </option>
          {principalOptions.map((option) => (
            <option key={option.id} value={option.id}>
              {option.name} ({option.detail})
            </option>
          ))}
        </SelectField>
        {directoryQuery.isError ? (
          <Alert tone="danger">Direktori principal tidak dapat dimuat. Coba tutup dan buka ulang dialog.</Alert>
        ) : null}

        <SelectField
          label="Permission"
          required
          helper="Hanya capability yang berlaku pada resource ini yang dapat diberikan."
          error={form.formState.errors.permissionCode?.message}
          {...form.register('permissionCode')}
        >
          <option value="">Pilih permission</option>
          {permissionOptionsFor(target.type).map((option) => (
            <option key={option.code} value={option.code}>
              {option.label} ({option.code})
            </option>
          ))}
        </SelectField>

        <SelectField
          label="Efek"
          required
          helper={
            effect === 'DENY'
              ? 'Penolakan eksplisit mengalahkan allow dari role, folder, maupun workspace.'
              : 'Allow hanya berlaku selama grant aktif dan belum kedaluwarsa.'
          }
          error={form.formState.errors.effect?.message}
          {...form.register('effect')}
        >
          {Object.entries(effectLabels).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </SelectField>

        {principalType === 'ORGANIZATION_UNIT' ? (
          <label className="flex items-start gap-2 rounded-lg border border-line px-3 py-2 text-sm text-ink-secondary">
            <input
              type="checkbox"
              className="mt-0.5 size-4 rounded border-line-strong accent-[#8B1A1A]"
              {...form.register('includeDescendants')}
            />
            <span>
              Berlaku juga untuk unit turunan
              <span className="mt-0.5 block text-xs text-ink-muted">
                Opt-in. Tanpa opsi ini, grant hanya berlaku untuk anggota unit yang dipilih.
              </span>
            </span>
          </label>
        ) : null}

        <InputField
          label="Kedaluwarsa"
          type="datetime-local"
          error={form.formState.errors.expiresAt?.message}
          helper="Kosongkan untuk grant tanpa batas waktu."
          {...form.register('expiresAt')}
        />
      </form>
    </Dialog>
  );
}
