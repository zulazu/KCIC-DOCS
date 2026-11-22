import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Icon } from '../../shared/components/Icon';
import { InputField } from '../../shared/components/Field';
import { cn } from '../../shared/utils/cn';
import { listDocuments } from '../documents/api';
import {
  type DistributionAccessLevel,
  accessLevelDescriptions,
  accessLevelLabels,
} from './api';

export interface DistributionDocumentSelection {
  documentId: string;
  title: string;
  accessLevel: DistributionAccessLevel;
}

const ACCESS_LEVELS: DistributionAccessLevel[] = ['VIEW', 'PREVIEW', 'DOWNLOAD'];

interface DistributionDocumentPickerProps {
  selected: DistributionDocumentSelection[];
  onChange: (items: DistributionDocumentSelection[]) => void;
  error?: string;
}

export function DistributionDocumentPicker({
  selected,
  onChange,
  error,
}: DistributionDocumentPickerProps) {
  const [keyword, setKeyword] = useState('');
  const trimmed = keyword.trim();
  const searchQuery = useQuery({
    queryKey: ['documents', 'picker', trimmed],
    queryFn: () => listDocuments({ keyword: trimmed, size: 8 }),
    enabled: trimmed.length >= 2,
  });

  function toggle(documentId: string, title: string) {
    const exists = selected.some((item) => item.documentId === documentId);
    if (exists) {
      onChange(selected.filter((item) => item.documentId !== documentId));
      return;
    }
    onChange([...selected, { documentId, title, accessLevel: 'VIEW' }]);
  }

  function changeLevel(documentId: string, accessLevel: DistributionAccessLevel) {
    onChange(
      selected.map((item) => (item.documentId === documentId ? { ...item, accessLevel } : item)),
    );
  }

  return (
    <div className="flex flex-col gap-3">
      <InputField
        label="Dokumen yang didistribusikan"
        required
        helper="Cari minimal 2 karakter. Anda hanya dapat mendistribusikan dokumen yang boleh Anda bagikan."
        error={error}
        placeholder="Cari dokumen berdasarkan judul atau kode"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />

      {selected.length > 0 ? (
        <ul className="divide-y divide-line rounded-lg border border-line" aria-label="Dokumen terpilih">
          {selected.map((item) => (
            <li key={item.documentId} className="flex flex-col gap-2 px-3 py-2.5 sm:flex-row sm:items-center">
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-semibold text-ink">{item.title}</p>
                <p className="mt-0.5 text-xs text-ink-secondary">
                  {accessLevelDescriptions[item.accessLevel]}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <select
                  aria-label={`Tingkat akses untuk ${item.title}`}
                  className="h-9 rounded-lg border border-line-strong bg-surface px-2 text-xs font-medium text-ink focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
                  value={item.accessLevel}
                  onChange={(event) =>
                    changeLevel(item.documentId, event.target.value as DistributionAccessLevel)
                  }
                >
                  {ACCESS_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {accessLevelLabels[level]}
                    </option>
                  ))}
                </select>
                <button
                  type="button"
                  onClick={() => toggle(item.documentId, item.title)}
                  aria-label={`Hapus ${item.title} dari distribusi`}
                  className="rounded-md p-1.5 text-ink-secondary hover:bg-surface-muted hover:text-danger"
                >
                  <Icon name="close" size={14} />
                </button>
              </div>
            </li>
          ))}
        </ul>
      ) : null}

      {trimmed.length >= 2 ? (
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
            <ul className="max-h-56 overflow-y-auto py-1" aria-label="Hasil pencarian dokumen">
              {searchQuery.data?.items.map((item) => {
                const isSelected = selected.some((entry) => entry.documentId === item.id);
                return (
                  <li key={item.id}>
                    <button
                      type="button"
                      onClick={() => toggle(item.id, item.title)}
                      aria-pressed={isSelected}
                      className={cn(
                        'flex w-full items-start gap-2 px-3 py-2 text-left text-xs hover:bg-surface',
                        isSelected ? 'bg-primary/5' : null,
                      )}
                    >
                      <span
                        className={cn(
                          'mt-0.5 flex size-4 shrink-0 items-center justify-center rounded border',
                          isSelected
                            ? 'border-primary bg-primary text-white'
                            : 'border-line-strong bg-surface',
                        )}
                        aria-hidden="true"
                      >
                        {isSelected ? <Icon name="check" size={11} /> : null}
                      </span>
                      <span className="min-w-0">
                        <span className="block truncate font-semibold text-ink">{item.title}</span>
                        <span className="mt-0.5 block truncate text-ink-secondary">
                          {item.documentCode ?? 'Tanpa kode'} · {item.workspaceName ?? 'Workspace'}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ul>
          )}
        </div>
      ) : null}
    </div>
  );
}
