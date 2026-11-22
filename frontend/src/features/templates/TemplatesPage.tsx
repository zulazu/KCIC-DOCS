import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { searchAccessDirectory } from '../access/api';
import { useAuth } from '../auth/AuthContext';
import { listDocumentCategories } from '../documents/api';
import { listActiveUnits } from '../workspaces/api';
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
import { FilterPanel } from '../../shared/components/FilterPanel';
import { Icon } from '../../shared/components/Icon';
import { PaginationBar } from '../../shared/components/PaginationBar';
import { PageHeader } from '../../shared/components/Surface';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDateTime } from '../../shared/utils/format';
import {
  type TemplateStatus,
  type TemplateSummary,
  TEMPLATE_STATUS_OPTIONS,
  listTemplates,
  templateStatusLabels,
} from './api';
import { CreateTemplateDialog } from './CreateTemplateDialog';

const DEFAULT_PAGE_SIZE = 10;
const DIRECTORY_CAPABILITIES = [
  'document.manage_access',
  'workspace.manage',
  'folder.update',
  'distribution.create',
];

function versionText(template: TemplateSummary): string {
  if (template.currentPublishedVersionNumber === null) {
    return 'Belum ada versi terbit';
  }
  return `Versi ${
    template.currentPublishedVersionLabel ?? template.currentPublishedVersionNumber
  }`;
}

function TemplateRow({ template }: { template: TemplateSummary }) {
  return (
    <li>
      <Link
        to={`/templates/${template.id}`}
        className="group flex flex-col gap-2.5 rounded-lg border border-line px-4 py-3.5 transition-colors hover:border-primary/40 hover:bg-primary/[0.03] focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none sm:flex-row sm:items-center"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
          <Icon name="template" size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate text-sm font-semibold text-ink group-hover:text-primary">
              {template.templateName}
            </p>
            <StatusBadge
              status={template.status}
              label={templateStatusLabels[template.status]}
            />
          </div>
          <p className="mt-0.5 truncate text-xs text-ink-secondary">
            {template.templateCode} · {template.documentTitle ?? 'Dokumen sumber'}
            {template.documentCode ? ` (${template.documentCode})` : ''}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-secondary">
            <span>{template.categoryName ?? 'Tanpa kategori'}</span>
            <span aria-hidden="true">·</span>
            <span>{template.ownerOrganizationUnitName ?? template.ownerUserName ?? '—'}</span>
            <span aria-hidden="true">·</span>
            <span>{versionText(template)}</span>
          </div>
        </div>
        <div className="shrink-0 text-left text-xs text-ink-secondary sm:text-right">
          <p>{template.status === 'PUBLISHED' ? 'Dipublikasikan' : 'Diperbarui'}</p>
          <p className="font-medium text-ink">
            {formatDateTime(
              template.status === 'PUBLISHED' ? template.publishedAt : template.updatedAt,
            )}
          </p>
          {template.publishedByName ? <p className="mt-1">oleh {template.publishedByName}</p> : null}
        </div>
      </Link>
    </li>
  );
}

export function TemplatesPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const capabilities = me?.capabilities ?? [];
  const canCreate = capabilities.includes('template.create');
  const canGovern =
    capabilities.includes('template.publish') || capabilities.includes('template.archive');
  const canUseDirectory = DIRECTORY_CAPABILITIES.some((code) => capabilities.includes(code));

  const [status, setStatus] = useState<TemplateStatus>('PUBLISHED');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [ownerUserId, setOwnerUserId] = useState('');
  const [organizationUnitId, setOrganizationUnitId] = useState('');
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);

  const categoriesQuery = useQuery({
    queryKey: ['master-data', 'categories'],
    queryFn: listDocumentCategories,
  });
  const unitsQuery = useQuery({
    queryKey: ['organization-units', 'active'],
    queryFn: listActiveUnits,
  });
  const directoryQuery = useQuery({
    queryKey: ['access-directory', 'template-owner'],
    queryFn: () => searchAccessDirectory(''),
    enabled: canUseDirectory,
  });

  const query = useQuery({
    queryKey: [
      'templates',
      { status, keyword, categoryId, ownerUserId, organizationUnitId, page, pageSize },
    ],
    queryFn: () =>
      listTemplates({
        status,
        keyword: keyword || undefined,
        categoryId: categoryId || undefined,
        ownerUserId: ownerUserId || undefined,
        organizationUnitId: organizationUnitId || undefined,
        page,
        size: pageSize,
      }),
    placeholderData: keepPreviousData,
  });

  const hasFilters = Boolean(
    keyword || categoryId || ownerUserId || organizationUnitId || status !== 'PUBLISHED',
  );
  const activeFilterCount = [keyword, categoryId, ownerUserId, organizationUnitId].filter(Boolean)
    .length + (status !== 'PUBLISHED' ? 1 : 0);
  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;
  const items = query.data?.items ?? [];

  function resetFilters() {
    setStatus('PUBLISHED');
    setKeywordInput('');
    setKeyword('');
    setCategoryId('');
    setOwnerUserId('');
    setOrganizationUnitId('');
    setPage(0);
  }

  return (
    <>
      <PageHeader
        title="Template"
        action={
          canCreate ? (
            <Button onClick={() => setCreateOpen(true)}>
              <Icon name="plus" size={16} />
              Template baru
            </Button>
          ) : undefined
        }
      />

      <FilterPanel activeCount={activeFilterCount}>
        <form
          className="grid grid-cols-1 gap-3 sm:grid-cols-2 xl:grid-cols-5"
          onSubmit={(event) => {
            event.preventDefault();
            setKeyword(keywordInput.trim());
            setPage(0);
          }}
        >
          <div className="sm:col-span-2">
            <InputField
              label="Kata kunci"
              placeholder="Nama atau kode template"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
            />
          </div>
          <SelectField
            label="Kategori"
            value={categoryId}
            onChange={(event) => {
              setCategoryId(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Semua kategori</option>
            {(categoriesQuery.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.categoryName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Unit pemilik"
            value={organizationUnitId}
            onChange={(event) => {
              setOrganizationUnitId(event.target.value);
              setPage(0);
            }}
          >
            <option value="">Semua unit</option>
            {(unitsQuery.data ?? []).map((unit) => (
              <option key={unit.id} value={unit.id}>
                {unit.unitName}
              </option>
            ))}
          </SelectField>
          {canUseDirectory ? (
            <SelectField
              label="Pemilik dokumen"
              value={ownerUserId}
              onChange={(event) => {
                setOwnerUserId(event.target.value);
                setPage(0);
              }}
            >
              <option value="">Semua pemilik</option>
              {(directoryQuery.data?.users ?? []).map((user) => (
                <option key={user.id} value={user.id}>
                  {user.name}
                </option>
              ))}
            </SelectField>
          ) : null}
          {canGovern || canCreate ? (
            <SelectField
              label="Status"
              value={status}
              onChange={(event) => {
                setStatus(event.target.value as TemplateStatus);
                setPage(0);
              }}
            >
              {TEMPLATE_STATUS_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </SelectField>
          ) : null}
          <div className="flex items-end justify-end gap-2 sm:col-span-2 xl:col-span-4">
            <Button type="submit">
              <Icon name="search" size={16} />
              Terapkan
            </Button>
            {hasFilters ? (
              <Button variant="ghost" size="compact" onClick={resetFilters}>
                Reset
              </Button>
            ) : null}
          </div>
        </form>
      </FilterPanel>

      {query.isLoading ? (
        <LoadingBlock label="Memuat template…" />
      ) : error ? (
        forbidden ? (
          <ForbiddenState message="Akun Anda tidak memiliki izin untuk melihat template dengan status ini." />
        ) : (
          <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />
        )
      ) : items.length === 0 ? (
        <EmptyState
          title={
            status === 'PUBLISHED' ? 'Belum ada template terbit' : 'Tidak ada template pada status ini'
          }
          description={
            hasFilters
              ? 'Tidak ada template yang cocok dengan filter saat ini. Longgarkan filter atau ubah kata kunci.'
              : status === 'PUBLISHED'
                ? 'Template resmi akan muncul di sini setelah dokumen sumber dipublikasikan sebagai template.'
                : 'Template draft atau arsip yang boleh Anda akses akan muncul di sini.'
          }
          action={
            canCreate ? (
              <Button onClick={() => setCreateOpen(true)}>
                <Icon name="plus" size={16} />
                Template baru
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ul className="space-y-2" aria-label="Daftar template">
            {items.map((template) => (
              <TemplateRow key={template.id} template={template} />
            ))}
          </ul>
          {query.data ? (
            <PaginationBar
              className="mt-4"
              page={page}
              size={pageSize}
              totalItems={query.data.totalItems}
              totalPages={query.data.totalPages}
              itemLabel="template"
              onPageChange={setPage}
              onSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          ) : null}
        </>
      )}

      <CreateTemplateDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={(detail) => {
          setCreateOpen(false);
          navigate(`/templates/${detail.summary.id}`);
        }}
      />
    </>
  );
}
