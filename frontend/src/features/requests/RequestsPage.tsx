import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import type { RequestScope, RequestSummary } from '../../shared/api/types';
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
import { listRequests } from './api';
import { CreateRequestDialog } from './CreateRequestDialog';
import { REQUEST_STATUS_OPTIONS, REQUEST_TYPE_OPTIONS, requestTypeLabel } from './labels';

const DEFAULT_PAGE_SIZE = 10;

const ASSIGNED_CAPABILITIES = ['request.view_assigned', 'request.approve', 'request.fulfill'];

function RequestRow({ request }: { request: RequestSummary }) {
  return (
    <li>
      <Link
        to={`/requests/${request.id}`}
        className="group flex flex-col gap-2.5 rounded-lg border border-line px-4 py-3.5 transition-colors hover:border-primary/40 hover:bg-primary/[0.03] focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none sm:flex-row sm:items-center"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
          <Icon name="document" size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate text-sm font-semibold text-ink group-hover:text-primary">
              {request.title}
            </p>
            <StatusBadge status={request.status} />
          </div>
          <p className="mt-0.5 truncate text-xs text-ink-secondary">
            {request.requestCode} · {requestTypeLabel(request.requestType)}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-secondary">
            <span>Pemohon: {request.requesterName ?? '—'}</span>
            <span aria-hidden="true">·</span>
            <span>Approver: {request.assignedToName ?? '—'}</span>
            {request.targetDocumentTitle ? (
              <>
                <span aria-hidden="true">·</span>
                <span className="truncate">Dokumen: {request.targetDocumentTitle}</span>
              </>
            ) : null}
            {request.targetOrganizationUnitName ? (
              <>
                <span aria-hidden="true">·</span>
                <span>Unit: {request.targetOrganizationUnitName}</span>
              </>
            ) : null}
          </div>
        </div>
        <div className="shrink-0 text-left text-xs text-ink-secondary sm:text-right">
          <p>Dibutuhkan</p>
          <p className="font-medium text-ink">{formatDate(request.neededByDate)}</p>
          <p className="mt-1">{formatDateTime(request.updatedAt)}</p>
        </div>
      </Link>
    </li>
  );
}

export function RequestsPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const capabilities = me?.capabilities ?? [];
  const canOwn = capabilities.includes('request.view_own');
  const canAssigned = ASSIGNED_CAPABILITIES.some((capability) => capabilities.includes(capability));
  const canCreate = capabilities.includes('request.create');

  const [scope, setScope] = useState<RequestScope>(canOwn ? 'own' : 'assigned');
  const [status, setStatus] = useState('');
  const [type, setType] = useState('');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);

  const query = useQuery({
    queryKey: ['requests', scope, { status, type, keyword, page, pageSize }],
    queryFn: () =>
      listRequests({
        scope,
        status: status || undefined,
        type: type || undefined,
        keyword: keyword || undefined,
        page,
        size: pageSize,
      }),
    enabled: scope === 'own' ? canOwn : canAssigned,
    placeholderData: keepPreviousData,
  });

  const hasFilters = Boolean(status || type || keyword);
  const activeFilterCount = [status, type, keyword].filter(Boolean).length;
  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;

  if (!canOwn && !canAssigned) {
    return (
      <div className="rounded-card border border-line bg-surface">
        <ForbiddenState message="Akun Anda tidak memiliki izin untuk membuka halaman request." />
      </div>
    );
  }

  function changeScope(next: RequestScope) {
    setScope(next);
    setPage(0);
  }

  function resetFilters() {
    setStatus('');
    setType('');
    setKeywordInput('');
    setKeyword('');
    setPage(0);
  }

  const items = query.data?.items ?? [];

  return (
    <>
      <PageHeader
        title="Requests"
        action={
          canCreate ? (
            <Button onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={16} />
              Request baru
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div
          role="tablist"
          aria-label="Tampilan request"
          className="inline-flex rounded-lg border border-line bg-surface-muted p-0.5"
        >
          {canOwn ? (
            <button
              type="button"
              role="tab"
              aria-selected={scope === 'own'}
              onClick={() => changeScope('own')}
              className={cn(
                'inline-flex h-8 items-center rounded-md px-3.5 text-xs font-semibold transition-colors',
                scope === 'own'
                  ? 'bg-surface text-primary shadow-sm'
                  : 'text-ink-secondary hover:text-ink',
              )}
            >
              Request saya
            </button>
          ) : null}
          {canAssigned ? (
            <button
              type="button"
              role="tab"
              aria-selected={scope === 'assigned'}
              onClick={() => changeScope('assigned')}
              className={cn(
                'inline-flex h-8 items-center rounded-md px-3.5 text-xs font-semibold transition-colors',
                scope === 'assigned'
                  ? 'bg-surface text-primary shadow-sm'
                  : 'text-ink-secondary hover:text-ink',
              )}
            >
              Tugas saya
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
              placeholder="Judul atau kode request"
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
            {REQUEST_STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Tipe"
            value={type}
            onChange={(event) => {
              setType(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Semua tipe</option>
            {REQUEST_TYPE_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </SelectField>
          <div className="flex items-end sm:col-span-2 xl:col-span-4 xl:justify-end">
            <Button type="submit">
              <Icon name="search" size={16} />
              Terapkan
            </Button>
          </div>
        </form>
      </FilterPanel>

      {query.isLoading ? (
        <LoadingBlock label="Memuat request…" />
      ) : error ? (
        forbidden ? (
          <ForbiddenState message="Akun Anda tidak memiliki izin untuk melihat daftar request ini." />
        ) : (
          <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />
        )
      ) : items.length === 0 ? (
        <EmptyState
          title={scope === 'own' ? 'Belum ada request' : 'Tidak ada tugas request'}
          description={
            hasFilters
              ? 'Tidak ada request yang cocok dengan filter saat ini. Longgarkan filter atau ubah kata kunci.'
              : scope === 'own'
                ? 'Request yang Anda ajukan akan muncul di sini beserta status review dan pemenuhannya.'
                : 'Request yang menunggu keputusan atau pemenuhan Anda akan muncul di sini.'
          }
          action={
            scope === 'own' && canCreate ? (
              <Button onClick={() => setCreateOpen(true)}>
                <Icon name="plus" size={16} />
                Request baru
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ul className="space-y-2" aria-label="Daftar request">
            {items.map((request) => (
              <RequestRow key={request.id} request={request} />
            ))}
          </ul>
          {query.data ? (
            <PaginationBar
              className="mt-4"
              page={page}
              size={pageSize}
              totalItems={query.data.totalItems}
              totalPages={query.data.totalPages}
              itemLabel="request"
              onPageChange={setPage}
              onSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          ) : null}
        </>
      )}

      <CreateRequestDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(detail) => {
          setCreateOpen(false);
          navigate(`/requests/${detail.summary.id}`);
        }}
      />
    </>
  );
}
