import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Button, ButtonLink } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/Dialog';
import {
  Alert,
  EmptyState,
  ErrorState,
  ForbiddenState,
  LoadingBlock,
} from '../../shared/components/Feedback';
import { Icon } from '../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { formatDate, formatDateTime } from '../../shared/utils/format';
import { fetchVersionContent, saveBlob } from '../documents/api';
import {
  type TemplateDetail,
  type TemplateVersion,
  archiveTemplate,
  getTemplate,
  publishTemplate,
  recordTemplateUsage,
  templateStatusLabels,
} from './api';
import { EditTemplateDialog } from './EditTemplateDialog';

function versionLabel(label: string | null, number: number): string {
  return label ? `${label} (versi ${number})` : `Versi ${number}`;
}

export function TemplateDetailPage() {
  const { templateId = '' } = useParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const [publishOpen, setPublishOpen] = useState(false);
  const [archiveOpen, setArchiveOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [historicalVersion, setHistoricalVersion] = useState<TemplateVersion | null>(null);

  const query = useQuery({
    queryKey: ['templates', 'detail', templateId],
    queryFn: () => getTemplate(templateId),
    enabled: Boolean(templateId),
  });

  function applyDetail(detail: TemplateDetail) {
    queryClient.setQueryData(['templates', 'detail', templateId], detail);
    void queryClient.invalidateQueries({ queryKey: ['templates'] });
  }

  const publish = useMutation({
    mutationFn: () => publishTemplate(templateId),
    onSuccess: (detail) => {
      applyDetail(detail);
      setPublishOpen(false);
      notify('Template diterbitkan pada versi aktif dokumen sumber.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const archive = useMutation({
    mutationFn: () => archiveTemplate(templateId),
    onSuccess: (detail) => {
      applyDetail(detail);
      setArchiveOpen(false);
      notify('Template diarsipkan. Histori dan referensi versi tetap tersimpan.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const openDocument = useMutation({
    mutationFn: async (source: TemplateDetail['summary']) => {
      await recordTemplateUsage(source.id, 'VIEW');
      return source.documentId;
    },
    onSuccess: (documentId) => {
      void queryClient.invalidateQueries({ queryKey: ['templates', 'detail', templateId] });
      navigate(`/documents/${documentId}`);
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const download = useMutation({
    mutationFn: async (detail: TemplateDetail) => {
      const versionId = detail.summary.currentPublishedVersionId;
      if (!versionId) {
        throw new Error('Template belum memiliki versi terbit.');
      }
      await recordTemplateUsage(detail.summary.id, 'DOWNLOAD');
      const content = await fetchVersionContent(detail.summary.documentId, versionId, true);
      saveBlob(content.blob, content.filename);
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['templates', 'detail', templateId] });
      notify('Unduhan template dimulai.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const downloadHistorical = useMutation({
    mutationFn: async (version: TemplateVersion) => {
      const current = query.data;
      if (!current) {
        throw new Error('Detail template belum tersedia.');
      }
      const content = await fetchVersionContent(current.summary.documentId, version.id, true);
      saveBlob(content.blob, content.filename);
    },
    onSuccess: () => {
      setHistoricalVersion(null);
      notify('Unduhan versi lama dimulai. Versi ini bukan acuan template saat ini.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;
  const notFound = error instanceof ApiError && error.status === 404;

  if (query.isLoading) {
    return <LoadingBlock label="Memuat detail template…" />;
  }

  if (notFound) {
    return (
      <Card>
        <EmptyState
          title="Template tidak ditemukan"
          description="Template tidak tersedia untuk akun Anda atau tautan tidak lagi valid."
          action={
            <ButtonLink to="/templates">Kembali ke katalog template</ButtonLink>
          }
        />
      </Card>
    );
  }

  if (forbidden) {
    return (
      <Card>
        <ForbiddenState message="Akun Anda tidak memiliki akses ke template ini." />
      </Card>
    );
  }

  if (error || !query.data) {
    return <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />;
  }

  const detail = query.data;
  const summary = detail.summary;
  const permissions = detail.permissions;
  const published = summary.status === 'PUBLISHED';
  const archived = summary.status === 'ARCHIVED';
  const latestVersion = detail.versionHistory[0] ?? null;
  const hasNewerDocumentVersion =
    published &&
    latestVersion !== null &&
    latestVersion.id !== summary.currentPublishedVersionId &&
    latestVersion.status === 'CURRENT';
  const usageBusy = openDocument.isPending || download.isPending;

  return (
    <>
      <div className="mb-4">
        <Link
          to="/templates"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
        >
          <Icon name="arrow-left" size={14} />
          Kembali ke katalog template
        </Link>
      </div>

      <PageHeader
        title={summary.templateName}
        description={`${summary.templateCode} · ${summary.documentTitle ?? 'Dokumen sumber'}`}
        meta={
          <>
            <StatusBadge
              status={summary.status}
              label={templateStatusLabels[summary.status]}
            />
            {published ? (
              <Badge tone="gold">
                {summary.currentPublishedVersionLabel ??
                  `Versi ${summary.currentPublishedVersionNumber}`}
              </Badge>
            ) : null}
            {published && summary.publishedAt ? (
              <span className="text-xs text-ink-secondary">
                Dipublikasikan {formatDateTime(summary.publishedAt)}
                {summary.publishedByName ? ` oleh ${summary.publishedByName}` : ''}
              </span>
            ) : null}
          </>
        }
        action={
          <>
            {permissions.canEdit ? (
              <Button variant="secondary" onClick={() => setEditOpen(true)}>
                <Icon name="edit" size={16} />
                Ubah metadata
              </Button>
            ) : null}
            {permissions.canPublish && !archived ? (
              <Button variant="secondary" onClick={() => setPublishOpen(true)}>
                <Icon name="upload" size={16} />
                {published ? 'Terbitkan ulang' : 'Terbitkan'}
              </Button>
            ) : null}
            {permissions.canArchive && !archived ? (
              <Button variant="danger" onClick={() => setArchiveOpen(true)}>
                <Icon name="archive" size={16} />
                Arsipkan
              </Button>
            ) : null}
          </>
        }
      />

      {hasNewerDocumentVersion ? (
        <div className="mb-5">
          <Alert tone="info" title="Dokumen sumber memiliki versi aktif yang lebih baru">
            Template masih menunjuk versi {summary.currentPublishedVersionNumber}. Tinjau versi{' '}
            {latestVersion?.versionNumber}, lalu terbitkan ulang bila versi baru layak dijadikan
            acuan.
          </Alert>
        </div>
      ) : null}

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-5">
          <Card>
            <CardHeader title="Informasi template" />
            <div className="px-5 py-2">
              <dl>
                <DetailRow label="Kode">{summary.templateCode}</DetailRow>
                <DetailRow label="Dokumen sumber">
                  {permissions.canViewDocument ? (
                    <Link
                      to={`/documents/${summary.documentId}`}
                      className="font-semibold text-primary hover:underline"
                    >
                      {summary.documentTitle ?? 'Dokumen'}
                    </Link>
                  ) : (
                    (summary.documentTitle ?? 'Dokumen')
                  )}
                </DetailRow>
                <DetailRow label="Kode dokumen">{summary.documentCode ?? '—'}</DetailRow>
                <DetailRow label="Kategori">{summary.categoryName ?? '—'}</DetailRow>
                <DetailRow label="Pemilik dokumen">
                  {summary.ownerUserName ?? summary.ownerOrganizationUnitName ?? '—'}
                </DetailRow>
                <DetailRow label="Unit pemilik">
                  {summary.ownerOrganizationUnitName ?? '—'}
                </DetailRow>
                <DetailRow label="Status dokumen">
                  <StatusBadge status={summary.documentStatus} />
                </DetailRow>
                <DetailRow label="Versi terbit">
                  {summary.currentPublishedVersionNumber === null
                    ? 'Belum diterbitkan'
                    : versionLabel(
                        summary.currentPublishedVersionLabel,
                        summary.currentPublishedVersionNumber,
                      )}
                </DetailRow>
                <DetailRow label="Berlaku efektif">{formatDate(detail.effectiveDate)}</DetailRow>
                <DetailRow label="Tinjau berikutnya">{formatDate(detail.reviewDate)}</DetailRow>
                <DetailRow label="Kedaluwarsa">{formatDate(detail.expiryDate)}</DetailRow>
                <DetailRow label="Diperbarui">{formatDateTime(summary.updatedAt)}</DetailRow>
              </dl>
            </div>
          </Card>

          {published ? (
            <Card>
              <CardHeader
                title="Gunakan template"
                description="Akses baca dan unduh mengikuti permission dokumen sumber."
              />
              <div className="flex flex-col gap-3 px-5 py-4">
                <p className="text-sm text-ink-secondary">
                  Template memakai dokumen dan versioning repository, sehingga versi yang Anda buka
                  adalah versi terbit yang sama dengan katalog dokumen.
                </p>
                <div className="flex flex-wrap gap-2">
                  <Button
                    onClick={() => openDocument.mutate(summary)}
                    loading={openDocument.isPending}
                    disabled={usageBusy || !permissions.canViewDocument}
                    title={
                      permissions.canViewDocument
                        ? undefined
                        : 'Anda tidak memiliki izin melihat dokumen sumber.'
                    }
                  >
                    <Icon name="document" size={16} />
                    Buka dokumen
                  </Button>
                  <Button
                    variant="secondary"
                    onClick={() => download.mutate(detail)}
                    loading={download.isPending}
                    disabled={
                      usageBusy ||
                      !permissions.canDownloadDocument ||
                      !summary.currentPublishedVersionId
                    }
                    title={
                      permissions.canDownloadDocument
                        ? undefined
                        : 'Anda tidak memiliki izin mengunduh dokumen sumber.'
                    }
                  >
                    <Icon name="download" size={16} />
                    Unduh versi terbit
                  </Button>
                </div>
                {!permissions.canViewDocument ? (
                  <p className="text-xs text-ink-secondary">
                    Akun Anda dapat melihat metadata template, tetapi tidak memiliki akses isi
                    dokumen sumber. Ajukan permintaan akses melalui menu Requests bila diperlukan.
                  </p>
                ) : null}
              </div>
            </Card>
          ) : null}

          {summary.description ? (
            <Card>
              <CardHeader
                title="Deskripsi template"
                description="Penjelasan kegunaan template dan unit yang disarankan memakainya."
              />
              <p className="px-5 py-4 text-sm whitespace-pre-wrap text-ink">
                {summary.description}
              </p>
            </Card>
          ) : null}

          {detail.documentDescription ? (
            <Card>
              <CardHeader title="Deskripsi dokumen sumber" />
              <p className="px-5 py-4 text-sm whitespace-pre-wrap text-ink">
                {detail.documentDescription}
              </p>
            </Card>
          ) : null}

          <Card>
            <CardHeader
              title="Riwayat versi dokumen"
              description="Versi lama tetap dapat ditelusuri sesuai permission dokumen."
            />
            {!detail.versionHistoryVisible ? (
              <p className="px-5 py-4 text-sm text-ink-secondary">
                Riwayat versi tidak ditampilkan karena akun Anda tidak memiliki akses ke dokumen
                sumber.
              </p>
            ) : detail.versionHistory.length === 0 ? (
              <p className="px-5 py-4 text-sm text-ink-secondary">
                Dokumen sumber belum memiliki versi.
              </p>
            ) : (
              <ul className="divide-y divide-line">
                {detail.versionHistory.map((version) => (
                  <li key={version.id} className="px-5 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <div className="flex flex-wrap items-center gap-2">
                        <p className="text-sm font-semibold text-ink">
                          {versionLabel(version.versionLabel, version.versionNumber)}
                        </p>
                        <StatusBadge status={version.status} />
                        {version.currentlyPublished ? (
                          <Badge tone="gold">Versi terbit template</Badge>
                        ) : null}
                      </div>
                      <div className="flex flex-wrap items-center gap-3">
                        <span className="text-xs text-ink-secondary">
                          {formatDateTime(version.publishedAt)}
                        </span>
                        {!version.currentlyPublished ? (
                          <Button
                            variant="secondary"
                            size="compact"
                            onClick={() => setHistoricalVersion(version)}
                            disabled={downloadHistorical.isPending || !permissions.canDownloadDocument}
                            title={
                              permissions.canDownloadDocument
                                ? 'Unduh versi lama dengan peringatan'
                                : 'Anda tidak memiliki izin mengunduh dokumen sumber.'
                            }
                          >
                            <Icon name="download" size={14} />
                            Unduh versi lama
                          </Button>
                        ) : null}
                      </div>
                    </div>
                    {!version.currentlyPublished && version.status === 'SUPERSEDED' ? (
                      <p className="mt-1 text-xs text-warning">
                        Versi lama — bukan lagi acuan template saat ini.
                      </p>
                    ) : null}
                    {version.changeSummary ? (
                      <p className="mt-1 text-xs whitespace-pre-wrap text-ink-secondary">
                        {version.changeSummary}
                      </p>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>

        <div className="space-y-5">
          <Card>
            <CardHeader title="Status template" />
            <div className="space-y-3 px-5 py-4 text-sm text-ink-secondary">
              <StatusBadge
                status={summary.status}
                label={templateStatusLabels[summary.status]}
              />
              <p>
                {archived
                  ? 'Template diarsipkan dan tidak tampil pada katalog umum. Referensi versi terakhir tetap tersimpan untuk histori.'
                  : published
                    ? 'Template tampil pada katalog umum dan menunjuk satu versi dokumen sumber.'
                    : 'Template masih draft. Publikasikan agar muncul pada katalog umum.'}
              </p>
              {detail.currentVersionChangeSummary ? (
                <p className="text-xs">
                  Catatan versi terbit: {detail.currentVersionChangeSummary}
                </p>
              ) : null}
            </div>
          </Card>

          <Card>
            <CardHeader title="Penggunaan template" />
            <div className="px-5 py-2">
              <dl>
                <DetailRow label="Total penggunaan">{detail.usageCount}</DetailRow>
                <DetailRow label="Dibuka">{detail.viewCount}</DetailRow>
                <DetailRow label="Diunduh">{detail.downloadCount}</DetailRow>
                <DetailRow label="Disalin">{detail.copyCount}</DetailRow>
              </dl>
            </div>
            <p className="px-5 py-3 text-xs text-ink-secondary">
              Penggunaan dicatat per versi terbit untuk analisis adopsi. Catatan ini tidak
              memberikan atau mencabut akses dokumen.
            </p>
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={publishOpen}
        title={published ? 'Terbitkan ulang template' : 'Terbitkan template'}
        message={
          <span>
            Template akan menunjuk versi aktif dokumen sumber
            {latestVersion?.status === 'CURRENT'
              ? ` (versi ${latestVersion.versionNumber})`
              : ''}
            . Publikasi dan versi yang diterbitkan tercatat pada audit trail.
          </span>
        }
        confirmLabel={published ? 'Terbitkan ulang' : 'Terbitkan'}
        tone="primary"
        loading={publish.isPending}
        onClose={() => setPublishOpen(false)}
        onConfirm={() => publish.mutate()}
      />

      <ConfirmDialog
        open={archiveOpen}
        title="Arsipkan template"
        message="Template tidak lagi tampil pada katalog umum. Histori publikasi dan referensi versi tetap disimpan. Tindakan ini tercatat pada audit trail."
        confirmLabel="Arsipkan"
        loading={archive.isPending}
        onClose={() => setArchiveOpen(false)}
        onConfirm={() => archive.mutate()}
      />

      <ConfirmDialog
        open={historicalVersion !== null}
        title="Versi ini bukan acuan template saat ini"
        message={
          <span>
            Anda akan mengunduh versi {historicalVersion?.versionNumber} dari dokumen sumber.
            Template saat ini menunjuk{' '}
            {summary.currentPublishedVersionNumber === null
              ? 'versi yang belum diterbitkan'
              : `versi ${summary.currentPublishedVersionNumber}`}
            . File versi lama dapat berbeda dari ketentuan terbaru, jadi jangan dipakai sebagai
            acuan resmi tanpa verifikasi.
          </span>
        }
        confirmLabel="Unduh versi lama"
        tone="primary"
        loading={downloadHistorical.isPending}
        onClose={() => {
          if (!downloadHistorical.isPending) {
            setHistoricalVersion(null);
          }
        }}
        onConfirm={() => {
          if (historicalVersion) {
            downloadHistorical.mutate(historicalVersion);
          }
        }}
      />

      {editOpen ? (
        <EditTemplateDialog
          detail={detail}
          onClose={() => setEditOpen(false)}
          onUpdated={(updated) => {
            applyDetail(updated);
            setEditOpen(false);
          }}
        />
      ) : null}
    </>
  );
}
