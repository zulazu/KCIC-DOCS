import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { StatusBadge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../../shared/components/Feedback';
import { InputField, SelectField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { FilterPanel } from '../../shared/components/FilterPanel';
import { PaginationBar } from '../../shared/components/PaginationBar';
import { PageHeader } from '../../shared/components/Surface';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { formatDate, formatDateTime } from '../../shared/utils/format';
import { useAuth } from '../auth/AuthContext';
import {
  type DistributionBox,
  type DistributionSummary,
  DISTRIBUTION_STATUS_OPTIONS,
  recipientStatusLabels,
} from './api';
import { listDistributions } from './api';
import { CreateDistributionDialog } from './CreateDistributionDialog';

const DEFAULT_PAGE_SIZE = 10;

function DistributionRow({
  distribution,
  box,
}: {
  distribution: DistributionSummary;
  box: DistributionBox;
}) {
  return (
    <li>
      <Link
        to={`/distributions/${distribution.id}`}
        className="group flex flex-col gap-2.5 rounded-lg border border-line px-4 py-3.5 transition-colors hover:border-primary/40 hover:bg-primary/[0.03] focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none sm:flex-row sm:items-center"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
          <Icon name="link" size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate text-sm font-semibold text-ink group-hover:text-primary">
              {distribution.title}
            </p>
            <StatusBadge status={distribution.status} />
            {box === 'received' && distribution.recipientStatus ? (
              <StatusBadge
                status={distribution.recipientStatus}
                label={recipientStatusLabels[distribution.recipientStatus]}
              />
            ) : null}
          </div>
          <p className="mt-0.5 truncate text-xs text-ink-secondary">
            {distribution.distributionCode} · Dari {distribution.senderName ?? '—'}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-secondary">
            <span>
              {distribution.itemCount} dokumen · {distribution.recipientCount} penerima
            </span>
            {box === 'sent' ? (
              <>
                <span aria-hidden="true">·</span>
                <span>
                  Dibaca {distribution.readCount}/{distribution.recipientCount}
                </span>
              </>
            ) : distribution.recipientStatusAt ? (
              <>
                <span aria-hidden="true">·</span>
                <span>Status pada {formatDateTime(distribution.recipientStatusAt)}</span>
              </>
            ) : null}
          </div>
        </div>
        <div className="shrink-0 text-left text-xs text-ink-secondary sm:text-right">
          <p>Dikirim</p>
          <p className="font-medium text-ink">{formatDateTime(distribution.sentAt)}</p>
          <p className="mt-1">
            {distribution.expiresAt
              ? `Berlaku s.d. ${formatDate(distribution.expiresAt)}`
              : 'Tanpa kedaluwarsa'}
          </p>
        </div>
      </Link>
    </li>
  );
}

export function DistributionsPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const capabilities = me?.capabilities ?? [];
  const canSent = capabilities.includes('distribution.view_own');
  const canReceived = capabilities.includes('distribution.view_received');
  const canCreate = capabilities.includes('distribution.create');

  const [box, setBox] = useState<DistributionBox>(canReceived ? 'received' : 'sent');
  const [status, setStatus] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);

  const query = useQuery({
    queryKey: ['distributions', box, { status, keyword, page, pageSize }],
    queryFn: () =>
      listDistributions({
        box,
        status: status || undefined,
        keyword: keyword || undefined,
        page,
        size: pageSize,
      }),
    enabled: box === 'received' ? canReceived : canSent,
    placeholderData: keepPreviousData,
  });

  const hasFilters = Boolean(status || keyword);
  const activeFilterCount = [status, keyword].filter(Boolean).length;
  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;

  if (!canSent && !canReceived) {
    return (
      <div className="rounded-card border border-line bg-surface">
        <ForbiddenState message="Akun Anda tidak memiliki izin untuk membuka halaman distribusi." />
      </div>
    );
  }

  function changeBox(next: DistributionBox) {
    setBox(next);
    setPage(0);
  }

  function resetFilters() {
    setStatus('');
    setKeywordInput('');
    setKeyword('');
    setPage(0);
  }

  const items = query.data?.items ?? [];

  return (
    <>
      <PageHeader
        title="Distribusi"
        action={
          canCreate ? (
            <Button onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={16} />
              Distribusi baru
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div
          role="tablist"
          aria-label="Tampilan distribusi"
          className="inline-flex rounded-lg border border-line bg-surface-muted p-0.5"
        >
          {canReceived ? (
            <button
              type="button"
              role="tab"
              aria-selected={box === 'received'}
              onClick={() => changeBox('received')}
              className={cn(
                'inline-flex h-8 items-center rounded-md px-3.5 text-xs font-semibold transition-colors',
                box === 'received'
                  ? 'bg-surface text-primary shadow-sm'
                  : 'text-ink-secondary hover:text-ink',
              )}
            >
              Diterima
            </button>
          ) : null}
          {canSent ? (
            <button
              type="button"
              role="tab"
              aria-selected={box === 'sent'}
              onClick={() => changeBox('sent')}
              className={cn(
                'inline-flex h-8 items-center rounded-md px-3.5 text-xs font-semibold transition-colors',
                box === 'sent'
                  ? 'bg-surface text-primary shadow-sm'
                  : 'text-ink-secondary hover:text-ink',
              )}
            >
              Terkirim
            </button>
          ) : null}
        </div>
        {hasFilters ? (
          <Button variant="ghost" size="compact" onClick={resetFilters}>
            Reset filter
          </Button>
        ) : null}
      </div>

      <FilterPanel activeCount={activeFilterCount}>
        <form
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-4"
          onSubmit={(event) => {
            event.preventDefault();
            setKeyword(keywordInput.trim());
            setPage(0);
          }}
        >
          <div className="sm:col-span-2">
            <InputField
              label="Kata kunci"
              placeholder="Judul atau kode distribusi"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
            />
          </div>
          <SelectField
            label="Status"
            value={status}
            onChange={(event) => {
              setStatus(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Semua status</option>
            {DISTRIBUTION_STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
          <div className="flex items-end">
            <Button type="submit">
              <Icon name="search" size={16} />
              Terapkan
            </Button>
          </div>
        </form>
      </FilterPanel>

      {query.isLoading ? (
        <LoadingBlock label="Memuat distribusi…" />
      ) : error ? (
        forbidden ? (
          <ForbiddenState message="Akun Anda tidak memiliki izin untuk melihat daftar distribusi ini." />
        ) : (
          <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />
        )
      ) : items.length === 0 ? (
        <EmptyState
          title={box === 'received' ? 'Belum ada distribusi diterima' : 'Belum ada distribusi terkirim'}
          description={
            hasFilters
              ? 'Tidak ada distribusi yang cocok dengan filter saat ini. Longgarkan filter atau ubah kata kunci.'
              : box === 'received'
                ? 'Distribusi yang dikirim kepada Anda akan muncul di sini beserta status pembacaannya.'
                : 'Distribusi yang Anda kirim akan muncul di sini beserta status pembacaan penerima.'
          }
          action={
            box === 'sent' && canCreate ? (
              <Button onClick={() => setCreateOpen(true)}>
                <Icon name="plus" size={16} />
                Distribusi baru
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ul className="space-y-2" aria-label="Daftar distribusi">
            {items.map((distribution) => (
              <DistributionRow key={distribution.id} distribution={distribution} box={box} />
            ))}
          </ul>
          {query.data ? (
            <PaginationBar
              className="mt-4"
              page={page}
              size={pageSize}
              totalItems={query.data.totalItems}
              totalPages={query.data.totalPages}
              itemLabel="distribusi"
              onPageChange={setPage}
              onSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          ) : null}
        </>
      )}

      <CreateDistributionDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(detail) => {
          setCreateOpen(false);
          navigate(`/distributions/${detail.summary.id}`);
        }}
      />
    </>
  );
}
