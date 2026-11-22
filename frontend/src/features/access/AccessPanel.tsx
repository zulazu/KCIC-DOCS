import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { AccessGrant, AccessResourceType } from '../../shared/api/types';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import { ConfirmDialog } from '../../shared/components/Dialog';
import { Alert, EmptyState, ErrorState, LoadingBlock } from '../../shared/components/Feedback';
import { Icon } from '../../shared/components/Icon';
import { Card, CardHeader } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { formatDateTime } from '../../shared/utils/format';
import {
  type AccessTarget,
  effectLabels,
  getAccessSummary,
  grantSourceLabels,
  principalTypeLabels,
  revokeAccessGrant,
} from './api';
import { GrantDialog } from './GrantDialog';

const panelTitles: Record<AccessResourceType, string> = {
  WORKSPACE: 'Akses workspace',
  FOLDER: 'Akses folder',
  DOCUMENT: 'Kontrol akses',
};

function targetKey(target: AccessTarget): string {
  return [target.type, target.workspaceId ?? '', target.folderId ?? '', target.documentId ?? ''].join(
    ':',
  );
}

function principalLabel(grant: AccessGrant): string {
  return grant.principalName ?? grant.principalId;
}

function permissionLabel(grant: AccessGrant): string {
  return grant.permissionName ?? grant.permissionCode;
}

function EffectBadge({ effect }: { effect: AccessGrant['effect'] }) {
  return <Badge tone={effect === 'ALLOW' ? 'success' : 'danger'}>{effectLabels[effect]}</Badge>;
}

function GrantMeta({ grant }: { grant: AccessGrant }) {
  return (
    <p className="text-xs text-ink-secondary">
      {principalTypeLabels[grant.principalType]} · Sumber {grantSourceLabels[grant.grantSource] ?? grant.grantSource}{' '}
      · Dicatat {grant.grantedByName ?? 'Sistem'} pada {formatDateTime(grant.grantedAt)}
      {grant.expiresAt ? ` · Kedaluwarsa ${formatDateTime(grant.expiresAt)}` : ' · Tanpa batas waktu'}
    </p>
  );
}

function ActiveGrantRow({
  grant,
  canManage,
  onRevoke,
}: {
  grant: AccessGrant;
  canManage: boolean;
  onRevoke: (grant: AccessGrant) => void;
}) {
  return (
    <li className="flex flex-col gap-2 px-5 py-3.5 sm:flex-row sm:items-start sm:justify-between">
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-semibold break-words text-ink">{principalLabel(grant)}</span>
          <EffectBadge effect={grant.effect} />
          {grant.includeDescendants ? <Badge tone="gold">Termasuk turunan</Badge> : null}
        </div>
        <p className="mt-0.5 text-sm text-ink">
          {permissionLabel(grant)}{' '}
          <span className="font-mono text-[11px] text-ink-secondary">({grant.permissionCode})</span>
        </p>
        <GrantMeta grant={grant} />
      </div>
      {canManage ? (
        <Button
          size="compact"
          variant="ghost"
          className="shrink-0 self-start text-danger hover:bg-danger-bg"
          onClick={() => onRevoke(grant)}
        >
          Cabut
        </Button>
      ) : null}
    </li>
  );
}

function HistoryGrantRow({ grant }: { grant: AccessGrant }) {
  const revoked = grant.revokedAt !== null;
  return (
    <li className="px-5 py-3">
      <div className="flex flex-wrap items-center gap-2">
        <span className="text-sm font-medium break-words text-ink">{principalLabel(grant)}</span>
        <EffectBadge effect={grant.effect} />
        {grant.includeDescendants ? <Badge tone="gold">Termasuk turunan</Badge> : null}
        <Badge tone="neutral">{revoked ? 'Dicabut' : 'Kedaluwarsa'}</Badge>
      </div>
      <p className="mt-0.5 text-xs text-ink">
        {permissionLabel(grant)}{' '}
        <span className="font-mono text-[11px] text-ink-secondary">({grant.permissionCode})</span>
      </p>
      <p className="text-xs text-ink-secondary">
        {revoked
          ? `Dicabut oleh ${grant.revokedByName ?? 'Sistem'} pada ${formatDateTime(grant.revokedAt)}`
          : `Kedaluwarsa pada ${formatDateTime(grant.expiresAt)}`}
      </p>
      <p className="text-xs text-ink-secondary">
        Dicatat {grant.grantedByName ?? 'Sistem'} pada {formatDateTime(grant.grantedAt)}
      </p>
    </li>
  );
}

export function AccessPanel({
  target,
  canManage,
  onChanged,
  showHeader = true,
}: {
  target: AccessTarget;
  canManage: boolean;
  onChanged?: () => void;
  showHeader?: boolean;
}) {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const key = targetKey(target);
  const [grantOpen, setGrantOpen] = useState(false);
  const [revokeTarget, setRevokeTarget] = useState<AccessGrant | null>(null);
  const [showHistory, setShowHistory] = useState(false);

  const accessQuery = useQuery({
    queryKey: ['access', key],
    queryFn: () => getAccessSummary(target),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['access', key] });
    onChanged?.();
  };

  const revokeMutation = useMutation({
    mutationFn: (grant: AccessGrant) => revokeAccessGrant(target, grant.id),
    onSuccess: (_data, grant) => {
      notify(`Grant ${permissionLabel(grant)} untuk ${principalLabel(grant)} dicabut.`);
      setRevokeTarget(null);
      invalidate();
    },
    onError: (error) => {
      notify(apiMessage(error), 'danger');
      setRevokeTarget(null);
    },
  });

  const summary = accessQuery.data;
  const active = summary?.active ?? [];
  const history = summary?.history ?? [];

  return (
    <Card>
      {showHeader ? (
        <CardHeader
          title={panelTitles[target.type]}
          description="Grant manual tercatat di audit trail. Deny eksplisit mengalahkan izin warisan."
          action={
            canManage && summary ? (
              <Button size="compact" onClick={() => setGrantOpen(true)}>
                <Icon name="plus" size={14} />
                Beri akses
              </Button>
            ) : undefined
          }
        />
      ) : canManage && summary ? (
        <div className="flex items-center justify-end border-b border-line px-5 py-3">
          <Button size="compact" onClick={() => setGrantOpen(true)}>
            <Icon name="plus" size={14} />
            Beri akses
          </Button>
        </div>
      ) : null}

      {accessQuery.isPending ? (
        <div className="px-5 py-4">
          <LoadingBlock label="Memuat kontrol akses…" />
        </div>
      ) : accessQuery.isError ? (
        <ErrorState
          message={apiMessage(accessQuery.error)}
          onRetry={() => void accessQuery.refetch()}
        />
      ) : active.length === 0 ? (
        <EmptyState
          title="Belum ada grant manual"
          description="Akses mengikuti role, scope unit, dan grant induk sampai grant manual dicatat di sini."
          action={
            canManage ? (
              <Button onClick={() => setGrantOpen(true)}>
                <Icon name="plus" size={16} />
                Beri akses
              </Button>
            ) : undefined
          }
        />
      ) : (
        <ul className="divide-y divide-line">
          {active.map((grant) => (
            <ActiveGrantRow
              key={grant.id}
              grant={grant}
              canManage={canManage}
              onRevoke={setRevokeTarget}
            />
          ))}
        </ul>
      )}

      {history.length > 0 ? (
        <div className="border-t border-line px-5 py-3">
          <button
            type="button"
            aria-expanded={showHistory}
            onClick={() => setShowHistory((current) => !current)}
            className="inline-flex items-center gap-1.5 text-xs font-semibold text-ink-secondary hover:text-primary"
          >
            <Icon
              name="chevron-down"
              size={14}
              className={cn('transition-transform', showHistory ? 'rotate-180' : '')}
            />
            Riwayat grant ({history.length})
          </button>
          {showHistory ? (
            <ul className="mt-2 divide-y divide-line rounded-lg border border-line bg-background">
              {history.map((grant) => (
                <HistoryGrantRow key={grant.id} grant={grant} />
              ))}
            </ul>
          ) : null}
        </div>
      ) : null}

      {accessQuery.data ? (
        <p className="border-t border-line px-5 py-3 text-xs text-ink-secondary">
          Pencabutan satu grant tidak memengaruhi grant dari sumber lain.
        </p>
      ) : accessQuery.isError ? (
        <div className="px-5 pb-4">
          <Alert tone="warning">
            Daftar grant tidak dapat dimuat. Operasi baca/tulis tetap diperiksa backend pada setiap
            permintaan.
          </Alert>
        </div>
      ) : null}

      {grantOpen ? (
        <GrantDialog
          target={target}
          onClose={() => setGrantOpen(false)}
          onGranted={invalidate}
        />
      ) : null}

      <ConfirmDialog
        open={revokeTarget !== null}
        title="Cabut grant akses?"
        message={
          revokeTarget ? (
            <>
              Grant <span className="font-semibold">{permissionLabel(revokeTarget)}</span> (
              {effectLabels[revokeTarget.effect]}) untuk{' '}
              <span className="font-semibold">{principalLabel(revokeTarget)}</span> akan dicabut.
              Akses dari role dan grant lain tetap berlaku sesuai sumbernya.
            </>
          ) : null
        }
        confirmLabel="Cabut grant"
        loading={revokeMutation.isPending}
        onConfirm={() => revokeTarget && revokeMutation.mutate(revokeTarget)}
        onClose={() => {
          if (!revokeMutation.isPending) {
            setRevokeTarget(null);
          }
        }}
      />
    </Card>
  );
}
