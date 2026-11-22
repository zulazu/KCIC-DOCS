import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import type { DocumentSummary } from '../../shared/api/types';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { InputField, TextareaField } from '../../shared/components/Field';
import { Alert } from '../../shared/components/Feedback';
import { Icon } from '../../shared/components/Icon';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { listDocuments } from '../documents/api';
import { createTemplate } from './api';
import type { TemplateDetail } from './api';

interface CreateTemplateDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (detail: TemplateDetail) => void;
}

export function CreateTemplateDialog({ open, onClose, onCreated }: CreateTemplateDialogProps) {
  const { notify } = useToast();
  const [keyword, setKeyword] = useState('');
  const [document, setDocument] = useState<DocumentSummary | null>(null);
  const [templateCode, setTemplateCode] = useState('');
  const [templateName, setTemplateName] = useState('');
  const [description, setDescription] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const trimmedKeyword = keyword.trim();
  const searchQuery = useQuery({
    queryKey: ['documents', 'template-picker', trimmedKeyword],
    queryFn: () => listDocuments({ keyword: trimmedKeyword, size: 8 }),
    enabled: open && trimmedKeyword.length >= 2,
  });

  function reset() {
    setKeyword('');
    setDocument(null);
    setTemplateCode('');
    setTemplateName('');
    setDescription('');
    setFieldErrors({});
    setFormError(null);
  }

  const mutation = useMutation({
    mutationFn: createTemplate,
    onSuccess: (detail) => {
      notify('Template dibuat sebagai draft. Publikasikan setelah dokumen sumber siap.');
      reset();
      onCreated(detail);
    },
    onError: (error) => {
      setFormError(apiMessage(error));
      if (error instanceof ApiError) {
        const next: Record<string, string> = {};
        for (const item of error.fieldErrors) {
          next[item.field] = item.message;
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
    if (!document) {
      errors.documentId = 'Pilih dokumen sumber template.';
    }
    if (!templateCode.trim()) {
      errors.templateCode = 'Kode template wajib diisi.';
    }
    if (!templateName.trim()) {
      errors.templateName = 'Nama template wajib diisi.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || !document) {
      return;
    }
    setFormError(null);
    mutation.mutate({
      documentId: document.id,
      templateCode: templateCode.trim(),
      templateName: templateName.trim(),
      description: description.trim() || undefined,
    });
  }

  return (
    <Dialog
      open={open}
      title="Buat template"
      description="Template memakai dokumen dan versioning repository. File tidak digandakan ke tabel template."
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={mutation.isPending}>
            Batal
          </Button>
          <Button type="submit" form="create-template-form" loading={mutation.isPending}>
            Simpan draft
          </Button>
        </>
      }
    >
      <form id="create-template-form" className="space-y-4" onSubmit={submit}>
        {formError ? <Alert tone="danger">{formError}</Alert> : null}

        <div className="flex flex-col gap-1.5">
          <p className="text-xs font-semibold text-ink">
            Dokumen sumber
            <span className="ml-0.5 text-danger" aria-hidden="true">
              *
            </span>
          </p>
          {document ? (
            <div className="flex items-start gap-2 rounded-lg border border-line px-3 py-2.5">
              <span className="mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
                <Icon name="document" size={16} />
              </span>
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-ink">{document.title}</p>
                <p className="mt-0.5 truncate text-xs text-ink-secondary">
                  {document.documentCode ?? 'Tanpa kode'} · {document.workspaceName ?? 'Workspace'}
                </p>
              </div>
              <button
                type="button"
                onClick={() => setDocument(null)}
                className="rounded-md p-1.5 text-ink-secondary hover:bg-surface-muted hover:text-danger"
                aria-label="Ganti dokumen sumber"
              >
                <Icon name="close" size={14} />
              </button>
            </div>
          ) : (
            <>
              <InputField
                label="Cari dokumen"
                required
                placeholder="Ketik minimal 2 karakter"
                value={keyword}
                onChange={(event) => setKeyword(event.target.value)}
                error={fieldErrors.documentId}
                helper="Pilih dokumen aktif yang dapat Anda akses. Satu dokumen hanya menjadi satu template."
              />
              {trimmedKeyword.length >= 2 ? (
                <div className="rounded-lg border border-line bg-surface-muted/50">
                  {searchQuery.isLoading ? (
                    <p className="px-3 py-2.5 text-xs text-ink-secondary">Mencari dokumen…</p>
                  ) : searchQuery.error ? (
                    <p className="px-3 py-2.5 text-xs text-danger">
                      Pencarian dokumen gagal. Periksa kata kunci lalu coba lagi.
                    </p>
                  ) : (searchQuery.data?.items.length ?? 0) === 0 ? (
                    <p className="px-3 py-2.5 text-xs text-ink-secondary">
                      Tidak ada dokumen yang cocok. Pastikan dokumen sudah aktif di katalog.
                    </p>
                  ) : (
                    <ul
                      className="max-h-52 overflow-y-auto py-1"
                      aria-label="Hasil pencarian dokumen sumber"
                    >
                      {searchQuery.data?.items.map((item) => (
                        <li key={item.id}>
                          <button
                            type="button"
                            onClick={() => setDocument(item)}
                            className={cn(
                              'flex w-full items-start gap-2 px-3 py-2 text-left text-xs hover:bg-surface',
                            )}
                          >
                            <Icon name="document" size={14} className="mt-0.5 shrink-0 text-primary" />
                            <span className="min-w-0">
                              <span className="block truncate font-semibold text-ink">
                                {item.title}
                              </span>
                              <span className="block truncate text-ink-secondary">
                                {item.documentCode ?? 'Tanpa kode'} ·{' '}
                                {item.ownerOrganizationUnitName ?? '—'}
                              </span>
                            </span>
                          </button>
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              ) : null}
            </>
          )}
        </div>

        <div className="grid gap-4 sm:grid-cols-2">
          <InputField
            label="Kode template"
            required
            maxLength={80}
            placeholder="Contoh: TPL_SOP_KEAMANAN"
            value={templateCode}
            onChange={(event) => setTemplateCode(event.target.value)}
            error={fieldErrors.templateCode}
            helper="Kode unik, disimpan dalam huruf kapital."
          />
          <InputField
            label="Nama template"
            required
            maxLength={180}
            placeholder="Nama template resmi"
            value={templateName}
            onChange={(event) => setTemplateName(event.target.value)}
            error={fieldErrors.templateName}
          />
        </div>

        <TextareaField
          label="Deskripsi"
          maxLength={4000}
          rows={3}
          placeholder="Kegunaan template dan unit yang disarankan memakainya."
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          error={fieldErrors.description}
        />
      </form>
    </Dialog>
  );
}
