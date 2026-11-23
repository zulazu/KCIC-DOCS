import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import type { ReactNode } from 'react';
import { visibleNavigation } from '../../app/navigation';
import { Badge, StatusBadge } from '../../shared/components/Badge';
import { Button, ButtonLink } from '../../shared/components/Button';
import { Icon, type IconName } from '../../shared/components/Icon';
import { Card, CardHeader, DetailRow, PageHeader } from '../../shared/components/Surface';
import { cn } from '../../shared/utils/cn';
import { formatDate, formatDateTime, roleLabel } from '../../shared/utils/format';
import { listDocuments } from '../documents/api';
import { listDistributions } from '../distributions/api';
import { getUnreadCount, listNotifications } from '../notifications/api';
import { listRequests } from '../requests/api';
import { requestTypeLabel } from '../requests/labels';
import { useAuth } from '../auth/AuthContext';

const ASSIGNED_CAPABILITIES = ['request.view_assigned', 'request.approve', 'request.fulfill'];

function hasAny(capabilities: string[], codes: string[]): boolean {
  return codes.some((code) => capabilities.includes(code));
}

function StatTile({
  to,
  label,
  icon,
  value,
  loading,
  highlight = false,
}: {
  to: string;
  label: string;
  icon: IconName;
  value: number | null;
  loading: boolean;
  highlight?: boolean;
}) {
  return (
    <Link
      to={to}
      className={cn(
        'tile-hover rounded-card border p-4 shadow-card',
        highlight
          ? 'border-primary bg-primary hover:bg-primary-hover'
          : 'border-line bg-surface hover:border-primary/30',
      )}
    >
      <span className="flex items-start justify-between gap-2">
        <span
          className={cn(
            'text-xs font-bold',
            highlight ? 'text-white/75' : 'text-ink-secondary',
          )}
        >
          {label}
        </span>
        <span
          aria-hidden="true"
          className={cn(
            'flex size-8 shrink-0 items-center justify-center rounded-lg',
            highlight ? 'bg-white/15 text-gold' : 'bg-primary-subtle text-primary',
          )}
        >
          <Icon name={icon} size={16} />
        </span>
      </span>
      <span
        className={cn(
          'mt-1 block font-display text-[32px] leading-9 font-bold tabular-nums',
          highlight ? 'text-white' : 'text-ink',
        )}
      >
        {loading ? (
          <span className="skeleton block h-9 w-14" role="status" aria-label="Memuat angka" />
        ) : (
          (value ?? '—')
        )}
      </span>
    </Link>
  );
}

function TileLink({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link
      to={to}
      className="inline-flex items-center gap-1 text-xs font-semibold text-primary hover:underline"
    >
      {children}
      <Icon name="chevron-right" size={13} />
    </Link>
  );
}

function TileEmpty({ children }: { children: ReactNode }) {
  return <p className="px-5 py-8 text-sm text-ink-secondary">{children}</p>;
}

function TileSkeleton() {
  return (
    <div className="space-y-3 px-5 py-4" role="status" aria-label="Memuat data">
      <div className="skeleton h-9 w-full" />
      <div className="skeleton h-9 w-full" />
      <div className="skeleton h-9 w-full" />
    </div>
  );
}

function TileError({ onRetry }: { onRetry: () => void }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 px-5 py-6 text-sm text-ink-secondary">
      <span>Data tidak dapat dimuat.</span>
      <Button variant="ghost" size="compact" onClick={onRetry}>
        Coba lagi
      </Button>
    </div>
  );
}

export function DashboardPage() {
  const { me } = useAuth();
  const capabilities = me?.capabilities ?? [];
  const canViewDocuments = capabilities.includes('document.view');
  const canViewAssigned = hasAny(capabilities, ASSIGNED_CAPABILITIES);
  const canViewNotifications = capabilities.includes('notification.view');
  const canViewReceived = capabilities.includes('distribution.view_received');
  const canViewSent = capabilities.includes('distribution.view_own');
  const distributionBox = canViewReceived ? 'received' : 'sent';
  const canViewDistributions = canViewReceived || canViewSent;

  const adminGroup = visibleNavigation(capabilities).find(
    (group) => group.label === 'Administration',
  );

  const tasksQuery = useQuery({
    queryKey: ['dashboard', 'tasks'],
    queryFn: () => listRequests({ scope: 'assigned', page: 0, size: 4 }),
    enabled: canViewAssigned,
    staleTime: 60_000,
  });
  const documentsQuery = useQuery({
    queryKey: ['dashboard', 'documents'],
    queryFn: () => listDocuments({ sort: '-updatedAt', page: 0, size: 4 }),
    enabled: canViewDocuments,
    staleTime: 60_000,
  });
  const notificationsQuery = useQuery({
    queryKey: ['dashboard', 'notifications'],
    queryFn: () => listNotifications(false, 0, 4),
    enabled: canViewNotifications,
    staleTime: 30_000,
  });
  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: getUnreadCount,
    enabled: canViewNotifications,
    staleTime: 30_000,
  });
  const distributionsQuery = useQuery({
    queryKey: ['dashboard', 'distributions', distributionBox],
    queryFn: () => listDistributions({ box: distributionBox, page: 0, size: 3 }),
    enabled: canViewDistributions,
    staleTime: 60_000,
  });

  const tasks = tasksQuery.data?.items ?? [];
  const documents = documentsQuery.data?.items ?? [];
  const notifications = notificationsQuery.data?.items ?? [];
  const distributions = distributionsQuery.data?.items ?? [];
  const unreadCount = unreadQuery.data?.unreadCount ?? 0;

  if (!me) {
    return null;
  }

  return (
    <>
      <PageHeader
        title={`Selamat datang, ${me.displayName}`}
        action={
          canViewDocuments ? (
            <ButtonLink to="/documents">
              <Icon name="search" size={16} />
              Cari dokumen
            </ButtonLink>
          ) : undefined
        }
      />

      <div className="mb-4 grid grid-cols-2 gap-4 lg:grid-cols-4">
        {canViewAssigned ? (
          <StatTile
            to="/requests"
            label="Request ditugaskan"
            icon="document"
            value={tasksQuery.isError ? null : (tasksQuery.data?.totalItems ?? null)}
            loading={tasksQuery.isLoading}
          />
        ) : null}
        {canViewDocuments ? (
          <StatTile
            to="/documents"
            label="Dokumen dapat diakses"
            icon="folder"
            value={documentsQuery.isError ? null : (documentsQuery.data?.totalItems ?? null)}
            loading={documentsQuery.isLoading}
          />
        ) : null}
        {canViewDistributions ? (
          <StatTile
            to="/distributions"
            label={distributionBox === 'received' ? 'Distribusi diterima' : 'Distribusi dikirim'}
            icon="link"
            value={distributionsQuery.isError ? null : (distributionsQuery.data?.totalItems ?? null)}
            loading={distributionsQuery.isLoading}
          />
        ) : null}
        {canViewNotifications ? (
          <StatTile
            to="/notifications"
            label="Notifikasi belum dibaca"
            icon="bell"
            value={unreadQuery.isError ? null : unreadCount}
            loading={unreadQuery.isLoading}
            highlight={unreadCount > 0}
          />
        ) : null}
      </div>

      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1fr)_360px] 2xl:grid-cols-[minmax(0,1fr)_420px]">
        <div className="min-w-0 space-y-4">
          {canViewAssigned ? (
            <Card>
              <CardHeader
                title="Request ditugaskan"
                action={<TileLink to="/requests">Semua request</TileLink>}
              />
              {tasksQuery.isLoading ? (
                <TileSkeleton />
              ) : tasksQuery.isError ? (
                <TileError onRetry={() => void tasksQuery.refetch()} />
              ) : tasks.length === 0 ? (
                <TileEmpty>Tidak ada request yang ditugaskan kepada Anda.</TileEmpty>
              ) : (
                <ul className="divide-y divide-line">
                  {tasks.map((task) => (
                    <li key={task.id}>
                      <Link
                        to={`/requests/${task.id}`}
                        className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-primary-subtle/40"
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-subtle text-primary">
                          <Icon name="document" size={16} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-2">
                            <span className="truncate text-sm font-semibold text-ink">
                              {task.title}
                            </span>
                            <StatusBadge status={task.status} />
                          </span>
                          <span className="mt-0.5 block truncate text-xs text-ink-secondary">
                            {task.requestCode} · {requestTypeLabel(task.requestType)} ·{' '}
                            {task.requesterName ?? 'Pemohon'}
                          </span>
                        </span>
                        <span className="hidden shrink-0 text-right text-xs text-ink-secondary sm:block">
                          <span className="block">Dibutuhkan</span>
                          <span className="font-medium text-ink">
                            {formatDate(task.neededByDate)}
                          </span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ) : null}

          {canViewDocuments ? (
            <Card>
              <CardHeader
                title="Dokumen terbaru"
                action={<TileLink to="/documents">Lihat katalog</TileLink>}
              />
              {documentsQuery.isLoading ? (
                <TileSkeleton />
              ) : documentsQuery.isError ? (
                <TileError onRetry={() => void documentsQuery.refetch()} />
              ) : documents.length === 0 ? (
                <TileEmpty>Belum ada dokumen yang dapat Anda akses.</TileEmpty>
              ) : (
                <ul className="divide-y divide-line">
                  {documents.map((document) => (
                    <li key={document.id}>
                      <Link
                        to={`/documents/${document.id}`}
                        className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-primary-subtle/40"
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-subtle text-primary">
                          <Icon
                            name={document.contentSource === 'EXTERNAL_LINK' ? 'link' : 'document'}
                            size={16}
                          />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-2">
                            <span className="truncate text-sm font-semibold text-ink">
                              {document.title}
                            </span>
                            <StatusBadge status={document.status} />
                          </span>
                          <span className="mt-0.5 block truncate text-xs text-ink-secondary">
                            {document.documentCode ?? 'Tanpa kode'} ·{' '}
                            {document.workspaceName ?? 'Workspace'}
                            {document.currentVersionNumber
                              ? ` · Versi ${document.currentVersionLabel ?? document.currentVersionNumber}`
                              : ''}
                          </span>
                        </span>
                        <span className="hidden shrink-0 text-right text-xs text-ink-secondary sm:block">
                          <span className="block">Diperbarui</span>
                          <span className="font-medium text-ink">
                            {formatDateTime(document.updatedAt)}
                          </span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ) : null}

          {canViewDistributions ? (
            <Card>
              <CardHeader
                title="Distribusi terbaru"
                action={<TileLink to="/distributions">Lihat semua</TileLink>}
              />
              {distributionsQuery.isLoading ? (
                <TileSkeleton />
              ) : distributionsQuery.isError ? (
                <TileError onRetry={() => void distributionsQuery.refetch()} />
              ) : distributions.length === 0 ? (
                <TileEmpty>
                  {distributionBox === 'received'
                    ? 'Belum ada distribusi yang diterima.'
                    : 'Belum ada distribusi yang dikirim.'}
                </TileEmpty>
              ) : (
                <ul className="divide-y divide-line">
                  {distributions.map((distribution) => (
                    <li key={distribution.id}>
                      <Link
                        to={`/distributions/${distribution.id}`}
                        className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-primary-subtle/40"
                      >
                        <span className="flex size-8 shrink-0 items-center justify-center rounded-lg bg-primary-subtle text-primary">
                          <Icon name="link" size={16} />
                        </span>
                        <span className="min-w-0 flex-1">
                          <span className="flex flex-wrap items-center gap-2">
                            <span className="truncate text-sm font-semibold text-ink">
                              {distribution.title}
                            </span>
                            <StatusBadge status={distribution.status} />
                          </span>
                          <span className="mt-0.5 block truncate text-xs text-ink-secondary">
                            {distribution.distributionCode} ·{' '}
                            {distributionBox === 'received'
                              ? `Dari ${distribution.senderName ?? '—'}`
                              : `${distribution.recipientCount} penerima`}
                          </span>
                        </span>
                        <span className="hidden shrink-0 text-xs text-ink-secondary sm:block">
                          {formatDate(distribution.sentAt)}
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </Card>
          ) : null}
        </div>

        <div className="min-w-0 space-y-4">
          {canViewNotifications ? (
            <Card>
              <CardHeader
                title="Notifikasi"
                action={<TileLink to="/notifications">Lihat semua</TileLink>}
              />
              {notificationsQuery.isLoading ? (
                <TileSkeleton />
              ) : notificationsQuery.isError ? (
                <TileError onRetry={() => void notificationsQuery.refetch()} />
              ) : notifications.length === 0 ? (
                <TileEmpty>Belum ada notifikasi.</TileEmpty>
              ) : (
                <ul className="divide-y divide-line">
                      {notifications.map((notification) => (
                        <li key={notification.id}>
                          <Link
                            to="/notifications"
                            className="block px-5 py-2.5 transition-colors hover:bg-primary-subtle/40"
                          >
                            <span className="flex items-start gap-2">
                              <span
                                className={
                                  notification.read
                                    ? 'mt-1.5 size-1.5 shrink-0 rounded-full bg-line-strong'
                                    : 'mt-1.5 size-1.5 shrink-0 rounded-full bg-primary'
                                }
                                aria-hidden="true"
                              />
                              <span className="min-w-0">
                                <span className="block truncate text-sm font-medium text-ink">
                                  {notification.title}
                                </span>
                                <span className="block text-xs text-ink-secondary">
                                  {formatDateTime(notification.createdAt)}
                                </span>
                              </span>
                            </span>
                          </Link>
                        </li>
                      ))}
                </ul>
              )}
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Akun & akses" />
            <div className="px-5 py-3">
              <dl>
                <DetailRow label="Username">
                  <span className="font-mono text-xs">{me.username}</span>
                </DetailRow>
                <DetailRow label="Role">
                  {me.roles.length === 0 ? (
                    <span className="text-ink-secondary">Belum ada role</span>
                  ) : (
                    <div className="flex flex-wrap gap-1.5">
                      {me.roles.map((role) => (
                        <Badge key={role} tone="neutral">
                          {roleLabel(role)}
                        </Badge>
                      ))}
                    </div>
                  )}
                </DetailRow>
                <DetailRow label="Unit">
                  {me.organizationUnits.length === 0 ? (
                    <span className="text-ink-secondary">Belum tergabung</span>
                  ) : (
                    <ul className="space-y-1">
                      {me.organizationUnits.map((unit) => (
                        <li key={unit.id} className="flex flex-wrap items-center gap-1.5">
                          <span>{unit.unitName}</span>
                          <span className="text-xs text-ink-secondary">{unit.unitCode}</span>
                          {unit.assignmentType === 'PRIMARY' ? (
                            <Badge tone="gold">Utama</Badge>
                          ) : null}
                        </li>
                      ))}
                    </ul>
                  )}
                </DetailRow>
                <DetailRow label="Login terakhir">
                  <span className="text-xs">{formatDateTime(me.lastLoginAt)}</span>
                </DetailRow>
              </dl>
            </div>
          </Card>

          {adminGroup ? (
            <Card>
              <CardHeader title="Administrasi" />
              <ul className="p-2">
                {adminGroup.items.map((item) => (
                  <li key={item.to}>
                    <Link
                      to={item.to}
                      className="flex items-center gap-2.5 rounded-lg px-3 py-2 transition-colors hover:bg-surface-muted"
                    >
                      <Icon name={item.icon} size={16} className="shrink-0 text-primary" />
                      <span className="min-w-0 flex-1 truncate text-sm font-medium text-ink">
                        {item.label}
                      </span>
                      <Icon name="chevron-right" size={14} className="text-ink-disabled" />
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          ) : null}
        </div>
      </div>
    </>
  );
}
