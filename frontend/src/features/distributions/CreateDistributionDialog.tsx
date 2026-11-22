import { useMutation, useQuery } from '@tanstack/react-query';
import { useDeferredValue, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import type { AccessPrincipalOption } from '../../shared/api/types';
import { Alert } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { InputField, TextareaField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { searchAccessDirectory } from '../access/api';
import { createDistribution } from './api';
import type { DistributionDetail } from './api';
import {
  DistributionDocumentPicker,
  type DistributionDocumentSelection,
} from './DistributionDocumentPicker';

interface CreateDistributionDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (detail: DistributionDetail) => void;
}

function PrincipalToggleList({
  options,
  selectedIds,
  onToggle,
  emptyText,
}: {
  options: AccessPrincipalOption[];
  selectedIds: string[];
  onToggle: (option: AccessPrincipalOption) => void;
  emptyText: string;
}) {
  if (options.length === 0) {
    return <p className="px-3 py-2.5 text-xs text-ink-secondary">{emptyText}</p>;
  }
  return (
    <ul className="max-h-44 overflow-y-auto py-1" aria-label="Pilihan penerima">
      {options.map((option) => {
        const isSelected = selectedIds.includes(option.id);
        return (
          <li key={option.id}>
            <button
              type="button"
              onClick={() => onToggle(option)}
              aria-pressed={isSelected}
              className={cn(
                'flex w-full items-center gap-2 px-3 py-2 text-left text-xs hover:bg-surface',
                isSelected ? 'bg-primary/5' : null,
              )}
            >
              <span
                className={cn(
                  'flex size-4 shrink-0 items-center justify-center rounded border',
                  isSelected
                    ? 'border-primary bg-primary text-white'
                    : 'border-line-strong bg-surface',
                )}
                aria-hidden="true"
              >
                {isSelected ? <Icon name="check" size={11} /> : null}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-semibold text-ink">{option.name}</span>
                <span className="block truncate text-ink-secondary">{option.detail}</span>
              </span>
            </button>
          </li>
        );
      })}
    </ul>
  );
}

export function CreateDistributionDialog({
  open,
  onClose,
  onCreated,
}: CreateDistributionDialogProps) {
  const { notify } = useToast();
  const [title, setTitle] = useState('');
  const [message, setMessage] = useState('');
  const [expiresAt, setExpiresAt] = useState('');
  const [documents, setDocuments] = useState<DistributionDocumentSelection[]>([]);
  const [selectedUsers, setSelectedUsers] = useState<AccessPrincipalOption[]>([]);
  const [selectedUnits, setSelectedUnits] = useState<AccessPrincipalOption[]>([]);
  const [userSearch, setUserSearch] = useState('');
  const [unitSearch, setUnitSearch] = useState('');
  const deferredUserSearch = useDeferredValue(userSearch);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const directoryQuery = useQuery({
    queryKey: ['access-directory', deferredUserSearch],
    queryFn: () => searchAccessDirectory(deferredUserSearch),
    enabled: open,
  });

  const unitTerm = unitSearch.trim().toLowerCase();
  const unitOptions = (directoryQuery.data?.units ?? []).filter((unit) => {
    if (!unitTerm) {
      return true;
    }
    return (
      unit.name.toLowerCase().includes(unitTerm) || unit.detail.toLowerCase().includes(unitTerm)
    );
  });

  function toggleUser(option: AccessPrincipalOption) {
    setSelectedUsers((current) =>
      current.some((entry) => entry.id === option.id)
        ? current.filter((entry) => entry.id !== option.id)
        : [...current, option],
    );
    setFieldErrors((current) => ({ ...current, recipientUserIds: '' }));
  }

  function toggleUnit(option: AccessPrincipalOption) {
    setSelectedUnits((current) =>
      current.some((entry) => entry.id === option.id)
        ? current.filter((entry) => entry.id !== option.id)
        : [...current, option],
    );
    setFieldErrors((current) => ({ ...current, recipientUserIds: '' }));
  }

  function reset() {
    setTitle('');
    setMessage('');
    setExpiresAt('');
    setDocuments([]);
    setSelectedUsers([]);
    setSelectedUnits([]);
    setUserSearch('');
    setUnitSearch('');
    setFieldErrors({});
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: createDistribution,
    onSuccess: (detail) => {
      notify('Distribusi terkirim dan akses penerima telah dicatat.');
      reset();
      onCreated(detail);
    },
    onError: (error) => {
      setFormError(apiMessage(error));
      if (error instanceof ApiError) {
        const next: Record<string, string> = {};
        for (const item of error.fieldErrors) {
          const field = item.field === 'recipientOrganizationUnitIds' ? 'recipientUserIds' : item.field;
          next[field] = item.message;
        }
        setFieldErrors(next);
      }
    },
  });

  function close() {
    if (mutation.isPending) {
      return;
    }
    reset();
    onClose();
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!title.trim()) {
      errors.title = 'Judul distribusi wajib diisi.';
    }
    if (documents.length === 0) {
      errors.items = 'Pilih minimal satu dokumen untuk didistribusikan.';
    }
    if (selectedUsers.length === 0 && selectedUnits.length === 0) {
      errors.recipientUserIds = 'Pilih minimal satu penerima distribusi.';
    }
    if (expiresAt && new Date(expiresAt).getTime() <= Date.now()) {
      errors.expiresAt = 'Waktu kedaluwarsa harus di masa depan.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setFormError(null);
    mutation.mutate({
      title: title.trim(),
      message: message.trim() || undefined,
      expiresAt: expiresAt ? new Date(expiresAt).toISOString() : undefined,
      recipientUserIds: selectedUsers.map((user) => user.id),
      recipientOrganizationUnitIds: selectedUnits.map((unit) => unit.id),
      items: documents.map((item) => ({
        documentId: item.documentId,
        accessLevel: item.accessLevel,
      })),
    });
  }

  const selectedRecipients = [...selectedUsers, ...selectedUnits];

  return (
    <Dialog
      open={open}
      title="Kirim distribusi"
      description="Distribusi memberi akses sementara sesuai tingkat yang dipilih dan dapat dicabut kembali."
      onClose={close}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={mutation.isPending}>
            Batal
          </Button>
          <Button type="submit" form="create-distribution-form" loading={mutation.isPending}>
            Kirim distribusi
          </Button>
        </>
      }
    >
      <form id="create-distribution-form" noValidate className="space-y-5" onSubmit={submit}>
        {formError ? <Alert tone="danger">{formError}</Alert> : null}

        <InputField
          label="Judul distribusi"
          required
          maxLength={200}
          value={title}
          error={fieldErrors.title}
          placeholder="Contoh: Paket dokumen rapat koordinasi Q4"
          onChange={(event) => setTitle(event.target.value)}
        />

        <TextareaField
          label="Pesan untuk penerima"
          rows={2}
          maxLength={4000}
          value={message}
          error={fieldErrors.message}
          helper="Opsional. Pesan tampil pada detail distribusi penerima."
          onChange={(event) => setMessage(event.target.value)}
        />

        <InputField
          label="Berlaku sampai"
          type="datetime-local"
          value={expiresAt}
          error={fieldErrors.expiresAt}
          helper="Kosongkan agar akses berlaku sampai dicabut secara manual."
          onChange={(event) => setExpiresAt(event.target.value)}
        />

        <DistributionDocumentPicker
          selected={documents}
          error={fieldErrors.items}
          onChange={(items) => {
            setDocuments(items);
            setFieldErrors((current) => ({ ...current, items: '' }));
          }}
        />

        <fieldset className="space-y-3">
          <legend className="text-xs font-semibold text-ink">
            Penerima
            <span className="ml-0.5 text-danger" aria-hidden="true">
              *
            </span>
          </legend>
          {fieldErrors.recipientUserIds ? (
            <p className="text-xs font-medium text-danger" role="alert">
              {fieldErrors.recipientUserIds}
            </p>
          ) : null}

          {selectedRecipients.length > 0 ? (
            <ul className="flex flex-wrap gap-1.5" aria-label="Penerima terpilih">
              {selectedRecipients.map((recipient) => (
                <li
                  key={recipient.id}
                  className="inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/5 py-1 pr-1.5 pl-3 text-xs font-medium text-primary"
                >
                  {recipient.name}
                  <button
                    type="button"
                    onClick={() =>
                      selectedUsers.some((user) => user.id === recipient.id)
                        ? toggleUser(recipient)
                        : toggleUnit(recipient)
                    }
                    aria-label={`Hapus ${recipient.name} dari penerima`}
                    className="rounded-full p-0.5 hover:bg-primary/10"
                  >
                    <Icon name="close" size={12} />
                  </button>
                </li>
              ))}
            </ul>
          ) : null}

          <div className="grid gap-4 lg:grid-cols-2">
            <div className="rounded-lg border border-line">
              <div className="border-b border-line px-3 py-2.5">
                <p className="text-xs font-semibold text-ink">Pengguna</p>
                <p className="text-[11px] text-ink-secondary">
                  Maksimal 20 pengguna aktif per pencarian.
                </p>
                <input
                  type="search"
                  aria-label="Cari pengguna penerima"
                  placeholder="Cari nama atau username"
                  value={userSearch}
                  onChange={(event) => setUserSearch(event.target.value)}
                  className="mt-2 h-9 w-full rounded-lg border border-line-strong bg-surface px-2.5 text-xs text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
                />
              </div>
              {directoryQuery.isPending ? (
                <p className="px-3 py-2.5 text-xs text-ink-secondary">Memuat direktori…</p>
              ) : directoryQuery.isError ? (
                <p className="px-3 py-2.5 text-xs text-danger">Direktori penerima tidak dapat dimuat.</p>
              ) : (
                <PrincipalToggleList
                  options={directoryQuery.data?.users ?? []}
                  selectedIds={selectedUsers.map((user) => user.id)}
                  onToggle={toggleUser}
                  emptyText="Tidak ada pengguna yang cocok."
                />
              )}
            </div>

            <div className="rounded-lg border border-line">
              <div className="border-b border-line px-3 py-2.5">
                <p className="text-xs font-semibold text-ink">Unit organisasi</p>
                <p className="text-[11px] text-ink-secondary">
                  Seluruh anggota aktif unit akan menerima distribusi.
                </p>
                <input
                  type="search"
                  aria-label="Cari unit organisasi penerima"
                  placeholder="Cari nama atau kode unit"
                  value={unitSearch}
                  onChange={(event) => setUnitSearch(event.target.value)}
                  className="mt-2 h-9 w-full rounded-lg border border-line-strong bg-surface px-2.5 text-xs text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
                />
              </div>
              {directoryQuery.isPending ? (
                <p className="px-3 py-2.5 text-xs text-ink-secondary">Memuat direktori…</p>
              ) : directoryQuery.isError ? (
                <p className="px-3 py-2.5 text-xs text-danger">Direktori penerima tidak dapat dimuat.</p>
              ) : (
                <PrincipalToggleList
                  options={unitOptions}
                  selectedIds={selectedUnits.map((unit) => unit.id)}
                  onToggle={toggleUnit}
                  emptyText="Tidak ada unit yang cocok."
                />
              )}
            </div>
          </div>

          <p className="text-xs text-ink-secondary">
            Tingkat akses tiap dokumen mengikuti akses Anda sendiri. Anggota unit yang tidak aktif
            saat pengiriman tidak menerima distribusi.
          </p>
        </fieldset>
      </form>
    </Dialog>
  );
}
