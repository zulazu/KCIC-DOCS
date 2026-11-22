import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import type {
  DocumentDetail,
  DocumentSummary,
  DocumentVersion,
  FilePolicy,
} from '../../shared/api/types';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog, Dialog } from '../../shared/components/Dialog';
import {
  Alert,
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../../shared/components/Feedback';
import { InputField, TextareaField } from '../../shared/components/Field';
import { Icon } from '../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { formatDate, formatDateTime, humanizeStatus } from '../../shared/utils/format';
import { AccessPanel } from '../access/AccessPanel';
import { listActiveUnits, listWorkspaces } from '../workspaces/api';
import {
  addFavorite,
  canPreviewInline,
  changeDocumentStatus,
  fetchVersionContent,
  getDocument,
  getFilePolicy,
  listActivity,
  listDocumentCategories,
  listDocumentClassifications,
  listDocumentTags,
  listDocumentTypes,
  listVersions,
  openExternalLink,
  promoteVersion,
  registerExternalLink,
  removeFavorite,
  saveBlob,
  uploadVersion,
} from './api';
import { DocumentFormDialog } from './DocumentFormDialog';

type StatusAction = 'activate' | 'archive' | 'restore';

interface PreviewState {
  versionId: string;
  url: string;
  contentType: string | null;
  filename: string | null;
}

function formatBytes(value: number | null): string {
  if (value === null || value === 0) {
    return '—';
  }
  if (value < 1024) {
    return `${value} B`;
  }
  if (value < 1024 * 1024) {
    return `${(value / 1024).toFixed(1)} KB`;
  }
  return `${(value / (1024 * 1024)).toFixed(1)} MB`;
}

function classificationTone(rank: number) {
  if (rank >= 3) {
    return 'danger' as const;
  }
  if (rank === 2) {
    return 'warning' as const;
  }
  return 'neutral' as const;
}

function extensionAllowed(file: File, policy: FilePolicy | undefined): string | null {
  if (!policy) {
    return null;
  }
  const dot = file.name.lastIndexOf('.');
  const extension = dot >= 0 ? file.name.slice(dot + 1).toLowerCase() : '';
  if (!extension || !policy.allowedExtensions.includes(extension)) {
    return `Ekstensi file harus salah satu dari: ${policy.allowedExtensions.join(', ')}.`;
  }
  if (file.size <= 0) {
    return 'File kosong tidak dapat diunggah.';
  }
  if (file.size > policy.maxFileSizeBytes) {
    return `Ukuran file melebihi batas ${(policy.maxFileSizeBytes / (1024 * 1024)).toFixed(0)} MB.`;
  }
  return null;
}

function VersionRow({
  version,
  document,
  busy,
  downloading,
  onPreview,
  onDownload,
  onPromote,
  onOpenLink,
}: {
  version: DocumentVersion;
  document: DocumentDetail;
  busy: boolean;
  downloading: boolean;
  onPreview: (version: DocumentVersion) => void;
  onDownload: (version: DocumentVersion) => void;
  onPromote: (version: DocumentVersion) => void;
  onOpenLink: (version: DocumentVersion) => void;
}) {
  const isExternal = version.contentSource === 'EXTERNAL_LINK';
  return (
    <li className="flex flex-col gap-3 px-5 py-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold text-ink">
            Versi {version.versionLabel ?? version.versionNumber}
          </span>
          <StatusBadge status={version.status} />
          <Badge tone={isExternal ? 'gold' : 'neutral'}>
            {isExternal ? 'Tautan eksternal' : 'File tersimpan'}
          </Badge>
        </div>
        <p className="mt-1 text-xs text-ink-secondary">
          {isExternal
            ? version.externalHost ?? 'Tautan Office/eksternal'
            : `${version.originalFilename ?? '—'} · ${formatBytes(version.sizeBytes)}`}
        </p>
        <p className="mt-0.5 text-xs text-ink-secondary">
          {version.changeSummary ?? 'Tanpa catatan perubahan'} ·{' '}
          {version.createdByName ?? 'Sistem'} · {formatDateTime(version.createdAt)}
        </p>
        {version.checksumSha256 ? (
          <p className="mt-1 truncate font-mono text-[11px] text-ink-secondary">
            SHA-256 {version.checksumSha256}
          </p>
        ) : null}
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-1.5">
        {isExternal ? (
          <Button
            variant="secondary"
            size="compact"
            disabled={busy}
            onClick={() => onOpenLink(version)}
          >
            <Icon name="link" size={14} />
            Buka tautan
          </Button>
        ) : (
          <>
            {document.permissions.canPreview && version.canPreview ? (
              <Button
                variant="secondary"
                size="compact"
                disabled={busy}
                onClick={() => onPreview(version)}
              >
                <Icon name="eye" size={14} />
                Preview
              </Button>
            ) : null}
            {document.permissions.canDownload && version.canDownload ? (
              <Button
                variant="secondary"
                size="compact"
                loading={downloading}
                onClick={() => onDownload(version)}
              >
                <Icon name="download" size={14} />
                Unduh
              </Button>
            ) : null}
          </>
        )}
        {document.permissions.canCreateVersion && version.status !== 'CURRENT' ? (
          <Button variant="secondary" size="compact" disabled={busy} onClick={() => onPromote(version)}>
            <Icon name="check" size={14} />
            Jadikan versi aktif
          </Button>
        ) : null}
      </div>
    </li>
  );
}

export function DocumentDetailPage() {
  const { documentId = '' } = useParams();
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const [editOpen, setEditOpen] = useState(false);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [linkOpen, setLinkOpen] = useState(false);
  const [statusAction, setStatusAction] = useState<StatusAction | null>(null);
  const [preview, setPreview] = useState<PreviewState | null>(null);
  const [activityExpanded, setActivityExpanded] = useState(false);
  const [previewLoadingId, setPreviewLoadingId] = useState<string | null>(null);
  const [downloadingId, setDownloadingId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [uploadFile, setUploadFile] = useState<File | null>(null);
  const [uploadLabel, setUploadLabel] = useState('');
  const [uploadSummary, setUploadSummary] = useState('');
  const [uploadProgress, setUploadProgress] = useState<number | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);
  const [linkUrl, setLinkUrl] = useState('');
  const [linkTitle, setLinkTitle] = useState('');
  const [linkLabel, setLinkLabel] = useState('');
  const [linkSummary, setLinkSummary] = useState('');
  const [linkError, setLinkError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const detailQuery = useQuery({
    queryKey: ['document', documentId],
    queryFn: () => getDocument(documentId),
    enabled: Boolean(documentId),
  });
  const versionsQuery = useQuery({
    queryKey: ['document', documentId, 'versions'],
    queryFn: () => listVersions(documentId),
    enabled: Boolean(documentId),
  });
  const activityQuery = useQuery({
    queryKey: ['document', documentId, 'activity'],
    queryFn: () => listActivity(documentId),
    enabled: Boolean(documentId),
  });
  const filePolicyQuery = useQuery({ queryKey: ['file-policy'], queryFn: getFilePolicy });
  const workspacesQuery = useQuery({ queryKey: ['workspaces', true], queryFn: () => listWorkspaces(true) });
  const unitsQuery = useQuery({ queryKey: ['organization-units', 'active'], queryFn: listActiveUnits });
  const categoriesQuery = useQuery({ queryKey: ['master-data', 'categories'], queryFn: listDocumentCategories });
  const typesQuery = useQuery({ queryKey: ['master-data', 'types'], queryFn: listDocumentTypes });
  const classificationsQuery = useQuery({
    queryKey: ['master-data', 'classifications'],
    queryFn: listDocumentClassifications,
  });
  const tagsQuery = useQuery({ queryKey: ['master-data', 'tags'], queryFn: listDocumentTags });

  useEffect(() => {
    return () => {
      if (preview) {
        URL.revokeObjectURL(preview.url);
      }
    };
  }, [preview]);

  const invalidateDocument = () => {
    void queryClient.invalidateQueries({ queryKey: ['document', documentId] });
    void queryClient.invalidateQueries({ queryKey: ['documents'] });
  };

  const favoriteMutation = useMutation({
    mutationFn: (favorited: boolean) =>
      favorited ? removeFavorite(documentId) : addFavorite(documentId),
    onSuccess: () => {
      invalidateDocument();
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const statusMutation = useMutation({
    mutationFn: (action: StatusAction) => changeDocumentStatus(documentId, action),
    onSuccess: (detail, action) => {
      notify(
        action === 'archive'
          ? 'Dokumen diarsipkan.'
          : action === 'restore'
            ? 'Dokumen dipulihkan.'
            : 'Dokumen diaktifkan.',
      );
      queryClient.setQueryData(['document', documentId], detail);
      setStatusAction(null);
      invalidateDocument();
    },
    onError: (error) => {
      setActionError(apiMessage(error));
      setStatusAction(null);
    },
  });

  const promoteMutation = useMutation({
    mutationFn: (version: DocumentVersion) => promoteVersion(documentId, version.id),
    onSuccess: () => {
      notify('Versi aktif dokumen diperbarui.');
      invalidateDocument();
      void queryClient.invalidateQueries({ queryKey: ['document', documentId, 'versions'] });
    },
    onError: (error) => setActionError(apiMessage(error)),
  });

  const uploadMutation = useMutation({
    mutationFn: () =>
      uploadVersion(
        documentId,
        uploadFile as File,
        { versionLabel: uploadLabel.trim() || undefined, changeSummary: uploadSummary.trim() || undefined },
        (percent) => setUploadProgress(percent),
      ),
    onSuccess: () => {
      notify('Versi dokumen berhasil diunggah.');
      setUploadOpen(false);
      setUploadFile(null);
      setUploadLabel('');
      setUploadSummary('');
      setUploadProgress(null);
      setUploadError(null);
      invalidateDocument();
      void queryClient.invalidateQueries({ queryKey: ['document', documentId, 'versions'] });
      void queryClient.invalidateQueries({ queryKey: ['document', documentId, 'activity'] });
    },
    onError: (error) => {
      setUploadProgress(null);
      setUploadError(apiMessage(error));
    },
  });

  const linkMutation = useMutation({
    mutationFn: () =>
      registerExternalLink(documentId, {
        url: linkUrl.trim(),
        linkTitle: linkTitle.trim() || undefined,
        versionLabel: linkLabel.trim() || undefined,
        changeSummary: linkSummary.trim() || undefined,
      }),
    onSuccess: () => {
      notify('Tautan eksternal dicatat sebagai versi baru.');
      setLinkOpen(false);
      setLinkUrl('');
      setLinkTitle('');
      setLinkLabel('');
      setLinkSummary('');
      setLinkError(null);
      invalidateDocument();
      void queryClient.invalidateQueries({ queryKey: ['document', documentId, 'versions'] });
    },
    onError: (error) => setLinkError(apiMessage(error)),
  });

  const detail = detailQuery.data;
  const versions = useMemo(() => versionsQuery.data ?? [], [versionsQuery.data]);
  const historyVersions = versions.filter((version) => version.id !== detail?.currentVersion?.id);
  const activity = activityQuery.data ?? [];
  const visibleActivity = activityExpanded ? activity : activity.slice(0, 8);
  const policy = filePolicyQuery.data;

  function openPreview(version: DocumentVersion) {
    setActionError(null);
    setPreviewLoadingId(version.id);
    void fetchVersionContent(documentId, version.id, false)
      .then((content) => {
        if (!canPreviewInline(content.contentType)) {
          saveBlob(content.blob, content.filename);
          return;
        }
        setPreview({
          versionId: version.id,
          url: URL.createObjectURL(content.blob),
          contentType: content.contentType,
          filename: content.filename,
        });
      })
      .catch((error) => setActionError(apiMessage(error)))
      .finally(() => setPreviewLoadingId(null));
  }

  async function downloadVersion(version: DocumentVersion) {
    setActionError(null);
    setDownloadingId(version.id);
    try {
      const content = await fetchVersionContent(documentId, version.id, true);
      saveBlob(content.blob, content.filename);
    } catch (error) {
      setActionError(apiMessage(error));
    } finally {
      setDownloadingId(null);
    }
  }

  async function openLink(version: DocumentVersion) {
    setActionError(null);
    try {
      const info = await openExternalLink(documentId, version.id);
      window.open(info.url, '_blank', 'noopener,noreferrer');
      notify(info.warning, 'info');
    } catch (error) {
      setActionError(apiMessage(error));
    }
  }

  function closePreview() {
    setPreview((current) => {
      if (current) {
        URL.revokeObjectURL(current.url);
      }
      return null;
    });
  }

  function submitUpload() {
    setUploadError(null);
    if (!uploadFile) {
      setUploadError('Pilih file yang akan diunggah.');
      return;
    }
    const validation = extensionAllowed(uploadFile, policy);
    if (validation) {
      setUploadError(validation);
      return;
    }
    setUploadProgress(0);
    uploadMutation.mutate();
  }

  function submitLink() {
    setLinkError(null);
    let parsed: URL;
    try {
      parsed = new URL(linkUrl.trim());
    } catch {
      setLinkError('URL tidak valid. Gunakan tautan HTTPS lengkap.');
      return;
    }
    if (parsed.protocol !== 'https:') {
      setLinkError('Hanya tautan HTTPS yang diizinkan.');
      return;
    }
    linkMutation.mutate();
  }

  if (!documentId) {
    return <ForbiddenState />;
  }

  if (detailQuery.isLoading) {
    return <LoadingBlock label="Memuat dokumen…" />;
  }

  if (detailQuery.error) {
    const error = detailQuery.error;
    if (error instanceof ApiError && error.status === 403) {
      return (
        <ForbiddenState message="Anda tidak memiliki akses ke dokumen ini. Ajukan permintaan akses melalui menu Requests." />
      );
    }
    if (error instanceof ApiError && error.status === 404) {
      return (
        <ErrorState
          title="Dokumen tidak ditemukan"
          message="Dokumen tidak tersedia atau tautan sudah tidak berlaku."
        />
      );
    }
    return <ErrorState message={apiMessage(error)} onRetry={() => void detailQuery.refetch()} />;
  }

  if (!detail) {
    return <ErrorState message="Detail dokumen tidak tersedia." />;
  }

  const summary: DocumentSummary = detail.summary;
  const canEdit = detail.permissions.canEdit;
  const activeWorkspaces = (workspacesQuery.data ?? []).filter(
    (workspace) => workspace.status === 'ACTIVE' || workspace.id === summary.workspaceId,
  );

  return (
    <>
      <nav className="mb-3 text-xs font-medium text-ink-secondary" aria-label="Breadcrumb">
        <Link to="/documents" className="inline-flex items-center gap-1 hover:text-primary">
          <Icon name="chevron-left" size={14} />
          Dokumen
        </Link>
        <span className="mx-2 text-ink-disabled" aria-hidden="true">
          /
        </span>
        <span className="text-ink">{summary.workspaceName ?? 'Workspace'}</span>
      </nav>

      <PageHeader
        title={summary.title}
        meta={
          <>
            {summary.documentCode ? (
              <span className="rounded-md border border-line bg-surface-muted px-2 py-0.5 font-mono text-xs font-semibold text-ink-secondary">
                {summary.documentCode}
              </span>
            ) : null}
            <StatusBadge status={summary.status} />
            <Badge tone={classificationTone(summary.classificationRank)}>
              {summary.classificationName ?? 'Klasifikasi'}
            </Badge>
            <Badge tone={summary.contentSource === 'EXTERNAL_LINK' ? 'gold' : 'neutral'}>
              {summary.contentSource === 'EXTERNAL_LINK' ? 'Tautan eksternal' : 'File tersimpan'}
            </Badge>
            <button
              type="button"
              onClick={() => favoriteMutation.mutate(summary.favorited)}
              disabled={favoriteMutation.isPending}
              aria-pressed={summary.favorited}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-semibold transition-colors',
                summary.favorited
                  ? 'border-gold/50 bg-gold-soft text-gold-foreground'
                  : 'border-line-strong bg-surface text-ink-secondary hover:text-ink',
              )}
            >
              <Icon name="star" size={14} />
              {summary.favorited ? 'Favorit' : 'Tambahkan favorit'}
            </button>
          </>
        }
        action={
          <>
            {canEdit ? (
              <Button variant="secondary" onClick={() => setEditOpen(true)}>
                <Icon name="edit" size={16} />
                Ubah metadata
              </Button>
            ) : null}
            {summary.status === 'ARCHIVED' && detail.permissions.canRestore ? (
              <Button variant="secondary" onClick={() => setStatusAction('restore')}>
                <Icon name="restore" size={16} />
                Pulihkan
              </Button>
            ) : null}
            {summary.status !== 'ARCHIVED' && summary.status !== 'DRAFT' && detail.permissions.canArchive ? (
              <Button variant="secondary" onClick={() => setStatusAction('archive')}>
                <Icon name="archive" size={16} />
                Arsipkan
              </Button>
            ) : null}
            {summary.status === 'DRAFT' && canEdit ? (
              <Button variant="secondary" loading={statusMutation.isPending} onClick={() => setStatusAction('activate')}>
                <Icon name="check" size={16} />
                Aktifkan
              </Button>
            ) : null}
            {detail.permissions.canUpload && summary.status !== 'ARCHIVED' ? (
              <Button onClick={() => setUploadOpen(true)}>
                <Icon name="upload" size={16} />
                Unggah versi
              </Button>
            ) : null}
            {detail.permissions.canCreateVersion && summary.status !== 'ARCHIVED' ? (
              <Button variant="secondary" onClick={() => setLinkOpen(true)}>
                <Icon name="link" size={16} />
                Tautan eksternal
              </Button>
            ) : null}
          </>
        }
      />

      {actionError ? (
        <div className="mb-4">
          <Alert tone="danger">{actionError}</Alert>
        </div>
      ) : null}

      {summary.status === 'ARCHIVED' ? (
        <div className="mb-4">
          <Alert tone="warning" title="Dokumen diarsipkan">
            Dokumen ini tidak muncul pada pencarian default. Pulihkan untuk mengaktifkan kembali.
          </Alert>
        </div>
      ) : null}

      <div className="grid gap-5 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="min-w-0 space-y-5">
          <Card>
            <CardHeader title="Versi aktif" />
            {detail.currentVersion ? (
              <ul className="divide-y divide-line">
                <VersionRow
                  version={detail.currentVersion}
                  document={detail}
                  busy={promoteMutation.isPending}
                  downloading={downloadingId === detail.currentVersion.id}
                  onPreview={openPreview}
                  onDownload={(version) => void downloadVersion(version)}
                  onPromote={(version) => promoteMutation.mutate(version)}
                  onOpenLink={(version) => void openLink(version)}
                />
              </ul>
            ) : (
              <EmptyState
                title="Belum ada versi"
                description="Unggah file atau catat tautan eksternal agar dokumen memiliki konten aktif."
                action={
                  detail.permissions.canUpload && summary.status !== 'ARCHIVED' ? (
                    <Button onClick={() => setUploadOpen(true)}>
                      <Icon name="upload" size={16} />
                      Unggah versi
                    </Button>
                  ) : undefined
                }
              />
            )}
          </Card>

          <Card>
            <CardHeader title="Riwayat versi" />
            {versionsQuery.isLoading ? (
              <div className="px-5 py-6">
                <LoadingBlock label="Memuat versi…" />
              </div>
            ) : versionsQuery.error ? (
              <div className="px-5 py-6">
                <ErrorState message={apiMessage(versionsQuery.error)} onRetry={() => void versionsQuery.refetch()} />
              </div>
            ) : versions.length === 0 ? (
              <EmptyState
                title="Belum ada versi"
                description="Versi akan muncul setelah file diunggah atau tautan eksternal dicatat."
              />
            ) : historyVersions.length === 0 ? (
              <p className="px-5 py-6 text-sm text-ink-secondary">
                Hanya versi aktif yang tercatat saat ini.
              </p>
            ) : (
              <ul className="divide-y divide-line">
                {historyVersions.map((version) => (
                  <VersionRow
                    key={version.id}
                    version={version}
                    document={detail}
                    busy={promoteMutation.isPending || previewLoadingId === version.id}
                    downloading={downloadingId === version.id}
                    onPreview={openPreview}
                    onDownload={(item) => void downloadVersion(item)}
                    onPromote={(item) => promoteMutation.mutate(item)}
                    onOpenLink={(item) => void openLink(item)}
                  />
                ))}
              </ul>
            )}
          </Card>

          <Card>
            <CardHeader title="Aktivitas" />
            {activityQuery.isLoading ? (
              <div className="px-5 py-6">
                <LoadingBlock label="Memuat aktivitas…" />
              </div>
            ) : activityQuery.error ? (
              <div className="px-5 py-6">
                <ErrorState message={apiMessage(activityQuery.error)} onRetry={() => void activityQuery.refetch()} />
              </div>
            ) : activity.length === 0 ? (
              <EmptyState title="Belum ada aktivitas" description="Aktivitas penting akan tercatat di sini." />
            ) : (
              <ul className="divide-y divide-line">
                {visibleActivity.map((event) => (
                  <li key={event.id} className="flex items-start gap-3 px-5 py-3">
                    <span className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-surface-muted text-ink-secondary">
                      <Icon name="document" size={14} />
                    </span>
                    <div className="min-w-0">
                      <p className="text-sm font-medium text-ink">
                        {humanizeStatus(event.actionCode)}
                        {event.outcome && event.outcome !== 'SUCCESS' ? (
                          <span className="ml-2 text-xs font-semibold text-danger">
                            {humanizeStatus(event.outcome)}
                          </span>
                        ) : null}
                      </p>
                      <p className="text-xs text-ink-secondary">
                        {event.actorName ?? 'Sistem'} · {formatDateTime(event.occurredAt)}
                      </p>
                    </div>
                  </li>
                ))}
              </ul>
            )}
            {activity.length > 8 ? (
              <div className="border-t border-line px-5 py-2.5">
                <Button
                  variant="ghost"
                  size="compact"
                  aria-expanded={activityExpanded}
                  onClick={() => setActivityExpanded((value) => !value)}
                >
                  {activityExpanded
                    ? 'Tampilkan lebih sedikit'
                    : `Tampilkan semua (${activity.length})`}
                </Button>
              </div>
            ) : null}
          </Card>
        </div>

        <div className="space-y-5">
          <Card>
            <CardHeader title="Metadata" />
            <dl className="px-5 py-3">
              <DetailRow label="Status">{humanizeStatus(summary.status)}</DetailRow>
              <DetailRow label="Workspace">
                <Link to={`/workspaces/${summary.workspaceId}`} className="text-primary hover:underline">
                  {summary.workspaceName ?? '—'}
                </Link>
              </DetailRow>
              <DetailRow label="Folder">{summary.folderName ?? '—'}</DetailRow>
              <DetailRow label="Jenis">{summary.documentTypeName ?? '—'}</DetailRow>
              <DetailRow label="Kategori">{summary.categoryName ?? '—'}</DetailRow>
              <DetailRow label="Klasifikasi">{summary.classificationName ?? '—'}</DetailRow>
              <DetailRow label="Owner">
                {summary.ownerOrganizationUnitName ?? summary.ownerUserName ?? '—'}
              </DetailRow>
              <DetailRow label="Berlaku">{formatDate(detail.effectiveDate)}</DetailRow>
              <DetailRow label="Review">{formatDate(detail.reviewDate)}</DetailRow>
              <DetailRow label="Kedaluwarsa">{formatDate(detail.expiryDate)}</DetailRow>
              <DetailRow label="Dibuat">
                {detail.createdByName ?? '—'}
                <span className="block text-xs text-ink-secondary">{formatDateTime(detail.createdAt)}</span>
              </DetailRow>
            </dl>
            {summary.tags.length ? (
              <div className="flex flex-wrap gap-1.5 border-t border-line px-5 py-3">
                {summary.tags.map((tag) => (
                  <Badge key={tag.id} tone="neutral">
                    {tag.tagName}
                  </Badge>
                ))}
              </div>
            ) : null}
          </Card>

          {detail.description ? (
            <Card>
              <CardHeader title="Deskripsi" />
              <p className="px-5 py-4 text-sm whitespace-pre-line text-ink-secondary">
                {detail.description}
              </p>
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Riwayat kepemilikan" />
            {detail.ownerHistory.length === 0 ? (
              <p className="px-5 py-4 text-sm text-ink-secondary">Belum ada riwayat kepemilikan.</p>
            ) : (
              <ul className="divide-y divide-line">
                {detail.ownerHistory.map((entry) => (
                  <li key={entry.id} className="px-5 py-3">
                    <p className="text-sm font-medium text-ink">
                      {entry.ownerOrganizationUnitName ?? entry.ownerUserName ?? '—'}
                    </p>
                    <p className="text-xs text-ink-secondary">
                      {formatDateTime(entry.validFrom)} —{' '}
                      {entry.validUntil ? formatDateTime(entry.validUntil) : 'sekarang'}
                    </p>
                    {entry.changeReason ? (
                      <p className="mt-0.5 text-xs text-ink-secondary">{entry.changeReason}</p>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>

          {detail.permissions.canManageAccess ? (
            <AccessPanel
              target={{ type: 'DOCUMENT', documentId }}
              canManage={detail.permissions.canManageAccess}
              onChanged={invalidateDocument}
            />
          ) : null}
        </div>
      </div>

      <DocumentFormDialog
        open={editOpen}
        mode="edit"
        document={detail}
        workspaces={activeWorkspaces}
        units={unitsQuery.data ?? []}
        categories={categoriesQuery.data ?? []}
        types={typesQuery.data ?? []}
        classifications={classificationsQuery.data ?? []}
        tags={tagsQuery.data ?? []}
        onClose={() => setEditOpen(false)}
        onSaved={(updated) => {
          setEditOpen(false);
          queryClient.setQueryData(['document', documentId], updated);
          invalidateDocument();
        }}
      />

      <Dialog
        open={uploadOpen}
        title="Unggah versi baru"
        description="File versi baru akan menjadi versi aktif setelah diunggah. Versi lama tetap tersimpan."
        size="md"
        onClose={() => {
          if (uploadMutation.isPending) {
            return;
          }
          setUploadOpen(false);
          setUploadError(null);
        }}
        footer={
          <>
            <Button
              variant="secondary"
              disabled={uploadMutation.isPending}
              onClick={() => {
                setUploadOpen(false);
                setUploadError(null);
              }}
            >
              Batal
            </Button>
            <Button loading={uploadMutation.isPending} onClick={submitUpload}>
              Unggah
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {uploadError ? <Alert tone="danger">{uploadError}</Alert> : null}
          {policy ? (
            <Alert tone="info" title="Kebijakan file">
              Ekstensi diizinkan: {policy.allowedExtensions.join(', ')}. Ukuran maksimum{' '}
              {(policy.maxFileSizeBytes / (1024 * 1024)).toFixed(0)} MB. Validasi tipe file dasar
              diterapkan; pemindaian malware tidak termasuk pada pilot.
            </Alert>
          ) : null}
          <div className="flex flex-col gap-1.5">
            <label htmlFor="upload-file" className="text-xs font-semibold text-ink">
              File <span className="text-danger">*</span>
            </label>
            <input
              id="upload-file"
              ref={fileInputRef}
              type="file"
              accept={policy ? policy.allowedExtensions.map((extension) => `.${extension}`).join(',') : undefined}
              onChange={(event) => setUploadFile(event.target.files?.[0] ?? null)}
              className="w-full rounded-lg border border-line-strong bg-surface px-3 py-2 text-sm text-ink file:mr-3 file:rounded-md file:border-0 file:bg-primary file:px-3 file:py-1.5 file:text-xs file:font-semibold file:text-white hover:file:bg-primary-hover focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none"
            />
            {uploadFile ? (
              <p className="text-xs text-ink-secondary">
                {uploadFile.name} · {formatBytes(uploadFile.size)}
              </p>
            ) : null}
          </div>
          <InputField
            label="Label versi"
            placeholder="Contoh: 1.1"
            value={uploadLabel}
            onChange={(event) => setUploadLabel(event.target.value)}
            maxLength={60}
          />
          <TextareaField
            label="Catatan perubahan"
            value={uploadSummary}
            onChange={(event) => setUploadSummary(event.target.value)}
            maxLength={1000}
          />
          {uploadProgress !== null ? (
            <div>
              <div className="mb-1 flex items-center justify-between text-xs text-ink-secondary">
                <span>Mengunggah…</span>
                <span>{uploadProgress}%</span>
              </div>
              <div className="h-2 overflow-hidden rounded-full bg-surface-muted">
                <div
                  className="h-full rounded-full bg-primary transition-[width]"
                  style={{ width: `${uploadProgress}%` }}
                  role="progressbar"
                  aria-valuenow={uploadProgress}
                  aria-valuemin={0}
                  aria-valuemax={100}
                />
              </div>
            </div>
          ) : null}
        </div>
      </Dialog>

      <Dialog
        open={linkOpen}
        title="Catat tautan eksternal"
        description="KCIC Connect hanya menyimpan tautan dan metadata. Izin pada penyedia eksternal tidak diubah."
        size="md"
        onClose={() => {
          if (linkMutation.isPending) {
            return;
          }
          setLinkOpen(false);
          setLinkError(null);
        }}
        footer={
          <>
            <Button
              variant="secondary"
              disabled={linkMutation.isPending}
              onClick={() => {
                setLinkOpen(false);
                setLinkError(null);
              }}
            >
              Batal
            </Button>
            <Button loading={linkMutation.isPending} onClick={submitLink}>
              Simpan tautan
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          {linkError ? <Alert tone="danger">{linkError}</Alert> : null}
          <Alert tone="warning" title="Batas integrasi Office">
            Tautan hanya dicatat sebagai sumber konten. KCIC Connect tidak menyinkronkan permission
            Office/SharePoint; pastikan penerima telah memiliki akses pada penyedia tersebut.
          </Alert>
          <InputField
            label="URL"
            required
            placeholder="https://..."
            value={linkUrl}
            onChange={(event) => setLinkUrl(event.target.value)}
            maxLength={2000}
          />
          <InputField
            label="Judul tautan"
            value={linkTitle}
            onChange={(event) => setLinkTitle(event.target.value)}
            maxLength={240}
          />
          <InputField
            label="Label versi"
            value={linkLabel}
            onChange={(event) => setLinkLabel(event.target.value)}
            maxLength={60}
          />
          <TextareaField
            label="Catatan perubahan"
            value={linkSummary}
            onChange={(event) => setLinkSummary(event.target.value)}
            maxLength={1000}
          />
        </div>
      </Dialog>

      <Dialog
        open={preview !== null}
        title={preview?.filename ?? 'Preview dokumen'}
        description="Preview hanya tersedia selama sesi dan izin Anda masih berlaku."
        size="lg"
        onClose={closePreview}
        footer={
          <Button variant="secondary" onClick={closePreview}>
            Tutup
          </Button>
        }
      >
        {preview?.contentType?.startsWith('image/') ? (
          <img src={preview.url} alt={preview.filename ?? 'Preview dokumen'} className="mx-auto max-h-[60vh]" />
        ) : preview?.contentType === 'application/pdf' ? (
          <iframe
            src={preview.url}
            title={preview.filename ?? 'Preview PDF'}
            className="h-[60vh] w-full rounded-lg border border-line"
          />
        ) : (
          <iframe
            src={preview?.url}
            title={preview?.filename ?? 'Preview dokumen'}
            className="h-[60vh] w-full rounded-lg border border-line bg-surface"
          />
        )}
      </Dialog>

      <ConfirmDialog
        open={statusAction !== null}
        tone={statusAction === 'archive' ? 'danger' : 'primary'}
        title={
          statusAction === 'archive'
            ? 'Arsipkan dokumen?'
            : statusAction === 'restore'
              ? 'Pulihkan dokumen?'
              : 'Aktifkan dokumen?'
        }
        message={
          statusAction === 'archive'
            ? 'Dokumen tidak lagi muncul pada pencarian default. Riwayat versi tetap tersimpan.'
            : statusAction === 'restore'
              ? 'Dokumen akan kembali aktif dan dapat diakses sesuai izin yang berlaku.'
              : 'Dokumen draf akan menjadi aktif dan dapat ditemukan pengguna yang berhak.'
        }
        confirmLabel={
          statusAction === 'archive'
            ? 'Arsipkan'
            : statusAction === 'restore'
              ? 'Pulihkan'
              : 'Aktifkan'
        }
        loading={statusMutation.isPending}
        onConfirm={() => {
          if (statusAction) {
            statusMutation.mutate(statusAction);
          }
        }}
        onClose={() => setStatusAction(null)}
      />
    </>
  );
}
