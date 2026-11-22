import { Button } from './Button';
import { Icon } from './Icon';

export const PAGE_SIZE_OPTIONS = [10, 25, 50, 100] as const;

interface PaginationBarProps {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  itemLabel: string;
  onPageChange: (page: number) => void;
  onSizeChange: (size: number) => void;
  className?: string;
}

export function PaginationBar({
  page,
  size,
  totalItems,
  totalPages,
  itemLabel,
  onPageChange,
  onSizeChange,
  className,
}: PaginationBarProps) {
  const start = totalItems === 0 ? 0 : page * size + 1;
  const end = Math.min(totalItems, (page + 1) * size);
  const pageCount = Math.max(totalPages, 1);

  return (
    <div
      className={[
        'flex flex-col gap-3 border-t border-line pt-3 text-xs text-ink-secondary sm:flex-row sm:items-center sm:justify-between',
        className ?? '',
      ]
        .filter(Boolean)
        .join(' ')}
    >
      <p aria-live="polite">
        Menampilkan{' '}
        <span className="font-semibold text-ink tabular-nums">
          {start}–{end}
        </span>{' '}
        dari <span className="font-semibold text-ink tabular-nums">{totalItems}</span> {itemLabel}
      </p>
      <div className="flex flex-wrap items-center gap-3">
        <label className="flex items-center gap-1.5">
          <span>Baris</span>
          <select
            value={size}
            onChange={(event) => onSizeChange(Number(event.target.value))}
            aria-label="Baris per halaman"
            className="h-8 rounded-md border border-line-strong bg-surface px-2 text-xs font-semibold text-ink focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
          >
            {PAGE_SIZE_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {option}
              </option>
            ))}
          </select>
        </label>
        <div className="flex items-center gap-1.5">
          <Button
            variant="secondary"
            size="compact"
            disabled={page <= 0}
            onClick={() => onPageChange(page - 1)}
            aria-label="Halaman sebelumnya"
          >
            <Icon name="chevron-left" size={14} />
            <span className="hidden sm:inline">Sebelumnya</span>
          </Button>
          <span className="px-1 font-semibold text-ink tabular-nums">
            {page + 1} / {pageCount}
          </span>
          <Button
            variant="secondary"
            size="compact"
            disabled={page + 1 >= pageCount}
            onClick={() => onPageChange(page + 1)}
            aria-label="Halaman berikutnya"
          >
            <span className="hidden sm:inline">Berikutnya</span>
            <Icon name="chevron-right" size={14} />
          </Button>
        </div>
      </div>
    </div>
  );
}
