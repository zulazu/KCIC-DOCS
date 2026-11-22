import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { ReactNode } from 'react';
import { ApiError } from '../../shared/api/client';
import { StatusBadge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { Card } from '../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../shared/components/Table';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDateTime } from '../../shared/utils/format';

export interface MasterField {
  key: string;
  label: string;
  type: 'text' | 'number' | 'textarea' | 'select' | 'checkbox';
  required?: boolean;
  helper?: string;
  placeholder?: string;
  min?: number;
  max?: number;
  pattern?: { value: RegExp; message: string };
  options?: { value: string; label: string }[];
}

export interface MasterPanelConfig<T> {
  key: string;
  title: string;
  description: string;
  list: () => Promise<T[]>;
  create: (payload: Record<string, unknown>) => Promise<T>;
  update: (id: string, payload: Record<string, unknown>) => Promise<T>;
  codeOf: (item: T) => string;
  nameOf: (item: T) => string;
  statusOf: (item: T) => boolean;
  meta: (item: T, all: T[]) => ReactNode;
  fields: (mode: 'create' | 'edit', item: T | null, all: T[]) => MasterField[];
  extraColumns?: { header: string; render: (item: T) => ReactNode }[];
}

type FormValues = Record<string, string | boolean>;

function initialValues(fields: MasterField[], item: Record<string, unknown> | null): FormValues {
  const values: FormValues = {};
  for (const field of fields) {
    if (field.type === 'checkbox') {
      values[field.key] = item ? Boolean(item[field.key]) : true;
    } else if (item && item[field.key] !== null && item[field.key] !== undefined) {
      values[field.key] = String(item[field.key]);
    } else {
      values[field.key] = field.options && field.options.length > 0 ? field.options[0]!.value : '';
    }
  }
  return values;
}

export function MasterDataPanel<T extends { id: string; updatedAt: string }>({
  config,
}: {
  config: MasterPanelConfig<T>;
}) {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [filter, setFilter] = useState('');
  const [dialogMode, setDialogMode] = useState<'create' | 'edit' | null>(null);
  const [editTarget, setEditTarget] = useState<T | null>(null);
  const [values, setValues] = useState<FormValues>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);

  const listQuery = useQuery({ queryKey: ['master-data', config.key], queryFn: config.list });

  const mutation = useMutation({
    mutationFn: (payload: Record<string, unknown>) =>
      dialogMode === 'edit' && editTarget
        ? config.update(editTarget.id, payload)
        : config.create(payload),
    onSuccess: (item) => {
      notify(`${config.nameOf(item)} berhasil disimpan.`);
      setDialogMode(null);
      setEditTarget(null);
      setActionError(null);
      setFieldErrors({});
      void queryClient.invalidateQueries({ queryKey: ['master-data', config.key] });
      void queryClient.invalidateQueries({ queryKey: ['master-data', 'lookup'] });
    },
    onError: (error) => {
      setActionError(error instanceof ApiError ? error.message : 'Data tidak dapat disimpan.');
      if (error instanceof ApiError) {
        setFieldErrors(
          Object.fromEntries(error.fieldErrors.map((item) => [item.field, item.message])),
        );
      }
    },
  });

  const openCreate = () => {
    const fields = config.fields('create', null, listQuery.data ?? []);
    setValues(initialValues(fields, null));
    setFieldErrors({});
    setActionError(null);
    setEditTarget(null);
    setDialogMode('create');
  };

  const openEdit = (item: T) => {
    const fields = config.fields('edit', item, listQuery.data ?? []);
    setValues(initialValues(fields, item as Record<string, unknown>));
    setFieldErrors({});
    setActionError(null);
    setEditTarget(item);
    setDialogMode('edit');
  };

  const submit = () => {
    if (!dialogMode) {
      return;
    }
    const fields = config.fields(dialogMode, editTarget, listQuery.data ?? []);
    const errors: Record<string, string> = {};
    const payload: Record<string, unknown> = {};
    for (const field of fields) {
      const raw = values[field.key];
      if (field.type === 'checkbox') {
        payload[field.key] = Boolean(raw);
        continue;
      }
      const text = String(raw ?? '').trim();
      if (field.required && text === '') {
        errors[field.key] = `${field.label} wajib diisi.`;
        continue;
      }
      if (field.pattern && text !== '' && !field.pattern.value.test(text)) {
        errors[field.key] = field.pattern.message;
        continue;
      }
      if (field.type === 'number') {
        const number = Number(text);
        if (Number.isNaN(number) || number < (field.min ?? 0) || number > (field.max ?? 1_000_000)) {
          errors[field.key] = `${field.label} tidak valid.`;
          continue;
        }
        payload[field.key] = number;
      } else if (field.key.toLowerCase().endsWith('id')) {
        payload[field.key] = text === '' ? null : text;
      } else if (text === '') {
        payload[field.key] = null;
      } else {
        payload[field.key] = text;
      }
    }
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    mutation.mutate(payload);
  };

  const items = (listQuery.data ?? []).filter((item) => {
    const term = filter.trim().toLowerCase();
    return (
      !term ||
      config.nameOf(item).toLowerCase().includes(term) ||
      config.codeOf(item).toLowerCase().includes(term)
    );
  });
  const fields = dialogMode ? config.fields(dialogMode, editTarget, listQuery.data ?? []) : [];

  return (
    <>
      {actionError && !dialogMode ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      <Card>
        <div className="flex flex-wrap items-end justify-between gap-3 border-b border-line px-5 py-4">
          <div className="w-full max-w-sm">
            <label htmlFor={`${config.key}-search`} className="mb-1.5 block text-xs font-semibold text-ink">
              Cari {config.title.toLowerCase()}
            </label>
            <div className="relative">
              <Icon
                name="search"
                className="pointer-events-none absolute top-1/2 left-3 -translate-y-1/2 text-ink-disabled"
                size={16}
              />
              <input
                id={`${config.key}-search`}
                type="search"
                value={filter}
                onChange={(event) => setFilter(event.target.value)}
                placeholder="Nama atau kode"
                className="h-10 w-full rounded-lg border border-line-strong bg-surface pr-3 pl-9 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
              />
            </div>
          </div>
          <div className="flex items-center gap-3">
            <p className="text-xs text-ink-secondary">
              {listQuery.data ? `${listQuery.data.length} data terdaftar` : null}
            </p>
            <Button onClick={openCreate} size="compact">
              <Icon name="plus" size={14} />
              Tambah
            </Button>
          </div>
        </div>

        {listQuery.isPending ? (
          <div className="px-5 py-4">
            <LoadingBlock />
          </div>
        ) : listQuery.isError ? (
          <ErrorState
            message={apiMessage(listQuery.error)}
            onRetry={() => void listQuery.refetch()}
          />
        ) : items.length === 0 ? (
          <EmptyState
            title="Belum ada data"
            description="Tambahkan data master pertama atau ubah kata kunci pencarian."
            action={<Button onClick={openCreate}>Tambah data</Button>}
          />
        ) : (
          <Table>
              <THead>
                <Th>Nama</Th>
                {(config.extraColumns ?? []).map((column) => (
                  <Th key={column.header} className="hidden md:table-cell">
                    {column.header}
                  </Th>
                ))}
                <Th>Status</Th>
                <Th className="hidden lg:table-cell">Diperbarui</Th>
                <Th align="right">Aksi</Th>
              </THead>
              <tbody>
                {items.map((item) => (
                  <Tr key={item.id}>
                    <Td>
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-semibold text-ink">{config.nameOf(item)}</span>
                        <span className="font-mono text-[11px] text-ink-secondary">
                          {config.codeOf(item)}
                        </span>
                      </div>
                      <div className="text-xs text-ink-secondary">
                        {config.meta(item, listQuery.data ?? [])}
                      </div>
                    </Td>
                    {(config.extraColumns ?? []).map((column) => (
                      <Td key={column.header} className="hidden text-xs text-ink-secondary md:table-cell">
                        {column.render(item)}
                      </Td>
                    ))}
                    <Td>
                      <StatusBadge
                        status={config.statusOf(item) ? 'ACTIVE' : 'INACTIVE'}
                        label={config.statusOf(item) ? 'Aktif' : 'Nonaktif'}
                      />
                    </Td>
                    <Td className="hidden text-xs text-ink-secondary tabular-nums lg:table-cell">
                      {formatDateTime(item.updatedAt)}
                    </Td>
                    <Td>
                      <div className="flex justify-end whitespace-nowrap">
                        <Button size="compact" variant="secondary" onClick={() => openEdit(item)}>
                          Ubah
                        </Button>
                      </div>
                    </Td>
                  </Tr>
                ))}
              </tbody>
            </Table>
        )}
      </Card>

      <Dialog
        open={dialogMode !== null}
        title={dialogMode === 'create' ? `Tambah ${config.title.toLowerCase()}` : `Ubah ${config.title.toLowerCase()}`}
        description={
          dialogMode === 'edit' ? `Kode ${editTarget ? config.codeOf(editTarget) : ''} bersifat tetap.` : undefined
        }
        onClose={() => setDialogMode(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setDialogMode(null)}>
              Batal
            </Button>
            <Button loading={mutation.isPending} onClick={submit}>
              {dialogMode === 'create' ? 'Simpan data' : 'Simpan perubahan'}
            </Button>
          </>
        }
      >
        <form
          noValidate
          className="space-y-4"
          onSubmit={(event) => {
            event.preventDefault();
            submit();
          }}
        >
          {actionError ? <Alert tone="danger">{actionError}</Alert> : null}
          {fields.map((field) => {
            const error = fieldErrors[field.key];
            if (field.type === 'checkbox') {
              return (
                <label key={field.key} className="flex items-start gap-2.5 text-sm text-ink">
                  <input
                    type="checkbox"
                    checked={Boolean(values[field.key])}
                    onChange={(event) =>
                      setValues((current) => ({ ...current, [field.key]: event.target.checked }))
                    }
                    className="mt-0.5 size-4 rounded border-line-strong text-primary accent-[#8B1A1A]"
                  />
                  <span>
                    <span className="font-semibold">{field.label}</span>
                    {field.helper ? (
                      <span className="block text-xs text-ink-secondary">{field.helper}</span>
                    ) : null}
                  </span>
                </label>
              );
            }
            if (field.type === 'select') {
              return (
                <SelectField
                  key={field.key}
                  label={field.label}
                  required={field.required}
                  helper={field.helper}
                  error={error}
                  value={String(values[field.key] ?? '')}
                  onChange={(event) =>
                    setValues((current) => ({ ...current, [field.key]: event.target.value }))
                  }
                >
                  {(field.options ?? []).map((option) => (
                    <option key={option.value} value={option.value}>
                      {option.label}
                    </option>
                  ))}
                </SelectField>
              );
            }
            if (field.type === 'textarea') {
              return (
                <TextareaField
                  key={field.key}
                  label={field.label}
                  required={field.required}
                  helper={field.helper}
                  error={error}
                  value={String(values[field.key] ?? '')}
                  onChange={(event) =>
                    setValues((current) => ({ ...current, [field.key]: event.target.value }))
                  }
                />
              );
            }
            return (
              <InputField
                key={field.key}
                label={field.label}
                required={field.required}
                helper={field.helper}
                error={error}
                placeholder={field.placeholder}
                type={field.type === 'number' ? 'number' : 'text'}
                min={field.min}
                max={field.max}
                value={String(values[field.key] ?? '')}
                onChange={(event) =>
                  setValues((current) => ({ ...current, [field.key]: event.target.value }))
                }
              />
            );
          })}
        </form>
      </Dialog>
    </>
  );
}
