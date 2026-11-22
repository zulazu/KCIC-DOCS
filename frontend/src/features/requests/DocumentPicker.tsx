import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Icon } from '../../shared/components/Icon';
import { InputField } from '../../shared/components/Field';
import { cn } from '../../shared/utils/cn';
import { formatDate } from '../../shared/utils/format';
import { listDocuments } from '../documents/api';

interface DocumentPickerProps {
  label: string;
  helper?: string;
  error?: string;
  required?: boolean;
  multiple?: boolean;
  selected: string[];
  onChange: (ids: string[]) => void;
}

export function DocumentPicker({
  label,
  helper,
  error,
  required = false,
  multiple = false,
  selected,
  onChange,
}: DocumentPickerProps) {
  const [keyword, setKeyword] = useState('');
  const [labels, setLabels] = useState<Record<string, string>>({});
  const trimmed = keyword.trim();
  const searchQuery = useQuery({
    queryKey: ['documents', 'picker', trimmed],
    queryFn: () => listDocuments({ keyword: trimmed, size: 8 }),
    enabled: trimmed.length >= 2,
  });

  function toggle(documentId: string, title?: string) {
    if (title) {
      setLabels((current) => ({ ...current, [documentId]: title }));
    }
    if (!multiple) {
      onChange([documentId]);
      return;
    }
    onChange(
      selected.includes(documentId)
        ? selected.filter((id) => id !== documentId)
        : [...selected, documentId],
    );
  }

  return (
    <div className="flex flex-col gap-2">
      <InputField
        label={label}
        required={required}
        helper={helper ?? 'Ketik minimal 2 karakter untuk mencari dokumen pada katalog.'}
        error={error}
        placeholder="Cari dokumen berdasarkan judul atau kode"
        value={keyword}
        onChange={(event) => setKeyword(event.target.value)}
      />
      {selected.length > 0 ? (
        <ul className="flex flex-wrap gap-1.5" aria-label="Dokumen terpilih">
          {selected.map((id) => (
            <li
              key={id}
              className="inline-flex items-center gap-1.5 rounded-full border border-primary/25 bg-primary/5 py-1 pr-1.5 pl-3 text-xs font-medium text-primary"
            >
              {labels[id] ?? 'Dokumen terpilih'}
              <button
                type="button"
                onClick={() => toggle(id)}
                aria-label={`Hapus ${labels[id] ?? 'dokumen'} dari pilihan`}
                className="rounded-full p-0.5 hover:bg-primary/10"
              >
                <Icon name="close" size={12} />
              </button>
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
              Tidak ada dokumen yang cocok. Pastikan dokumen sudah terdaftar di katalog.
            </p>
          ) : (
            <ul className="max-h-56 overflow-y-auto py-1" aria-label="Hasil pencarian dokumen">
              {searchQuery.data?.items.map((item) => {
                const isSelected = selected.includes(item.id);
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
                          {item.documentCode ?? 'Tanpa kode'} · {item.status} ·{' '}
                          {formatDate(item.updatedAt)}
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
