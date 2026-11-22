import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import type { DocumentSummary } from '../../shared/api/types';
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
import { formatDateTime } from '../../shared/utils/format';
import { useAuth } from '../auth/AuthContext';
import { listActiveUnits, listWorkspaces } from '../workspaces/api';
import {
  listDocumentCategories,
  listDocumentClassifications,
  listDocumentTags,
  listDocumentTypes,
  listDocuments,
  listFavorites,
} from './api';
import { DocumentFormDialog } from './DocumentFormDialog';

const DEFAULT_PAGE_SIZE = 10;

type CatalogTab = 'catalog' | 'favorites';

function DocumentRow({ document }: { document: DocumentSummary }) {
  return (
    <li>
      <Link
        to={`/documents/${document.id}`}
        className="group flex flex-col gap-2.5 rounded-lg border border-line px-4 py-3.5 transition-colors hover:border-primary/40 hover:bg-primary/[0.03] focus-visible:ring-2 focus-visible:ring-primary/40 focus-visible:outline-none sm:flex-row sm:items-center"
      >
        <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
          <Icon name={document.contentSource === 'EXTERNAL_LINK' ? 'link' : 'document'} size={18} />
        </span>
        <div className="min-w-0 flex-1">
          <div className="flex flex-wrap items-center gap-2">
            <p className="truncate text-sm font-semibold text-ink group-hover:text-primary">
              {document.title}
            </p>
            <StatusBadge status={document.status} />
            {document.favorited ? (
              <Icon name="star" size={14} className="text-gold-foreground" />
            ) : null}
          </div>
          <p className="mt-0.5 truncate text-xs text-ink-secondary">
            {document.documentCode ?? 'Tanpa kode'} · {document.workspaceName ?? 'Workspace'}
            {document.folderName ? ` / ${document.folderName}` : ''}
          </p>
          <div className="mt-1.5 flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-secondary">
            <span>{document.documentTypeName ?? '—'}</span>
            <span aria-hidden="true">·</span>
            <span>{document.classificationName ?? '—'}</span>
            <span aria-hidden="true">·</span>
            <span>
              {document.currentVersionNumber
                ? `Versi ${document.currentVersionLabel ?? document.currentVersionNumber}`
                : 'Belum ada versi'}
            </span>
            <span aria-hidden="true">·</span>
            <span>{document.ownerOrganizationUnitName ?? document.ownerUserName ?? '—'}</span>
          </div>
        </div>
        <div className="shrink-0 text-left text-xs text-ink-secondary sm:text-right">
          <p>Diperbarui</p>
          <p className="font-medium text-ink">{formatDateTime(document.updatedAt)}</p>
        </div>
      </Link>
    </li>
  );
}

export function DocumentsPage() {
  const { me } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [tab, setTab] = useState<CatalogTab>('catalog');
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [workspaceId, setWorkspaceId] = useState(searchParams.get('workspaceId') ?? '');
  const [categoryId, setCategoryId] = useState('');
  const [documentTypeId, setDocumentTypeId] = useState('');
  const [classificationId, setClassificationId] = useState('');
  const [status, setStatus] = useState('');
  const [sort, setSort] = useState('-updatedAt');
  const [includeArchived, setIncludeArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [createOpen, setCreateOpen] = useState(false);

  const workspacesQuery = useQuery({
    queryKey: ['workspaces', true],
    queryFn: () => listWorkspaces(true),
  });
  const unitsQuery = useQuery({ queryKey: ['organization-units', 'active'], queryFn: listActiveUnits });
  const categoriesQuery = useQuery({ queryKey: ['master-data', 'categories'], queryFn: listDocumentCategories });
  const typesQuery = useQuery({ queryKey: ['master-data', 'types'], queryFn: listDocumentTypes });
  const classificationsQuery = useQuery({
    queryKey: ['master-data', 'classifications'],
    queryFn: listDocumentClassifications,
  });
  const tagsQuery = useQuery({ queryKey: ['master-data', 'tags'], queryFn: listDocumentTags });

  const catalogQuery = useQuery({
    queryKey: [
      'documents',
      'catalog',
      {
        keyword,
        workspaceId,
        categoryId,
        documentTypeId,
        classificationId,
        status,
        sort,
        includeArchived,
        page,
        pageSize,
      },
    ],
    queryFn: () =>
      listDocuments({
        keyword: keyword || undefined,
        workspaceId: workspaceId || undefined,
        categoryId: categoryId || undefined,
        documentTypeId: documentTypeId || undefined,
        classificationId: classificationId || undefined,
        status: status || undefined,
        sort,
        includeArchived,
        page,
        size: pageSize,
      }),
    enabled: tab === 'catalog',
  });

  const favoritesQuery = useQuery({
    queryKey: ['documents', 'favorites'],
    queryFn: listFavorites,
    enabled: tab === 'favorites',
  });

  const favoriteItems = useMemo(() => {
    const term = keyword.trim().toLowerCase();
    return (favoritesQuery.data ?? []).filter(
      (document) =>
        (!workspaceId || document.workspaceId === workspaceId) &&
        (!term ||
          document.title.toLowerCase().includes(term) ||
          (document.documentCode ?? '').toLowerCase().includes(term)),
    );
  }, [favoritesQuery.data, keyword, workspaceId]);

  const workspaces = workspacesQuery.data ?? [];
  const canCreate = me?.capabilities.includes('document.create') ?? false;
  const activeQuery = tab === 'catalog' ? catalogQuery : favoritesQuery;
  const items = tab === 'catalog' ? (catalogQuery.data?.items ?? []) : favoriteItems;
  const hasFilters = Boolean(
    keyword || workspaceId || categoryId || documentTypeId || classificationId || status,
  );
  const activeFilterCount =
    [keyword, workspaceId, categoryId, documentTypeId, classificationId, status].filter(Boolean)
      .length + (includeArchived ? 1 : 0);

  function applyFilters(updater: () => void) {
    updater();
    setPage(0);
  }

  function resetFilters() {
    setKeywordInput('');
    setKeyword('');
    setWorkspaceId('');
    setCategoryId('');
    setDocumentTypeId('');
    setClassificationId('');
    setStatus('');
    setIncludeArchived(false);
    setPage(0);
  }

  const error = activeQuery.error;
  const forbidden = error instanceof ApiError && error.status === 403;

  return (
    <>
      <PageHeader
        title="Dokumen"
        action={
          canCreate ? (
            <Button
              onClick={() => setCreateOpen(true)}
              disabled={!workspaces.length}
              title={!workspaces.length ? 'Buat workspace terlebih dahulu.' : undefined}
            >
              <Icon name="plus" size={16} />
              Dokumen baru
            </Button>
          ) : undefined
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <div
          role="tablist"
          aria-label="Tampilan dokumen"
          className="inline-flex rounded-lg border border-line bg-surface-muted p-0.5"
        >
          {(
            [
              ['catalog', 'Katalog'],
              ['favorites', 'Favorit saya'],
            ] as const
          ).map(([value, label]) => (
            <button
              key={value}
              type="button"
              role="tab"
              aria-selected={tab === value}
              onClick={() => {
                setTab(value);
                setPage(0);
              }}
              className={cn(
                'inline-flex h-8 items-center rounded-md px-3.5 text-xs font-semibold transition-colors',
                tab === value
                  ? 'bg-surface text-primary shadow-sm'
                  : 'text-ink-secondary hover:text-ink',
              )}
            >
              {label}
            </button>
          ))}
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
            applyFilters(() => setKeyword(keywordInput.trim()));
          }}
        >
          <div className="sm:col-span-2 xl:col-span-1">
            <InputField
              label="Kata kunci"
              placeholder="Judul atau kode dokumen"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
            />
          </div>
          <SelectField
            label="Workspace"
            value={workspaceId}
            onChange={(event) => applyFilters(() => setWorkspaceId(event.target.value))}
          >
            <option value="">Semua workspace</option>
            {workspaces.map((workspace) => (
              <option key={workspace.id} value={workspace.id}>
                {workspace.workspaceName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Kategori"
            value={categoryId}
            onChange={(event) => applyFilters(() => setCategoryId(event.target.value))}
          >
            <option value="">Semua kategori</option>
            {(categoriesQuery.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.categoryName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Jenis dokumen"
            value={documentTypeId}
            onChange={(event) => applyFilters(() => setDocumentTypeId(event.target.value))}
          >
            <option value="">Semua jenis</option>
            {(typesQuery.data ?? []).map((type) => (
              <option key={type.id} value={type.id}>
                {type.typeName}
              </option>
            ))}
          </SelectField>
          <SelectField
            label="Klasifikasi"
            value={classificationId}
            onChange={(event) => applyFilters(() => setClassificationId(event.target.value))}
          >
            <option value="">Semua klasifikasi</option>
            {(classificationsQuery.data ?? []).map((classification) => (
              <option key={classification.id} value={classification.id}>
                {classification.classificationName}
              </option>
            ))}
          </SelectField>
          {tab === 'catalog' ? (
            <>
              <SelectField
                label="Status"
                value={status}
                onChange={(event) => applyFilters(() => setStatus(event.target.value))}
              >
                <option value="">Semua status</option>
                <option value="DRAFT">Draft</option>
                <option value="ACTIVE">Aktif</option>
                <option value="ARCHIVED">Arsip</option>
              </SelectField>
              <SelectField
                label="Urutkan"
                value={sort}
                onChange={(event) => applyFilters(() => setSort(event.target.value))}
              >
                <option value="-updatedAt">Terakhir diperbarui</option>
                <option value="title">Judul A-Z</option>
                <option value="-createdAt">Terbaru dibuat</option>
              </SelectField>
            </>
          ) : null}
          <div className="flex items-end sm:col-span-2 xl:col-span-1 xl:justify-end">
            <Button type="submit">
              <Icon name="search" size={16} />
              Terapkan
            </Button>
          </div>
        </form>
        {tab === 'catalog' ? (
          <label className="mt-3 flex w-fit cursor-pointer items-center gap-2 text-xs font-medium text-ink-secondary">
            <input
              type="checkbox"
              className="size-4 rounded border-line-strong accent-primary"
              checked={includeArchived}
              onChange={(event) => applyFilters(() => setIncludeArchived(event.target.checked))}
            />
            Tampilkan dokumen arsip
          </label>
        ) : null}
      </FilterPanel>

      {activeQuery.isLoading ? (
        <LoadingBlock label="Memuat dokumen…" />
      ) : error ? (
        forbidden ? (
          <ForbiddenState message="Akun Anda tidak memiliki izin untuk melihat dokumen pada scope ini." />
        ) : (
          <ErrorState message={apiMessage(error)} onRetry={() => void activeQuery.refetch()} />
        )
      ) : items.length === 0 ? (
        <EmptyState
          title={tab === 'favorites' ? 'Belum ada dokumen favorit' : 'Dokumen tidak ditemukan'}
          description={
            tab === 'favorites'
              ? 'Tandai dokumen sebagai favorit dari halaman detail agar mudah ditemukan kembali.'
              : hasFilters
                ? 'Tidak ada dokumen yang cocok dengan filter saat ini. Longgarkan filter atau ubah kata kunci.'
                : 'Belum ada dokumen aktif yang dapat Anda akses. Dokumen arsip disembunyikan; centang "Tampilkan dokumen arsip" untuk melihatnya.'
          }
          action={
            tab === 'catalog' && canCreate && workspaces.length ? (
              <Button onClick={() => setCreateOpen(true)}>
                <Icon name="plus" size={16} />
                Dokumen baru
              </Button>
            ) : undefined
          }
        />
      ) : (
        <>
          <ul className="space-y-2" aria-label="Daftar dokumen">
            {items.map((document) => (
              <DocumentRow key={document.id} document={document} />
            ))}
          </ul>
          {tab === 'catalog' && catalogQuery.data ? (
            <PaginationBar
              className="mt-4"
              page={page}
              size={pageSize}
              totalItems={catalogQuery.data.totalItems}
              totalPages={catalogQuery.data.totalPages}
              itemLabel="dokumen"
              onPageChange={setPage}
              onSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          ) : null}
        </>
      )}

      <DocumentFormDialog
        open={createOpen}
        mode="create"
        defaultWorkspaceId={workspaceId || workspaces[0]?.id}
        workspaces={workspaces.filter((workspace) => workspace.status === 'ACTIVE')}
        units={unitsQuery.data ?? []}
        categories={categoriesQuery.data ?? []}
        types={typesQuery.data ?? []}
        classifications={classificationsQuery.data ?? []}
        tags={tagsQuery.data ?? []}
        onClose={() => setCreateOpen(false)}
        onSaved={(detail) => {
          setCreateOpen(false);
          navigate(`/documents/${detail.summary.id}`);
        }}
      />
    </>
  );
}
