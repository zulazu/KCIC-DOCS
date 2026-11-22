import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import type { FulfillmentType, RequestDetail } from '../../shared/api/types';
import { Alert } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { InputField, TextareaField } from '../../shared/components/Field';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { fulfillRequest, uploadFulfillment } from './api';
import { DocumentPicker } from './DocumentPicker';
import { FULFILLMENT_TYPE_LABELS } from './labels';

interface FulfillDialogProps {
  open: boolean;
  request: RequestDetail;
  onClose: () => void;
  onCompleted: (detail: RequestDetail) => void;
}

export function FulfillDialog({ open, request, onClose, onCompleted }: FulfillDialogProps) {
  const { notify } = useToast();
  const targetDocumentId = request.summary.targetDocumentId;
  const options = (Object.keys(FULFILLMENT_TYPE_LABELS) as FulfillmentType[]).filter(
    (type) => type !== 'ACCESS_GRANTED' || Boolean(targetDocumentId),
  );

  const [type, setType] = useState<FulfillmentType>(targetDocumentId ? 'ACCESS_GRANTED' : 'DOCUMENT_LINKED');
  const [documentIds, setDocumentIds] = useState<string[]>([]);
  const [externalUrl, setExternalUrl] = useState('');
  const [linkTitle, setLinkTitle] = useState('');
  const [note, setNote] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [versionLabel, setVersionLabel] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [progress, setProgress] = useState<number | null>(null);

  function close() {
    if (!pending) {
      onClose();
    }
  }

  async function submit() {
    const errors: Record<string, string> = {};
    if (type === 'DOCUMENT_LINKED' && documentIds.length === 0) {
      errors.documentIds = 'Pilih minimal satu dokumen.';
    }
    if (type === 'EXTERNAL_LINK_REGISTERED') {
      if (!externalUrl.trim()) {
        errors.externalUrl = 'URL eksternal wajib diisi.';
      } else if (!/^https:\/\//i.test(externalUrl.trim())) {
        errors.externalUrl = 'URL wajib menggunakan HTTPS.';
      }
    }
    if (type === 'DOCUMENT_UPLOADED') {
      if (documentIds.length === 0) {
        errors.documentId = 'Pilih dokumen tujuan unggahan.';
      }
      if (!file) {
        errors.file = 'Pilih file yang akan diunggah.';
      }
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setPending(true);
    setFormError(null);
    try {
      let detail: RequestDetail;
      const uploadTarget = documentIds[0];
      if (type === 'DOCUMENT_UPLOADED' && file && uploadTarget) {
        detail = await uploadFulfillment(
          request.summary.id,
          uploadTarget,
          file,
          { note: note.trim() || undefined, versionLabel: versionLabel.trim() || undefined },
          setProgress,
        );
      } else {
        detail = await fulfillRequest(request.summary.id, {
          fulfillmentType: type,
          documentIds: type === 'DOCUMENT_LINKED' ? documentIds : undefined,
          externalUrl: type === 'EXTERNAL_LINK_REGISTERED' ? externalUrl.trim() : undefined,
          linkTitle: type === 'EXTERNAL_LINK_REGISTERED' ? linkTitle.trim() || undefined : undefined,
          note: note.trim() || undefined,
        });
      }
      notify('Request berhasil dipenuhi dan pemohon telah diberi notifikasi.');
      onCompleted(detail);
    } catch (caught) {
      setFormError(apiMessage(caught));
      if (caught instanceof ApiError) {
        const next: Record<string, string> = {};
        for (const item of caught.fieldErrors) {
          next[item.field] = item.message;
        }
        setFieldErrors(next);
      }
      setPending(false);
      setProgress(null);
    }
  }

  return (
    <Dialog
      open={open}
      title={`Penuhi request ${request.summary.requestCode}`}
      description={`Pemohon: ${request.summary.requesterName ?? '—'}`}
      onClose={close}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={pending}>
            Batal
          </Button>
          <Button type="submit" form="fulfill-form" loading={pending}>
            {progress === null ? 'Selesaikan pemenuhan' : `Mengunggah ${progress}%`}
          </Button>
        </>
      }
    >
      <form
        id="fulfill-form"
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          void submit();
        }}
      >
        {formError ? <Alert>{formError}</Alert> : null}

        <fieldset className="space-y-2">
          <legend className="text-xs font-semibold text-ink">Jenis pemenuhan</legend>
          <div className="grid gap-2 sm:grid-cols-2">
            {options.map((option) => (
              <label
                key={option}
                className={cn(
                  'flex cursor-pointer items-start gap-2 rounded-lg border px-3 py-2.5 text-xs',
                  type === option
                    ? 'border-primary bg-primary/5'
                    : 'border-line hover:border-primary/40',
                )}
              >
                <input
                  type="radio"
                  name="fulfillmentType"
                  value={option}
                  checked={type === option}
                  onChange={() => {
                    setType(option);
                    setFieldErrors({});
                  }}
                  className="mt-0.5 accent-primary"
                />
                <span>
                  <span className="block font-semibold text-ink">{FULFILLMENT_TYPE_LABELS[option]}</span>
                  <span className="mt-0.5 block text-ink-secondary">
                    {option === 'ACCESS_GRANTED'
                      ? 'Berikan izin lihat, pratinjau, dan unduh untuk dokumen target.'
                      : option === 'DOCUMENT_LINKED'
                        ? 'Tautkan dokumen katalog yang sudah ada pada pemenuhan ini.'
                        : option === 'DOCUMENT_UPLOADED'
                          ? 'Unggah versi baru ke dokumen katalog yang sudah ada.'
                          : 'Catat tautan Office/eksternal (HTTPS) sebagai pemenuhan.'}
                  </span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

        {type === 'ACCESS_GRANTED' ? (
          <p className="rounded-lg border border-line bg-surface-muted px-3 py-2.5 text-xs text-ink-secondary">
            Akses akan diberikan pada dokumen{' '}
            <span className="font-semibold text-ink">
              {request.summary.targetDocumentTitle ?? 'target'}
            </span>{' '}
            dengan sumber grant <span className="font-semibold text-ink">REQUEST</span>. Grant dapat
            dicabut terpisah dari dokumen.
          </p>
        ) : null}

        {type === 'DOCUMENT_LINKED' ? (
          <DocumentPicker
            label="Dokumen yang ditautkan"
            helper="Pilih dokumen yang dapat Anda akses. Minimal satu dokumen."
            error={fieldErrors.documentIds}
            multiple
            selected={documentIds}
            onChange={setDocumentIds}
          />
        ) : null}

        {type === 'DOCUMENT_UPLOADED' ? (
          <>
            <DocumentPicker
              label="Dokumen tujuan"
              helper="Pilih satu dokumen yang sudah terdaftar; file akan menjadi versi baru."
              error={fieldErrors.documentId}
              selected={documentIds}
              onChange={setDocumentIds}
            />
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="flex flex-col gap-1.5">
                <label htmlFor="fulfill-file" className="text-xs font-semibold text-ink">
                  File dokumen<span className="ml-0.5 text-danger" aria-hidden="true">*</span>
                </label>
                <input
                  id="fulfill-file"
                  type="file"
                  onChange={(event) => setFile(event.target.files?.[0] ?? null)}
                  aria-invalid={fieldErrors.file ? true : undefined}
                  className="w-full rounded-lg border border-line-strong bg-surface px-3 py-2 text-xs text-ink file:mr-3 file:rounded-md file:border-0 file:bg-primary/10 file:px-2.5 file:py-1.5 file:text-xs file:font-semibold file:text-primary"
                />
                {fieldErrors.file ? (
                  <p role="alert" className="text-xs font-medium text-danger">
                    {fieldErrors.file}
                  </p>
                ) : (
                  <p className="text-xs text-ink-secondary">
                    Jenis dan ukuran file mengikuti file policy yang berlaku.
                  </p>
                )}
              </div>
              <InputField
                label="Label versi"
                maxLength={30}
                value={versionLabel}
                onChange={(event) => setVersionLabel(event.target.value)}
                placeholder="Contoh: v2"
              />
            </div>
          </>
        ) : null}

        {type === 'EXTERNAL_LINK_REGISTERED' ? (
          <div className="grid gap-4 sm:grid-cols-2">
            <InputField
              label="URL eksternal"
              required
              type="url"
              placeholder="https://"
              value={externalUrl}
              error={fieldErrors.externalUrl}
              helper="Hanya HTTPS. Pastikan tautan dapat diakses penerima."
              onChange={(event) => setExternalUrl(event.target.value)}
            />
            <InputField
              label="Judul tautan"
              maxLength={200}
              value={linkTitle}
              error={fieldErrors.linkTitle}
              onChange={(event) => setLinkTitle(event.target.value)}
            />
          </div>
        ) : null}

        <TextareaField
          label="Catatan pemenuhan"
          rows={2}
          maxLength={2000}
          value={note}
          error={fieldErrors.note}
          onChange={(event) => setNote(event.target.value)}
        />
      </form>
    </Dialog>
  );
}
