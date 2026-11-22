import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { ApiError } from '../../shared/api/client';
import type {
  DocumentCategory,
  DocumentClassification,
  DocumentDetail,
  DocumentTag,
  DocumentType,
  OrganizationUnit,
  Workspace,
} from '../../shared/api/types';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Alert } from '../../shared/components/Feedback';
import { InputField, SelectField, TextareaField } from '../../shared/components/Field';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { listFolders } from '../workspaces/api';
import { createDocument, updateDocument } from './api';
import type { DocumentPayload } from './api';

const schema = z.object({
  workspaceId: z.string().min(1, 'Workspace wajib dipilih.'),
  title: z.string().trim().min(1, 'Judul dokumen wajib diisi.').max(240),
  documentCode: z.string().trim().max(100),
  description: z.string().max(4000),
  folderId: z.string(),
  categoryId: z.string().min(1, 'Kategori wajib dipilih.'),
  documentTypeId: z.string().min(1, 'Jenis dokumen wajib dipilih.'),
  classificationId: z.string().min(1, 'Klasifikasi wajib dipilih.'),
  ownerSelection: z.string(),
  ownerChangeReason: z.string().max(500),
  effectiveDate: z.string(),
  reviewDate: z.string(),
  expiryDate: z.string(),
});

type DocumentForm = z.infer<typeof schema>;

interface DocumentFormDialogProps {
  open: boolean;
  mode: 'create' | 'edit';
  document?: DocumentDetail;
  defaultWorkspaceId?: string;
  workspaces: Workspace[];
  units: OrganizationUnit[];
  categories: DocumentCategory[];
  types: DocumentType[];
  classifications: DocumentClassification[];
  tags: DocumentTag[];
  onClose: () => void;
  onSaved: (detail: DocumentDetail, mode: 'create' | 'edit') => void;
}

function initialSelection(document?: DocumentDetail): string {
  if (!document) {
    return '';
  }
  if (document.summary.ownerOrganizationUnitId) {
    return `unit:${document.summary.ownerOrganizationUnitId}`;
  }
  if (document.summary.ownerUserId) {
    return `user:${document.summary.ownerUserId}`;
  }
  return '';
}

export function DocumentFormDialog({
  open,
  mode,
  document,
  defaultWorkspaceId,
  workspaces,
  units,
  categories,
  types,
  classifications,
  tags,
  onClose,
  onSaved,
}: DocumentFormDialogProps) {
  const { notify } = useToast();
  const [tagIds, setTagIds] = useState<string[]>([]);
  const [actionError, setActionError] = useState<string | null>(null);

  const form = useForm<DocumentForm>({
    resolver: zodResolver(schema),
    defaultValues: {
      workspaceId: '',
      title: '',
      documentCode: '',
      description: '',
      folderId: '',
      categoryId: '',
      documentTypeId: '',
      classificationId: '',
      ownerSelection: '',
      ownerChangeReason: '',
      effectiveDate: '',
      reviewDate: '',
      expiryDate: '',
    },
  });

  const workspaceId = form.watch('workspaceId');
  const ownerSelection = form.watch('ownerSelection');

  useEffect(() => {
    if (!open) {
      return;
    }
    form.reset({
      workspaceId: document?.summary.workspaceId ?? defaultWorkspaceId ?? '',
      title: document?.summary.title ?? '',
      documentCode: document?.summary.documentCode ?? '',
      description: document?.description ?? '',
      folderId: document?.summary.folderId ?? '',
      categoryId: document?.summary.categoryId ?? '',
      documentTypeId: document?.summary.documentTypeId ?? '',
      classificationId: document?.summary.classificationId ?? '',
      ownerSelection: initialSelection(document),
      ownerChangeReason: '',
      effectiveDate: document?.effectiveDate ?? '',
      reviewDate: document?.reviewDate ?? '',
      expiryDate: document?.expiryDate ?? '',
    });
    setTagIds(document?.summary.tags.map((tag) => tag.id) ?? []);
    setActionError(null);
  }, [open, document, defaultWorkspaceId, form]);

  const foldersQuery = useQuery({
    queryKey: ['folders', workspaceId, 'document-form'],
    queryFn: () => listFolders(workspaceId, false),
    enabled: open && workspaceId !== '',
  });

  const mutation = useMutation({
    mutationFn: (values: DocumentForm) => {
      const payload: DocumentPayload = {
        title: values.title.trim(),
        documentCode: values.documentCode.trim() || undefined,
        description: values.description.trim() || undefined,
        folderId: values.folderId || null,
        categoryId: values.categoryId,
        documentTypeId: values.documentTypeId,
        classificationId: values.classificationId,
        tagIds,
        effectiveDate: values.effectiveDate || null,
        reviewDate: values.reviewDate || null,
        expiryDate: values.expiryDate || null,
      };
      if (values.ownerSelection.startsWith('unit:')) {
        payload.ownerOrganizationUnitId = values.ownerSelection.slice('unit:'.length);
      } else if (values.ownerSelection.startsWith('user:')) {
        payload.ownerUserId = values.ownerSelection.slice('user:'.length);
      }
      const reason = values.ownerChangeReason.trim();
      if (mode === 'edit' && values.ownerSelection !== initialSelection(document) && reason) {
        payload.ownerChangeReason = reason;
      }
      return mode === 'create'
        ? createDocument(values.workspaceId, payload)
        : updateDocument(document ? document.summary.id : '', payload);
    },
    onSuccess: (detail) => {
      notify(mode === 'create' ? 'Dokumen berhasil dibuat.' : 'Metadata dokumen diperbarui.');
      setActionError(null);
      onSaved(detail, mode);
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        const allowed = new Set(Object.keys(schema.shape));
        for (const fieldError of error.fieldErrors) {
          if (allowed.has(fieldError.field)) {
            form.setError(fieldError.field as keyof DocumentForm, { message: fieldError.message });
          }
        }
      }
    },
  });

  const handleSubmit = form.handleSubmit((values) => {
    const ownerChanged = mode === 'edit' && values.ownerSelection !== initialSelection(document);
    if (ownerChanged && values.ownerChangeReason.trim().length < 5) {
      form.setError('ownerChangeReason', {
        message: 'Alasan perubahan owner wajib diisi minimal 5 karakter.',
      });
      return;
    }
    if (
      values.effectiveDate &&
      values.reviewDate &&
      values.reviewDate < values.effectiveDate
    ) {
      form.setError('reviewDate', {
        message: 'Tanggal review tidak boleh sebelum tanggal berlaku.',
      });
      return;
    }
    if (values.effectiveDate && values.expiryDate && values.expiryDate < values.effectiveDate) {
      form.setError('expiryDate', {
        message: 'Tanggal kedaluwarsa tidak boleh sebelum tanggal berlaku.',
      });
      return;
    }
    mutation.mutate(values);
  });

  const close = () => {
    if (mutation.isPending) {
      return;
    }
    setActionError(null);
    onClose();
  };

  const toggleTag = (tagId: string) => {
    setTagIds((current) =>
      current.includes(tagId) ? current.filter((id) => id !== tagId) : [...current, tagId],
    );
  };

  const activeUnits = units.filter((unit) => unit.status === 'ACTIVE');

  return (
    <Dialog
      open={open}
      title={mode === 'create' ? 'Dokumen baru' : 'Ubah metadata dokumen'}
      description={
        mode === 'create'
          ? 'Dokumen dibuat sebagai draf lalu dapat diaktifkan setelah versi tersedia.'
          : 'Perubahan owner wajib disertai alasan dan tercatat pada riwayat kepemilikan.'
      }
      size="lg"
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Batal
          </Button>
          <Button loading={mutation.isPending} onClick={() => void handleSubmit()}>
            {mode === 'create' ? 'Buat dokumen' : 'Simpan perubahan'}
          </Button>
        </>
      }
    >
      <form noValidate className="space-y-4" onSubmit={handleSubmit}>
        {actionError ? <Alert tone="danger">{actionError}</Alert> : null}

        <div className="grid gap-4 md:grid-cols-2">
          <SelectField
            label="Workspace"
            required
            helper={mode === 'edit' ? 'Workspace tidak dapat dipindahkan lewat form ini.' : undefined}
            disabled={mode === 'edit'}
            error={form.formState.errors.workspaceId?.message}
            {...form.register('workspaceId')}
          >
            <option value="">Pilih workspace</option>
            {workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>
                {workspace.workspaceName} ({workspace.workspaceCode})
              </option>
            ))}
          </SelectField>
          <InputField
            label="Kode dokumen"
            helper="Kode unik, tidak wajib. Huruf besar/kecil dianggap sama."
            error={form.formState.errors.documentCode?.message}
            {...form.register('documentCode')}
          />
        </div>

        <InputField
          label="Judul dokumen"
          required
          maxLength={240}
          error={form.formState.errors.title?.message}
          {...form.register('title')}
        />

        <TextareaField label="Deskripsi" {...form.register('description')} />

        <div className="grid gap-4 md:grid-cols-3">
          <SelectField
            label="Kategori"
            required
            error={form.formState.errors.categoryId?.message}
            {...form.register('categoryId')}
          >
            <option value="">Pilih kategori</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.categoryName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Jenis dokumen"
            required
            error={form.formState.errors.documentTypeId?.message}
            {...form.register('documentTypeId')}
          >
            <option value="">Pilih jenis</option>
            {types.map((type) => (
              <option key={type.id} value={type.id}>
                {type.typeName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Klasifikasi"
            required
            error={form.formState.errors.classificationId?.message}
            {...form.register('classificationId')}
          >
            <option value="">Pilih klasifikasi</option>
            {classifications.map((classification) => (
              <option key={classification.id} value={classification.id}>
                {classification.classificationName} (tingkat {classification.sensitivityRank})
              </option>
            ))}
          </SelectField>
        </div>

        <div className="grid gap-4 md:grid-cols-2">
          <SelectField
            label="Folder"
            helper={
              workspaceId === ''
                ? 'Pilih workspace terlebih dahulu.'
                : foldersQuery.isPending
                  ? 'Memuat folder…'
                  : 'Opsional. Folder harus aktif pada workspace ini.'
            }
            disabled={workspaceId === '' || foldersQuery.isPending}
            error={form.formState.errors.folderId?.message}
            {...form.register('folderId')}
          >
            <option value="">Tingkat atas workspace</option>
            {(foldersQuery.data ?? []).map((folder) => (
              <option key={folder.id} value={folder.id}>
                {folder.folderName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Pemilik"
            helper="Tanpa pilihan berarti Anda menjadi pemilik dokumen."
            error={form.formState.errors.ownerChangeReason?.message}
            {...form.register('ownerSelection')}
          >
            <option value="">Saya sendiri</option>
            {activeUnits.map((unit) => (
              <option key={unit.id} value={`unit:${unit.id}`}>
                Unit: {unit.unitName}
              </option>
            ))}
          </SelectField>
        </div>

        {mode === 'edit' && ownerSelection !== initialSelection(document) ? (
          <InputField
            label="Alasan perubahan owner"
            required
            helper="Minimal 5 karakter. Alasan disimpan pada riwayat kepemilikan dan audit."
            error={form.formState.errors.ownerChangeReason?.message}
            {...form.register('ownerChangeReason')}
          />
        ) : null}

        <fieldset className="space-y-2">
          <legend className="text-xs font-semibold text-ink">
            Tag <span className="font-normal text-ink-secondary">(opsional)</span>
          </legend>
          {tags.length === 0 ? (
            <p className="text-xs text-ink-secondary">
              Belum ada tag aktif. Administrator dapat menambahkannya melalui Master Data.
            </p>
          ) : (
            <div className="flex flex-wrap gap-2">
              {tags.map((tag) => {
                const checked = tagIds.includes(tag.id);
                return (
                  <label
                    key={tag.id}
                    className={cn(
                      'inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1 text-xs font-semibold',
                      checked
                        ? 'border-primary/30 bg-primary-subtle text-primary'
                        : 'border-line-strong bg-surface text-ink-secondary hover:bg-surface-muted',
                    )}
                  >
                    <input
                      type="checkbox"
                      className="sr-only"
                      checked={checked}
                      onChange={() => toggleTag(tag.id)}
                    />
                    {checked ? (
                      <span aria-hidden="true">✓</span>
                    ) : (
                      <span className="size-1.5 rounded-full bg-current opacity-40" aria-hidden="true" />
                    )}
                    {tag.tagName}
                  </label>
                );
              })}
            </div>
          )}
        </fieldset>

        <div className="grid gap-4 md:grid-cols-3">
          <InputField
            type="date"
            label="Tanggal berlaku"
            error={form.formState.errors.effectiveDate?.message}
            {...form.register('effectiveDate')}
          />
          <InputField
            type="date"
            label="Tanggal review"
            error={form.formState.errors.reviewDate?.message}
            {...form.register('reviewDate')}
          />
          <InputField
            type="date"
            label="Tanggal kedaluwarsa"
            error={form.formState.errors.expiryDate?.message}
            {...form.register('expiryDate')}
          />
        </div>
      </form>
    </Dialog>
  );
}
