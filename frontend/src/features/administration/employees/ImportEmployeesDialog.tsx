import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { ApiError, saveBlob } from '../../../shared/api/client';
import { Button } from '../../../shared/components/Button';
import { Dialog } from '../../../shared/components/Dialog';
import { Icon } from '../../../shared/components/Icon';
import { downloadEmployeeTemplate, importEmployees } from '../api';

interface ImportEmployeesDialogProps {
  open: boolean;
  onClose: () => void;
  onImported: (count: number) => void;
}

const MAX_FILE_BYTES = 5 * 1024 * 1024;

export function ImportEmployeesDialog({ open, onClose, onImported }: ImportEmployeesDialogProps) {
  const [file, setFile] = useState<File | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [localError, setLocalError] = useState<string | null>(null);
  const [templatePending, setTemplatePending] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const importMutation = useMutation({
    mutationFn: importEmployees,
    onSuccess: (result) => {
      setFile(null);
      setError(null);
      setLocalError(null);
      if (inputRef.current) {
        inputRef.current.value = '';
      }
      onImported(result.importedCount);
    },
    onError: (mutationError) => {
      if (mutationError instanceof ApiError) {
        setError(mutationError);
        setLocalError(null);
      } else {
        setError(null);
        setLocalError('Import tidak dapat diproses. Coba lagi.');
      }
    },
  });

  const close = () => {
    setFile(null);
    setError(null);
    setLocalError(null);
    if (inputRef.current) {
      inputRef.current.value = '';
    }
    onClose();
  };

  const selectFile = (selected: File | null) => {
    setError(null);
    setLocalError(null);
    if (selected && selected.size > MAX_FILE_BYTES) {
      setFile(null);
      setLocalError('Ukuran file maksimal 5 MB.');
      if (inputRef.current) {
        inputRef.current.value = '';
      }
      return;
    }
    setFile(selected);
  };

  const downloadTemplate = async () => {
    setTemplatePending(true);
    try {
      const content = await downloadEmployeeTemplate();
      saveBlob(content.blob, content.filename ?? 'kcic-docs-employee-import-template.xlsx');
    } catch {
      setLocalError('Template tidak dapat diunduh. Coba lagi.');
    } finally {
      setTemplatePending(false);
    }
  };

  const failureMessage = localError ?? error?.message ?? null;
  const rowErrors = error?.fieldErrors ?? [];

  return (
    <Dialog
      open={open}
      title="Import employee dari Excel"
      description="Unggah file .xlsx berisi banyak employee sekaligus. Seluruh baris divalidasi lebih dulu; jika ada satu baris tidak valid, tidak ada data yang disimpan."
      onClose={close}
      footer={
        <>
          <Button variant="secondary" onClick={close}>
            Batal
          </Button>
          <Button
            disabled={!file}
            loading={importMutation.isPending}
            onClick={() => {
              if (file) {
                importMutation.mutate(file);
              }
            }}
          >
            <Icon name="upload" size={16} />
            Import sekarang
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div className="rounded-lg border border-line bg-surface-muted px-3 py-2.5 text-xs text-ink-secondary">
          <p className="font-semibold text-ink">Format kolom (sheet pertama, header persis):</p>
          <p className="mt-1 font-mono text-[11px]">
            nip, full_name, email, employment_status, division_unit_code, position_code
          </p>
          <ul className="mt-2 list-disc space-y-0.5 pl-4">
            <li>employment_status: ACTIVE, INACTIVE, atau TERMINATED.</li>
            <li>division_unit_code: kode Biro/Division aktif yang sudah terdaftar di KCIC Docs.</li>
            <li>position_code: opsional; kode jabatan aktif dari menu Organisasi → Jabatan.</li>
            <li>File lama dengan 5 kolom (tanpa position_code) tetap diterima.</li>
            <li>NIP dan email harus unik, belum terdaftar, dan tidak duplikat dalam file.</li>
            <li>Maksimal 1000 baris data dan 5 MB; format kolom NIP sebagai Text.</li>
          </ul>
          <Button
            variant="ghost"
            size="compact"
            className="mt-2"
            loading={templatePending}
            onClick={() => void downloadTemplate()}
          >
            <Icon name="download" size={14} />
            Unduh template Excel
          </Button>
        </div>

        <div>
          <label htmlFor="employee-import-file" className="mb-1.5 block text-xs font-semibold text-ink">
            File Excel (.xlsx)
          </label>
          <input
            ref={inputRef}
            id="employee-import-file"
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
            className="block w-full cursor-pointer rounded-lg border border-line-strong bg-surface text-sm text-ink file:mr-3 file:rounded-l-lg file:border-0 file:bg-primary-subtle file:px-3 file:py-2.5 file:text-sm file:font-semibold file:text-primary hover:file:bg-primary/15 focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
          />
          {file ? (
            <p className="mt-1.5 text-xs text-ink-secondary">
              {file.name} · {(file.size / 1024).toFixed(0)} KB
            </p>
          ) : (
            <p className="mt-1.5 text-xs text-ink-disabled">Belum ada file dipilih.</p>
          )}
        </div>

        {failureMessage ? (
          <div role="alert" className="rounded-lg border border-danger/25 bg-danger-bg px-3 py-2 text-xs text-danger">
            <p className="font-semibold">{failureMessage}</p>
            {rowErrors.length > 0 ? (
              <ul className="mt-2 max-h-56 space-y-1 overflow-y-auto pr-1">
                {rowErrors.map((rowError, index) => (
                  <li key={`${rowError.field}-${index}`}>
                    <span className="font-semibold">{rowError.field}:</span> {rowError.message}
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        ) : null}
      </div>
    </Dialog>
  );
}