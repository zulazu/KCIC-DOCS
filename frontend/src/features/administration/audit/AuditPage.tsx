import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError } from '../../../shared/api/client';
import { Badge } from '../../../shared/components/Badge';
import { Button } from '../../../shared/components/Button';
import {
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../../../shared/components/Feedback';
import { InputField, SelectField } from '../../../shared/components/Field';
import { FilterPanel } from '../../../shared/components/FilterPanel';
import { Icon } from '../../../shared/components/Icon';
import { PaginationBar } from '../../../shared/components/PaginationBar';
import { Card, PageHeader } from '../../../shared/components/Surface';
import { Table, Td, Th, THead, Tr } from '../../../shared/components/Table';
import { apiMessage } from '../../../shared/utils/apiMessage';
import { formatDateTime } from '../../../shared/utils/format';
import { type AuditEvent, type AuditSearchParams, searchAuditEvents } from './api';

const DEFAULT_PAGE_SIZE = 10;

const RESOURCE_TYPES = [
  'DOCUMENT',
  'USER',
  'WORKSPACE',
  'FOLDER',
  'REQUEST',
  'DISTRIBUTION',
  'TEMPLATE',
  'ROLE',
] as const;

const OUTCOMES = ['SUCCESS', 'FAILURE', 'DENIED'] as const;

type FilterState = {
  actor: string;
  action: string;
  resourceType: string;
  resourceId: string;
  outcome: string;
  from: string;
  to: string;
};

const EMPTY_FILTERS: FilterState = {
  actor: '',
  action: '',
  resourceType: '',
  resourceId: '',
  outcome: '',
  from: '',
  to: '',
};

function outcomeTone(outcome: string) {
  if (outcome === 'SUCCESS') {
    return 'success' as const;
  }
  if (outcome === 'FAILURE') {
    return 'danger' as const;
  }
  return 'warning' as const;
}

function outcomeLabel(outcome: string): string {
  if (outcome === 'SUCCESS') return 'Sukses';
  if (outcome === 'FAILURE') return 'Gagal';
  if (outcome === 'DENIED') return 'Ditolak';
  return outcome;
}

function AuditRow({ event }: { event: AuditEvent }) {
  const metadata = Object.entries(event.safeMetadata ?? {});
  return (
    <Tr className="align-top">
      <Td className="text-xs whitespace-nowrap text-ink-secondary tabular-nums">
        {formatDateTime(event.occurredAt)}
      </Td>
      <Td>
        <p className="font-semibold text-ink">
          {event.actorDisplayName ?? event.actorUsername ?? 'Sistem'}
        </p>
        <p className="text-xs text-ink-secondary">
          {event.actorUsername ?? event.actorUserId ?? '—'}
        </p>
      </Td>
      <Td>
        <p className="font-mono text-xs font-semibold text-ink">{event.actionCode}</p>
        <p className="mt-1">
          <Badge tone={outcomeTone(event.outcome)}>{outcomeLabel(event.outcome)}</Badge>
        </p>
      </Td>
      <Td>
        <p className="font-semibold text-ink">{event.resourceType}</p>
        {event.resourceId ? (
          <p className="font-mono text-xs text-ink-secondary" title={event.resourceId}>
            {event.resourceId.slice(0, 8)}…
          </p>
        ) : (
          <p className="text-xs text-ink-secondary">—</p>
        )}
      </Td>
      <Td>
        {metadata.length === 0 ? (
          <span className="text-xs text-ink-secondary">Tanpa metadata</span>
        ) : (
          <details className="group">
            <summary className="inline-flex h-8 cursor-pointer list-none items-center gap-1.5 rounded-lg border border-line-strong bg-surface px-3 text-xs font-semibold text-ink transition-colors hover:bg-surface-muted [&::-webkit-details-marker]:hidden">
              <Icon name="chevron-right" size={14} className="transition-transform group-open:rotate-90" />
              Metadata ({metadata.length})
            </summary>
            <dl className="mt-2 space-y-1 text-xs">
              {metadata.map(([key, value]) => (
                <div key={key} className="flex gap-2">
                  <dt className="font-mono text-ink-secondary">{key}:</dt>
                  <dd className="break-all text-ink">
                    {typeof value === 'string' ? value : JSON.stringify(value)}
                  </dd>
                </div>
              ))}
            </dl>
            {event.requestId ? (
              <p className="mt-2 font-mono text-[11px] text-ink-secondary">
                requestId: {event.requestId}
              </p>
            ) : null}
          </details>
        )}
      </Td>
    </Tr>
  );
}

export function AuditPage() {
  const [filters, setFilters] = useState<FilterState>(EMPTY_FILTERS);
  const [applied, setApplied] = useState<FilterState>(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const params: AuditSearchParams = {
    actor: applied.actor.trim() || undefined,
    action: applied.action.trim() || undefined,
    resourceType: applied.resourceType || undefined,
    resourceId: applied.resourceId.trim() || undefined,
    outcome: applied.outcome || undefined,
    from: applied.from || undefined,
    to: applied.to || undefined,
    page,
    size: pageSize,
  };

  const query = useQuery({
    queryKey: ['audit-events', params],
    queryFn: () => searchAuditEvents(params),
    placeholderData: keepPreviousData,
  });

  function apply(event: FormEvent) {
    event.preventDefault();
    setPage(0);
    setApplied(filters);
  }

  function reset() {
    setFilters(EMPTY_FILTERS);
    setApplied(EMPTY_FILTERS);
    setPage(0);
  }

  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;
  const items = query.data?.items ?? [];
  const hasFilters = Object.values(applied).some((value) => value !== '');
  const activeFilterCount = Object.values(applied).filter((value) => value !== '').length;

  return (
    <>
      <PageHeader title="Audit Trail" />

      <FilterPanel activeCount={activeFilterCount} className="mb-4">
        <form className="grid gap-3 md:grid-cols-3 xl:grid-cols-6" onSubmit={apply}>
          <InputField
            label="Aktor"
            placeholder="Username atau nama"
            value={filters.actor}
            onChange={(event) => setFilters({ ...filters, actor: event.target.value })}
          />
          <InputField
            label="Aksi"
            placeholder="Contoh: LOGIN_SUCCESS"
            className="font-mono"
            value={filters.action}
            onChange={(event) => setFilters({ ...filters, action: event.target.value })}
          />
          <SelectField
            label="Tipe resource"
            value={filters.resourceType}
            onChange={(event) => setFilters({ ...filters, resourceType: event.target.value })}
          >
            <option value="">Semua resource</option>
            {RESOURCE_TYPES.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Outcome"
            value={filters.outcome}
            onChange={(event) => setFilters({ ...filters, outcome: event.target.value })}
          >
            <option value="">Semua outcome</option>
            {OUTCOMES.map((outcome) => (
              <option key={outcome} value={outcome}>
                {outcome}
              </option>
            ))}
          </SelectField>
          <InputField
            label="Dari tanggal"
            type="date"
            value={filters.from}
            onChange={(event) => setFilters({ ...filters, from: event.target.value })}
          />
          <InputField
            label="Sampai tanggal"
            type="date"
            value={filters.to}
            onChange={(event) => setFilters({ ...filters, to: event.target.value })}
          />
          <div className="flex items-end gap-2 md:col-span-3 xl:col-span-6">
            <Button type="submit" loading={query.isFetching && !query.isLoading}>
              <Icon name="search" size={16} />
              Telusuri
            </Button>
            <Button type="button" variant="secondary" onClick={reset}>
              Reset
            </Button>
          </div>
        </form>
      </FilterPanel>

      <Card>
        {query.isLoading ? (
          <LoadingBlock label="Memuat audit event…" />
        ) : forbidden ? (
          <ForbiddenState message="Akun Anda tidak memiliki izin melihat audit trail." />
        ) : error ? (
          <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />
        ) : items.length === 0 ? (
          <EmptyState
            title="Tidak ada audit event"
            description={
              hasFilters
                ? 'Tidak ada event yang cocok dengan filter saat ini. Longgarkan filter atau ubah rentang tanggal.'
                : 'Audit event akan muncul di sini setelah aktivitas bisnis tercatat.'
            }
          />
        ) : (
          <Table className="min-w-[920px]" caption="Daftar audit event">
            <THead>
              <Th>Waktu</Th>
              <Th>Aktor</Th>
              <Th>Aksi</Th>
              <Th>Resource</Th>
              <Th>Metadata</Th>
            </THead>
            <tbody>
              {items.map((event) => (
                <AuditRow key={event.id} event={event} />
              ))}
            </tbody>
          </Table>
        )}
      </Card>

      {query.data && items.length > 0 ? (
        <PaginationBar
          className="mt-4"
          page={page}
          size={pageSize}
          totalItems={query.data.totalItems}
          totalPages={query.data.totalPages}
          itemLabel="event"
          onPageChange={setPage}
          onSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
        />
      ) : null}
    </>
  );
}
