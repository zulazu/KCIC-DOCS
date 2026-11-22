import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import type { BadgeTone } from '../../shared/components/Badge';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Button, ButtonLink } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/Dialog';
import {
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
import { useAuth } from '../auth/AuthContext';
import {
  type DistributionAccessLevel,
  type DistributionDetail,
  type DistributionRecipient,
  accessLevelLabels,
  distributionEventLabels,
  getDistribution,
  recipientStatusLabels,
  revokeDistribution,
  revokeDistributionRecipient,
} from './api';

const ACCESS_TONES: Record<DistributionAccessLevel, BadgeTone> = {
  VIEW: 'neutral',
  PREVIEW: 'info',
  DOWNLOAD: 'gold',
};

function versionLabel(item: DistributionDetail['items'][number]): string {
  if (item.versionLabel) {
    return item.versionLabel;
  }
  return item.versionNumber === null ? 'Tanpa versi' : `Versi ${item.versionNumber}`;
}

export function DistributionDetailPage() {
  const { distributionId = '' } = useParams();
  const { me } = useAuth();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [revokeAllOpen, setRevokeAllOpen] = useState(false);
  const [recipientToRevoke, setRecipientToRevoke] = useState<DistributionRecipient | null>(null);

  const query = useQuery({
    queryKey: ['distributions', 'detail', distributionId],
    queryFn: () => getDistribution(distributionId),
    enabled: Boolean(distributionId),
  });

  function applyDetail(detail: DistributionDetail) {
    queryClient.setQueryData(['distributions', 'detail', distributionId], detail);
    void queryClient.invalidateQueries({ queryKey: ['distributions'] });
  }

  const revokeAll = useMutation({
    mutationFn: () => revokeDistribution(distributionId),
    onSuccess: (detail) => {
      applyDetail(detail);
      setRevokeAllOpen(false);
      notify('Distribusi dicabut. Seluruh akses penerima dari distribusi ini telah dihentikan.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const revokeRecipient = useMutation({
    mutationFn: (recipientId: string) => revokeDistributionRecipient(distributionId, recipientId),
    onSuccess: (detail) => {
      applyDetail(detail);
      notify('Akses penerima dicabut.');
      setRecipientToRevoke(null);
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;
  const notFound = error instanceof ApiError && error.status === 404;

  if (query.isLoading) {
    return <LoadingBlock label="Memuat detail distribusi…" />;
  }

  if (notFound) {
    return (
      <Card>
        <EmptyState
          title="Distribusi tidak ditemukan"
          description="Distribusi tidak tersedia untuk akun Anda atau tautan tidak lagi valid."
          action={
            <ButtonLink to="/distributions">Kembali ke daftar distribusi</ButtonLink>
          }
        />
      </Card>
    );
  }

  if (forbidden) {
    return (
      <Card>
        <ForbiddenState message="Akun Anda tidak memiliki akses ke distribusi ini." />
      </Card>
    );
  }

  if (error || !query.data) {
    return <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />;
  }

  const detail = query.data;
  const { summary } = detail;
  const capabilities = me?.capabilities ?? [];
  const isSender = me?.userId === summary.senderUserId;
  const canModerate = isSender || capabilities.includes('distribution.revoke');
  const active = summary.status === 'SENT' || summary.status === 'PARTIALLY_REVOKED';
  const canRevokeAll = active && canModerate;

  return (
    <>
      <div className="mb-4">
        <Link
          to="/distributions"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
        >
          <Icon name="arrow-left" size={14} />
          Kembali ke daftar distribusi
        </Link>
      </div>

      <PageHeader
        title={summary.title}
        description={`${summary.distributionCode} · Dari ${summary.senderName ?? '—'}`}
        meta={
          <>
            <StatusBadge status={summary.status} />
            {!isSender && summary.recipientStatus ? (
              <Badge tone={summary.recipientStatus === 'READ' ? 'success' : 'neutral'}>
                Status Anda: {recipientStatusLabels[summary.recipientStatus]}
              </Badge>
            ) : null}
            <span className="text-xs text-ink-secondary">
              Dikirim {formatDateTime(summary.sentAt)}
            </span>
          </>
        }
        action={
          canRevokeAll ? (
            <Button variant="danger" onClick={() => setRevokeAllOpen(true)}>
              <Icon name="lock" size={16} />
              Cabut distribusi
            </Button>
          ) : undefined
        }
      />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_380px]">
        <div className="space-y-5">
          <Card>
            <CardHeader title="Ringkasan distribusi" />
            <div className="px-5 py-2">
              <dl>
                <DetailRow label="Kode">{summary.distributionCode}</DetailRow>
                <DetailRow label="Pengirim">{summary.senderName ?? '—'}</DetailRow>
                <DetailRow label="Dikirim">{formatDateTime(summary.sentAt)}</DetailRow>
                <DetailRow label="Berlaku sampai">
                  {summary.expiresAt ? formatDate(summary.expiresAt) : 'Tanpa kedaluwarsa'}
                </DetailRow>
                <DetailRow label="Dokumen">{summary.itemCount} dokumen</DetailRow>
                <DetailRow label="Penerima">
                  {summary.recipientCount} penerima
                  {canModerate ? ` · ${summary.readCount} sudah membaca` : ''}
                </DetailRow>
              </dl>
            </div>
          </Card>

          {detail.message ? (
            <Card>
              <CardHeader title="Pesan" />
              <p className="px-5 py-4 text-sm whitespace-pre-wrap text-ink">{detail.message}</p>
            </Card>
          ) : null}

          <Card>
            <CardHeader
              title="Dokumen"
              description="Akses yang diberikan mengikuti tingkat akses tiap dokumen."
            />
            <ul className="divide-y divide-line">
              {detail.items.map((item) => (
                <li key={item.documentId} className="flex flex-wrap items-center gap-3 px-5 py-3">
                  <span className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-primary/8 text-primary">
                    <Icon name="document" size={17} />
                  </span>
                  <div className="min-w-0 flex-1">
                    {item.canOpen ? (
                      <Link
                        to={`/documents/${item.documentId}`}
                        className="truncate text-sm font-semibold text-primary hover:underline"
                      >
                        {item.documentTitle ?? 'Dokumen'}
                      </Link>
                    ) : (
                      <p className="truncate text-sm font-semibold text-ink">
                        {item.documentTitle ?? 'Dokumen'}
                      </p>
                    )}
                    <p className="mt-0.5 text-xs text-ink-secondary">
                      {item.documentCode ?? 'Tanpa kode'} · {versionLabel(item)}
                      {item.canOpen ? '' : ' · Akses dokumen sudah tidak aktif'}
                    </p>
                  </div>
                  <Badge tone={ACCESS_TONES[item.accessLevel]}>
                    {accessLevelLabels[item.accessLevel]}
                  </Badge>
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <CardHeader
              title="Riwayat distribusi"
              description="Urutan kronologis pengiriman, pembacaan, pencabutan, dan kedaluwarsa."
            />
            {detail.events.length === 0 ? (
              <p className="px-5 py-4 text-xs text-ink-secondary">Belum ada aktivitas tercatat.</p>
            ) : (
              <ol className="space-y-0 px-5 py-2">
                {detail.events.map((event, index) => (
                  <li key={event.id} className="relative flex gap-3 py-3">
                    <span className="relative flex w-5 shrink-0 justify-center">
                      {index < detail.events.length - 1 ? (
                        <span
                          className="absolute top-4 bottom-[-12px] w-px bg-line"
                          aria-hidden="true"
                        />
                      ) : null}
                      <span
                        className="mt-1 size-2.5 rounded-full border-2 border-primary bg-surface"
                        aria-hidden="true"
                      />
                    </span>
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="text-sm font-semibold text-ink">
                          {distributionEventLabels[event.eventType]}
                        </span>
                        {event.recipientName ? (
                          <span className="text-xs text-ink-secondary">{event.recipientName}</span>
                        ) : null}
                      </div>
                      <p className="mt-0.5 text-xs text-ink-secondary">
                        {event.actorName ?? 'Sistem'} · {formatDateTime(event.occurredAt)}
                      </p>
                      {event.note ? (
                        <p className="mt-1 rounded-md bg-surface-muted px-2.5 py-1.5 text-xs whitespace-pre-wrap text-ink">
                          {event.note}
                        </p>
                      ) : null}
                    </div>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>

        <div className="space-y-5">
          {canModerate ? (
            <Card>
              <CardHeader
                title="Penerima"
                description="Cabut akses penerima tertentu tanpa memengaruhi penerima lain."
              />
              <ul className="divide-y divide-line">
                {detail.recipients.map((recipient) => {
                  const recipientActive = recipient.status === 'SENT' || recipient.status === 'READ';
                  const canRevokeRecipient = active && recipientActive;
                  return (
                    <li key={recipient.id} className="px-5 py-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-semibold text-ink">
                          {recipient.userName ?? 'Penerima'}
                        </p>
                        <StatusBadge
                          status={recipient.status}
                          label={recipientStatusLabels[recipient.status]}
                        />
                      </div>
                      <p className="mt-1 text-xs text-ink-secondary">
                        Dikirim {formatDateTime(recipient.sentAt)}
                        {recipient.readAt ? ` · Dibaca ${formatDateTime(recipient.readAt)}` : ''}
                        {recipient.revokedAt ? ` · Dicabut ${formatDateTime(recipient.revokedAt)}` : ''}
                      </p>
                      {canRevokeRecipient ? (
                        <Button
                          variant="ghost"
                          size="compact"
                          className="mt-2 text-danger hover:bg-danger-bg"
                          onClick={() => setRecipientToRevoke(recipient)}
                        >
                          Cabut akses penerima
                        </Button>
                      ) : null}
                    </li>
                  );
                })}
              </ul>
            </Card>
          ) : (
            <Card>
              <CardHeader title="Akses Anda" />
              <div className="space-y-3 px-5 py-4 text-sm text-ink-secondary">
                {summary.recipientStatus === 'REVOKED' ? (
                  <>
                    <p>Akses Anda melalui distribusi ini sudah dicabut oleh pengirim.</p>
                    <p className="text-xs">
                      Hubungi pengirim bila Anda masih memerlukan akses ke dokumen ini.
                    </p>
                  </>
                ) : summary.recipientStatus === 'EXPIRED' ? (
                  <>
                    <p>Akses Anda melalui distribusi ini sudah kedaluwarsa.</p>
                    <p className="text-xs">
                      Ajukan permintaan akses baru melalui menu Requests bila masih diperlukan.
                    </p>
                  </>
                ) : (
                  <>
                    <p>
                      Distribusi ini memberi Anda akses ke {summary.itemCount} dokumen sesuai tingkat
                      akses yang tercantum. Akses dapat dicabut pengirim kapan saja.
                    </p>
                    <p className="text-xs">
                      {summary.expiresAt
                        ? `Akses berakhir pada ${formatDate(summary.expiresAt)} kecuali dicabut lebih awal.`
                        : 'Akses berlaku sampai dicabut oleh pengirim.'}
                    </p>
                  </>
                )}
              </div>
            </Card>
          )}

          {canModerate && !active ? (
            <Card>
              <CardHeader title="Status akhir" />
              <p className="px-5 py-4 text-sm text-ink-secondary">
                Distribusi sudah tidak aktif. Akses yang pernah diberikan melalui distribusi ini telah
                dihentikan atau kedaluwarsa sesuai kebijakan.
              </p>
            </Card>
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={revokeAllOpen}
        title="Cabut seluruh distribusi"
        message="Semua penerima yang masih aktif akan kehilangan akses dari distribusi ini. Tindakan ini tercatat pada audit trail."
        confirmLabel="Cabut distribusi"
        loading={revokeAll.isPending}
        onClose={() => setRevokeAllOpen(false)}
        onConfirm={() => revokeAll.mutate()}
      />

      <ConfirmDialog
        open={recipientToRevoke !== null}
        title="Cabut akses penerima"
        message={
          recipientToRevoke
            ? `Akses ${recipientToRevoke.userName ?? 'penerima ini'} dari distribusi ini akan dihentikan. Grant lain di luar distribusi tidak terpengaruh.`
            : ''
        }
        confirmLabel="Cabut akses"
        loading={revokeRecipient.isPending}
        onClose={() => setRecipientToRevoke(null)}
        onConfirm={() => {
          if (recipientToRevoke) {
            revokeRecipient.mutate(recipientToRevoke.id);
          }
        }}
      />
    </>
  );
}
