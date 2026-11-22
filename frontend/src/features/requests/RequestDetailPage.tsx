import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../shared/api/client';
import type { RequestDetail, RequestStatus } from '../../shared/api/types';
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
import { formatDate, formatDateTime, humanizeStatus } from '../../shared/utils/format';
import {
  approveRequest,
  cancelRequest,
  closeRequest,
  getRequest,
  rejectRequest,
  startReview,
} from './api';
import { DecisionDialog } from './DecisionDialog';
import { FulfillDialog } from './FulfillDialog';
import { FULFILLMENT_TYPE_LABELS, requestTypeLabel } from './labels';

const FULFILLABLE: RequestStatus[] = ['APPROVED', 'IN_PROGRESS'];

type DecisionAction = 'approve' | 'reject' | 'cancel' | 'close';

const DECISION_COPY: Record<DecisionAction, { title: string; label: string; tone: 'primary' | 'danger'; requireComment: boolean }> = {
  approve: { title: 'Setujui request', label: 'Setujui', tone: 'primary', requireComment: false },
  reject: { title: 'Tolak request', label: 'Tolak request', tone: 'danger', requireComment: true },
  cancel: { title: 'Batalkan request', label: 'Batalkan', tone: 'danger', requireComment: false },
  close: { title: 'Tutup request', label: 'Tutup request', tone: 'primary', requireComment: false },
};

export function RequestDetailPage() {
  const { requestId = '' } = useParams();
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [decision, setDecision] = useState<DecisionAction | null>(null);
  const [reviewOpen, setReviewOpen] = useState(false);
  const [fulfillOpen, setFulfillOpen] = useState(false);

  const query = useQuery({
    queryKey: ['requests', 'detail', requestId],
    queryFn: () => getRequest(requestId),
    enabled: Boolean(requestId),
  });

  function applyDetail(detail: RequestDetail) {
    queryClient.setQueryData(['requests', 'detail', requestId], detail);
    void queryClient.invalidateQueries({ queryKey: ['requests'] });
    void queryClient.invalidateQueries({ queryKey: ['notifications', 'unread'] });
  }

  const startReviewMutation = useMutation({
    mutationFn: () => startReview(requestId),
    onSuccess: (detail) => {
      applyDetail(detail);
      setReviewOpen(false);
      notify('Request masuk ke tahap review.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const decisionMutation = useMutation({
    mutationFn: ({ action, comment }: { action: DecisionAction; comment: string }) => {
      switch (action) {
        case 'approve':
          return approveRequest(requestId, comment || undefined);
        case 'reject':
          return rejectRequest(requestId, comment);
        case 'cancel':
          return cancelRequest(requestId, comment || undefined);
        case 'close':
          return closeRequest(requestId, comment || undefined);
      }
    },
    onSuccess: (detail) => {
      applyDetail(detail);
      notify('Keputusan request berhasil disimpan.');
      setDecision(null);
    },
  });

  const error = query.error;
  const forbidden = error instanceof ApiError && error.status === 403;
  const notFound = error instanceof ApiError && error.status === 404;

  if (query.isLoading) {
    return <LoadingBlock label="Memuat detail request…" />;
  }

  if (notFound) {
    return (
      <Card>
        <EmptyState
          title="Request tidak ditemukan"
          description="Request mungkin telah dihapus atau tautan tidak lagi valid."
          action={
            <ButtonLink to="/requests">Kembali ke daftar request</ButtonLink>
          }
        />
      </Card>
    );
  }

  if (forbidden) {
    return (
      <Card>
        <ForbiddenState message="Akun Anda tidak memiliki akses ke request ini." />
      </Card>
    );
  }

  if (error || !query.data) {
    return <ErrorState message={apiMessage(error)} onRetry={() => void query.refetch()} />;
  }

  const detail = query.data;
  const { summary } = detail;
  const canDecide = summary.status === 'SUBMITTED' || summary.status === 'UNDER_REVIEW';
  const canCancel =
    summary.canCancel && ['SUBMITTED', 'UNDER_REVIEW', 'APPROVED', 'IN_PROGRESS'].includes(summary.status);
  const canClose = summary.canClose && (summary.status === 'FULFILLED' || summary.status === 'REJECTED');
  const canStartReview = summary.canApprove && summary.status === 'SUBMITTED';
  const canFulfill = summary.canFulfill && FULFILLABLE.includes(summary.status);
  const decisionCopy = decision ? DECISION_COPY[decision] : null;
  const overdue =
    summary.neededByDate &&
    !['CLOSED', 'CANCELLED', 'REJECTED', 'FULFILLED'].includes(summary.status) &&
    summary.neededByDate < new Date().toISOString().slice(0, 10);

  return (
    <>
      <div className="mb-4">
        <Link
          to="/requests"
          className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
        >
          <Icon name="arrow-left" size={14} />
          Kembali ke daftar request
        </Link>
      </div>

      <PageHeader
        title={summary.title}
        description={`${summary.requestCode} · ${requestTypeLabel(summary.requestType)}`}
        meta={
          <>
            <StatusBadge status={summary.status} />
            {overdue ? <Badge tone="danger">Melewati batas waktu</Badge> : null}
            <span className="text-xs text-ink-secondary">Dibuat {formatDateTime(summary.createdAt)}</span>
          </>
        }
        action={
          <>
            {canStartReview ? (
              <Button variant="secondary" onClick={() => setReviewOpen(true)}>
                Mulai review
              </Button>
            ) : null}
            {canDecide && summary.canApprove ? (
              <Button onClick={() => setDecision('approve')}>Setujui</Button>
            ) : null}
            {canDecide && summary.canReject ? (
              <Button variant="danger" onClick={() => setDecision('reject')}>
                Tolak
              </Button>
            ) : null}
            {canFulfill ? (
              <Button onClick={() => setFulfillOpen(true)}>
                <Icon name="check" size={16} />
                Penuhi request
              </Button>
            ) : null}
            {canClose ? (
              <Button variant="secondary" onClick={() => setDecision('close')}>
                Tutup
              </Button>
            ) : null}
            {canCancel ? (
              <Button variant="ghost" onClick={() => setDecision('cancel')}>
                Batalkan
              </Button>
            ) : null}
          </>
        }
      />

      <div className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_360px]">
        <div className="space-y-5">
          <Card>
            <CardHeader title="Ringkasan request" />
            <div className="px-5 py-2">
              <dl>
                <DetailRow label="Pemohon">{summary.requesterName ?? '—'}</DetailRow>
                <DetailRow label="Approver">{summary.assignedToName ?? '—'}</DetailRow>
                <DetailRow label="Dokumen target">
                  {summary.targetDocumentId ? (
                    <Link
                      to={`/documents/${summary.targetDocumentId}`}
                      className="font-medium text-primary hover:underline"
                    >
                      {summary.targetDocumentTitle ?? 'Buka dokumen'}
                    </Link>
                  ) : (
                    '—'
                  )}
                </DetailRow>
                <DetailRow label="Pemilik dokumen">{detail.targetOwnerName ?? '—'}</DetailRow>
                <DetailRow label="Unit organisasi">
                  {summary.targetOrganizationUnitName ?? '—'}
                </DetailRow>
                <DetailRow label="Dibutuhkan sebelum">
                  {formatDate(summary.neededByDate)}
                </DetailRow>
                <DetailRow label="Dikirim">{formatDateTime(summary.submittedAt)}</DetailRow>
                <DetailRow label="Terakhir diperbarui">
                  {formatDateTime(summary.updatedAt)}
                </DetailRow>
              </dl>
            </div>
          </Card>

          <Card>
            <CardHeader title="Alasan dan deskripsi" />
            <div className="space-y-4 px-5 py-4 text-sm">
              <div>
                <p className="text-xs font-semibold text-ink-secondary">Alasan</p>
                <p className="mt-1 whitespace-pre-wrap text-ink">{detail.reason}</p>
              </div>
              <div>
                <p className="text-xs font-semibold text-ink-secondary">Deskripsi</p>
                <p className="mt-1 whitespace-pre-wrap text-ink">
                  {detail.description || 'Tidak ada deskripsi tambahan.'}
                </p>
              </div>
            </div>
          </Card>

          <Card>
            <CardHeader title="Riwayat proses" description="Urutan kronologis perubahan status request." />
            <ol className="space-y-0 px-5 py-2">
              {detail.history.map((entry, index) => (
                <li key={entry.id} className="relative flex gap-3 py-3">
                  <span className="relative flex w-5 shrink-0 justify-center">
                    {index < detail.history.length - 1 ? (
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
                        {humanizeStatus(entry.toStatus)}
                      </span>
                      {entry.fromStatus ? (
                        <span className="text-xs text-ink-secondary">
                          dari {humanizeStatus(entry.fromStatus)}
                        </span>
                      ) : null}
                    </div>
                    <p className="mt-0.5 text-xs text-ink-secondary">
                      {entry.actorName ?? 'Sistem'} · {formatDateTime(entry.occurredAt)}
                    </p>
                    {entry.comment ? (
                      <p className="mt-1 rounded-md bg-surface-muted px-2.5 py-1.5 text-xs whitespace-pre-wrap text-ink">
                        {entry.comment}
                      </p>
                    ) : null}
                  </div>
                </li>
              ))}
            </ol>
          </Card>
        </div>

        <div className="space-y-5">
          <Card>
            <CardHeader title="Persetujuan" />
            <ul className="divide-y divide-line">
              {detail.approvals.map((approval) => (
                <li key={approval.id} className="px-5 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-ink">{approval.approverName ?? '—'}</p>
                    <StatusBadge status={approval.decision} />
                  </div>
                  <p className="mt-1 text-xs text-ink-secondary">
                    Ditugaskan {formatDateTime(approval.assignedAt)}
                    {approval.decidedAt ? ` · Diputuskan ${formatDateTime(approval.decidedAt)}` : ''}
                  </p>
                  {approval.decisionReason ? (
                    <p className="mt-1.5 rounded-md bg-surface-muted px-2.5 py-1.5 text-xs whitespace-pre-wrap text-ink">
                      {approval.decisionReason}
                    </p>
                  ) : null}
                </li>
              ))}
            </ul>
          </Card>

          <Card>
            <CardHeader title="Pemenuhan" />
            {detail.fulfillments.length === 0 ? (
              <p className="px-5 py-4 text-xs text-ink-secondary">
                Belum ada pemenuhan. Pemenuhan tersedia setelah request disetujui.
              </p>
            ) : (
              <ul className="divide-y divide-line">
                {detail.fulfillments.map((fulfillment) => (
                  <li key={fulfillment.id} className="px-5 py-3">
                    <div className="flex flex-wrap items-center justify-between gap-2">
                      <p className="text-sm font-semibold text-ink">
                        {FULFILLMENT_TYPE_LABELS[fulfillment.type]}
                      </p>
                      <span className="text-xs text-ink-secondary">
                        {formatDateTime(fulfillment.fulfilledAt)}
                      </span>
                    </div>
                    <p className="mt-0.5 text-xs text-ink-secondary">
                      Oleh {fulfillment.fulfilledByName ?? '—'}
                    </p>
                    {fulfillment.note ? (
                      <p className="mt-1 rounded-md bg-surface-muted px-2.5 py-1.5 text-xs whitespace-pre-wrap text-ink">
                        {fulfillment.note}
                      </p>
                    ) : null}
                    {fulfillment.items.length > 0 ? (
                      <ul className="mt-2 space-y-1">
                        {fulfillment.items.map((item) => (
                          <li key={item.documentId}>
                            <Link
                              to={`/documents/${item.documentId}`}
                              className="inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline"
                            >
                              <Icon name="document" size={12} />
                              {item.documentTitle ?? 'Dokumen'}
                            </Link>
                          </li>
                        ))}
                      </ul>
                    ) : null}
                    {fulfillment.externalUrl ? (
                      <a
                        href={fulfillment.externalUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="mt-2 inline-flex items-center gap-1 text-xs font-medium break-all text-primary hover:underline"
                      >
                        <Icon name="link" size={12} />
                        {fulfillment.externalLinkTitle ?? fulfillment.externalUrl}
                      </a>
                    ) : null}
                  </li>
                ))}
              </ul>
            )}
          </Card>
        </div>
      </div>

      <ConfirmDialog
        open={reviewOpen}
        title="Mulai review request"
        message="Request akan dipindahkan ke tahap review. Lanjutkan?"
        confirmLabel="Mulai review"
        loading={startReviewMutation.isPending}
        onClose={() => setReviewOpen(false)}
        onConfirm={() => startReviewMutation.mutate()}
      />

      {decisionCopy ? (
        <DecisionDialog
          open={decision !== null}
          title={decisionCopy.title}
          description={`Request ${summary.requestCode} — ${summary.title}`}
          confirmLabel={decisionCopy.label}
          tone={decisionCopy.tone}
          requireComment={decisionCopy.requireComment}
          onClose={() => setDecision(null)}
          onSubmit={async (comment) => {
            if (!decision) {
              return;
            }
            await decisionMutation.mutateAsync({ action: decision, comment });
          }}
        />
      ) : null}

      {fulfillOpen ? (
        <FulfillDialog
          open
          request={detail}
          onClose={() => setFulfillOpen(false)}
          onCompleted={(updated) => {
            applyDetail(updated);
            setFulfillOpen(false);
          }}
        />
      ) : null}
    </>
  );
}
