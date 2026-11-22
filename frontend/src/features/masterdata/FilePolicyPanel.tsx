import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import type { FilePolicy } from '../../shared/api/types';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../shared/components/Feedback';
import { InputField, TextareaField } from '../../shared/components/Field';
import { Card } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDateTime } from '../../shared/utils/format';
import { listFilePolicies, updateFilePolicy } from './api';

const MB = 1024 * 1024;

export function FilePolicyPanel() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const query = useQuery({ queryKey: ['master-data', 'file-policies'], queryFn: listFilePolicies });
  const [target, setTarget] = useState<FilePolicy | null>(null);
  const [extensions, setExtensions] = useState('');
  const [mimeTypes, setMimeTypes] = useState('');
  const [maxMb, setMaxMb] = useState('25');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [actionError, setActionError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      updateFilePolicy(target?.id ?? '', {
        allowedExtensions: splitList(extensions),
        allowedMimeTypes: splitList(mimeTypes),
        maxFileSizeBytes: Math.round(Number(maxMb) * MB),
      }),
    onSuccess: (policy) => {
      notify(`File policy ${policy.policyCode} diperbarui.`);
      setTarget(null);
      setActionError(null);
      setFieldErrors({});
      void queryClient.invalidateQueries({ queryKey: ['master-data', 'file-policies'] });
      void queryClient.invalidateQueries({ queryKey: ['master-data', 'lookup'] });
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      if (error instanceof ApiError) {
        setFieldErrors(
          Object.fromEntries(
            error.fieldErrors.map((item) => [item.field.replace(/\[\d+]$/, ''), item.message]),
          ),
        );
      }
    },
  });

  const openEdit = (policy: FilePolicy) => {
    setTarget(policy);
    setExtensions(policy.allowedExtensions.join(', '));
    setMimeTypes(policy.allowedMimeTypes.join(', '));
    setMaxMb(String(Math.round(policy.maxFileSizeBytes / MB)));
    setFieldErrors({});
    setActionError(null);
  };

  const submit = () => {
    const errors: Record<string, string> = {};
    if (splitList(extensions).length === 0) {
      errors.allowedExtensions = 'Minimal satu ekstensi diizinkan.';
    }
    if (splitList(mimeTypes).length === 0) {
      errors.allowedMimeTypes = 'Minimal satu MIME type diizinkan.';
    }
    const size = Number(maxMb);
    if (Number.isNaN(size) || size <= 0) {
      errors.maxFileSizeBytes = 'Ukuran maksimum harus lebih dari 0 MB.';
    }
    if (Object.keys(errors).length > 0) {
      setFieldErrors(errors);
      return;
    }
    setFieldErrors({});
    mutation.mutate();
  };

  return (
    <Card>
      <div className="border-b border-line px-5 py-4">
        <h2 className="text-base font-semibold text-ink">File policy</h2>
        <p className="mt-0.5 text-xs text-ink-secondary">
          Allowlist ekstensi dan MIME type divalidasi ulang oleh backend pada setiap upload.
          Antivirus tidak termasuk pilot; validasi ini bukan pemindaian malware.
        </p>
      </div>
      {query.isPending ? (
        <div className="px-5 py-4">
          <LoadingBlock />
        </div>
      ) : query.isError ? (
        <ErrorState message={apiMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : !query.data || query.data.length === 0 ? (
        <EmptyState title="Belum ada file policy" description="File policy default belum dikonfigurasi." />
      ) : (
        <ul className="divide-y divide-line">
          {query.data.map((policy) => (
            <li key={policy.id} className="flex flex-wrap items-start justify-between gap-3 px-5 py-4">
              <div className="space-y-2">
                <div className="flex flex-wrap items-center gap-2">
                  <span className="font-semibold text-ink">{policy.policyName}</span>
                  <span className="font-mono text-[11px] text-ink-secondary">{policy.policyCode}</span>
                  {policy.defaultPolicy ? <Badge tone="gold">Default</Badge> : null}
                  <Badge tone={policy.active ? 'success' : 'neutral'}>
                    {policy.active ? 'Aktif' : 'Nonaktif'}
                  </Badge>
                </div>
                <dl className="grid gap-x-8 gap-y-1 text-xs text-ink-secondary sm:grid-cols-2">
                  <div>
                    <dt className="font-semibold text-ink">Ekstensi</dt>
                    <dd className="font-mono break-words">{policy.allowedExtensions.join(', ')}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-ink">MIME type</dt>
                    <dd className="font-mono break-words">{policy.allowedMimeTypes.join(', ')}</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-ink">Ukuran maksimum</dt>
                    <dd>{Math.round(policy.maxFileSizeBytes / MB)} MB</dd>
                  </div>
                  <div>
                    <dt className="font-semibold text-ink">Diperbarui</dt>
                    <dd>{formatDateTime(policy.updatedAt)}</dd>
                  </div>
                </dl>
              </div>
              <Button size="compact" variant="secondary" onClick={() => openEdit(policy)}>
                Ubah
              </Button>
            </li>
          ))}
        </ul>
      )}

      <Dialog
        open={target !== null}
        title="Ubah file policy"
        description={target ? `${target.policyName} (${target.policyCode})` : undefined}
        onClose={() => setTarget(null)}
        footer={
          <>
            <Button variant="secondary" onClick={() => setTarget(null)}>
              Batal
            </Button>
            <Button loading={mutation.isPending} onClick={submit}>
              Simpan perubahan
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
          <TextareaField
            label="Ekstensi diizinkan"
            required
            helper="Pisahkan dengan koma. Tanpa titik, contoh: pdf, docx, xlsx."
            error={fieldErrors.allowedExtensions}
            value={extensions}
            onChange={(event) => setExtensions(event.target.value)}
          />
          <TextareaField
            label="MIME type diizinkan"
            required
            helper="Pisahkan dengan koma, contoh: application/pdf, image/png."
            error={fieldErrors.allowedMimeTypes}
            value={mimeTypes}
            onChange={(event) => setMimeTypes(event.target.value)}
          />
          <InputField
            label="Ukuran maksimum (MB)"
            required
            type="number"
            min={1}
            error={fieldErrors.maxFileSizeBytes}
            value={maxMb}
            onChange={(event) => setMaxMb(event.target.value)}
          />
        </form>
      </Dialog>
    </Card>
  );
}

function splitList(value: string): string[] {
  // Split on commas/newlines only; a token containing a space must reach backend
  // validation instead of being silently split into valid-looking entries.
  return value
    .split(/[,\n]+/)
    .map((item) => item.trim())
    .filter(Boolean);
}
