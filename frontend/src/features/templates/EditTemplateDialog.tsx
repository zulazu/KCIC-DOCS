import { useMutation } from '@tanstack/react-query';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../shared/api/client';
import { Button } from '../../shared/components/Button';
import { Dialog } from '../../shared/components/Dialog';
import { InputField, TextareaField } from '../../shared/components/Field';
import { Alert } from '../../shared/components/Feedback';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { updateTemplate } from './api';
import type { TemplateDetail } from './api';

interface EditTemplateDialogProps {
  detail: TemplateDetail;
  onClose: () => void;
  onUpdated: (detail: TemplateDetail) => void;
}

export function EditTemplateDialog({ detail, onClose, onUpdated }: EditTemplateDialogProps) {
  const { notify } = useToast();
  const [templateCode, setTemplateCode] = useState(detail.summary.templateCode);
  const [templateName, setTemplateName] = useState(detail.summary.templateName);
  const [description, setDescription] = useState(detail.summary.description ?? '');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      updateTemplate(detail.summary.id, {
        templateCode: templateCode.trim(),
        templateName: templateName.trim(),
        description: description.trim() || undefined,
      }),
    onSuccess: (updated) => {
      notify('Metadata template diperbarui.');
      onUpdated(updated);
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
    if (!mutation.isPending) {
      onClose();
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const errors: Record<string, string> = {};
    if (!templateCode.trim()) {
      errors.templateCode = 'Kode template wajib diisi.';
    }
    if (!templateName.trim()) {
      errors.templateName = 'Nama template wajib diisi.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setFormError(null);
    mutation.mutate();
  }

  return (
    <Dialog
      open
      title="Ubah metadata template"
      description="Perubahan kode, nama, dan deskripsi tercatat pada audit trail."
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={mutation.isPending}>
            Batal
          </Button>
          <Button type="submit" form="edit-template-form" loading={mutation.isPending}>
            Simpan perubahan
          </Button>
        </>
      }
    >
      <form id="edit-template-form" className="space-y-4" onSubmit={submit}>
        {formError ? <Alert tone="danger">{formError}</Alert> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <InputField
            label="Kode template"
            required
            maxLength={80}
            value={templateCode}
            onChange={(event) => setTemplateCode(event.target.value)}
            error={fieldErrors.templateCode}
            helper="Kode unik, disimpan dalam huruf kapital."
          />
          <InputField
            label="Nama template"
            required
            maxLength={180}
            value={templateName}
            onChange={(event) => setTemplateName(event.target.value)}
            error={fieldErrors.templateName}
          />
        </div>
        <TextareaField
          label="Deskripsi"
          maxLength={4000}
          rows={3}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          error={fieldErrors.description}
        />
      </form>
    </Dialog>
  );
}
