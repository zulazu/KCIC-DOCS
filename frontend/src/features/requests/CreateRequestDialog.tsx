import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ApiError } from '../../shared/api/client';
import type { RequestDetail } from '../../shared/api/types';
import { Alert } from '../../shared/components/Feedback';
import { Button } from '../../shared/components/Button';
import { InputField, SelectField, TextareaField } from '../../shared/components/Field';
import { Dialog } from '../../shared/components/Dialog';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { listActiveUnits } from '../workspaces/api';
import { createRequest } from './api';
import type { CreateRequestPayload } from './api';
import { DocumentPicker } from './DocumentPicker';
import { REQUEST_TYPE_OPTIONS } from './labels';

interface CreateRequestDialogProps {
  open: boolean;
  onClose: () => void;
  onCreated: (detail: RequestDetail) => void;
}

const today = new Date().toISOString().slice(0, 10);

export function CreateRequestDialog({ open, onClose, onCreated }: CreateRequestDialogProps) {
  const { notify } = useToast();
  const [requestType, setRequestType] = useState('ACCESS');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [reason, setReason] = useState('');
  const [documentIds, setDocumentIds] = useState<string[]>([]);
  const [unitId, setUnitId] = useState('');
  const [neededByDate, setNeededByDate] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);

  const unitsQuery = useQuery({ queryKey: ['organization-units', 'active'], queryFn: listActiveUnits });

  const mutation = useMutation({
    mutationFn: (payload: CreateRequestPayload) => createRequest(payload),
    onSuccess: (detail) => {
      notify('Request berhasil dikirim dan diteruskan ke approver.');
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

  function reset() {
    setRequestType('ACCESS');
    setTitle('');
    setDescription('');
    setReason('');
    setDocumentIds([]);
    setUnitId('');
    setNeededByDate('');
    setFieldErrors({});
    setFormError(null);
  }

  function close() {
    if (mutation.isPending) {
      return;
    }
    reset();
    onClose();
  }

  function submit() {
    const errors: Record<string, string> = {};
    if (!title.trim()) {
      errors.title = 'Judul request wajib diisi.';
    }
    if (!reason.trim()) {
      errors.reason = 'Alasan request wajib diisi.';
    }
    if (requestType === 'ACCESS' && documentIds.length === 0) {
      errors.targetDocumentId = 'Pilih dokumen yang Anda butuhkan aksesnya.';
    }
    if (neededByDate && neededByDate < today) {
      errors.neededByDate = 'Tanggal kebutuhan tidak boleh di masa lalu.';
    }
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }
    setFormError(null);
    mutation.mutate({
      requestType,
      title: title.trim(),
      description: description.trim() || undefined,
      reason: reason.trim(),
      targetDocumentId: documentIds[0] ?? null,
      targetOrganizationUnitId: unitId || null,
      neededByDate: neededByDate || null,
    });
  }

  const requiresDocument = requestType === 'ACCESS';
  const showsDocument = requestType !== 'TEMPLATE';

  return (
    <Dialog
      open={open}
      title="Buat request"
      description="Request diteruskan otomatis ke pemilik dokumen, manajer unit, atau document administrator."
      onClose={close}
      size="lg"
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={mutation.isPending}>
            Batal
          </Button>
          <Button type="submit" form="create-request-form" loading={mutation.isPending}>
            Kirim request
          </Button>
        </>
      }
    >
      <form
        id="create-request-form"
        className="space-y-4"
        onSubmit={(event) => {
          event.preventDefault();
          submit();
        }}
      >
        {formError ? <Alert>{formError}</Alert> : null}
        <SelectField
          label="Tipe request"
          required
          value={requestType}
          onChange={(event) => {
            setRequestType(event.target.value);
            setFieldErrors({});
          }}
        >
          {REQUEST_TYPE_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </SelectField>
        <InputField
          label="Judul request"
          required
          maxLength={200}
          value={title}
          error={fieldErrors.title}
          onChange={(event) => setTitle(event.target.value)}
          placeholder="Contoh: Akses kontrak kerja sama 2026"
        />
        <TextareaField
          label="Alasan / kebutuhan"
          required
          rows={3}
          maxLength={4000}
          value={reason}
          error={fieldErrors.reason}
          helper="Jelaskan tujuan penggunaan dokumen agar approver dapat menilai request."
          onChange={(event) => setReason(event.target.value)}
        />
        {showsDocument ? (
          <DocumentPicker
            label={requiresDocument ? 'Dokumen yang dibutuhkan' : 'Dokumen terkait'}
            required={requiresDocument}
            helper={
              requiresDocument
                ? 'Wajib. Dokumen harus sudah terdaftar pada katalog.'
                : 'Opsional. Membantu approver memahami dokumen yang dimaksud.'
            }
            error={fieldErrors.targetDocumentId}
            selected={documentIds}
            onChange={setDocumentIds}
          />
        ) : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label="Unit organisasi"
            value={unitId}
            error={fieldErrors.targetOrganizationUnitId}
            helper="Kosongkan untuk mengikuti unit pemilik dokumen."
            onChange={(event) => setUnitId(event.target.value)}
          >
            <option value="">Tidak ditentukan</option>
            {(unitsQuery.data ?? []).map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName}
              </option>
            ))}
          </SelectField>
          <InputField
            label="Dibutuhkan sebelum"
            type="date"
            min={today}
            value={neededByDate}
            error={fieldErrors.neededByDate}
            onChange={(event) => setNeededByDate(event.target.value)}
          />
        </div>
        <TextareaField
          label="Deskripsi tambahan"
          rows={2}
          maxLength={4000}
          value={description}
          error={fieldErrors.description}
          onChange={(event) => setDescription(event.target.value)}
        />
      </form>
    </Dialog>
  );
}
