import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import type { NotificationItem } from '../../shared/api/types';
import { Badge } from '../../shared/components/Badge';
import { Button } from '../../shared/components/Button';
import {
  EmptyState,
  ErrorState,
  LoadingBlock,
} from '../../shared/components/Feedback';
import { Icon } from '../../shared/components/Icon';
import { PaginationBar } from '../../shared/components/PaginationBar';
import { Card, PageHeader } from '../../shared/components/Surface';
import { useToast } from '../../shared/components/Toast';
import { apiMessage } from '../../shared/utils/apiMessage';
import { cn } from '../../shared/utils/cn';
import { formatDateTime } from '../../shared/utils/format';
import { getUnreadCount, listNotifications, markAllNotificationsRead, markNotificationRead } from './api';

const DEFAULT_PAGE_SIZE = 10;

function targetPath(notification: NotificationItem): string | null {
  if (!notification.targetResourceId) {
    return null;
  }
  switch (notification.targetResourceType) {
    case 'REQUEST':
      return `/requests/${notification.targetResourceId}`;
    case 'DOCUMENT':
      return `/documents/${notification.targetResourceId}`;
    case 'DISTRIBUTION':
      return `/distributions/${notification.targetResourceId}`;
    default:
      return null;
  }
}

export function NotificationsPage() {
  const { notify } = useToast();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);

  const query = useQuery({
    queryKey: ['notifications', 'list', { unreadOnly, page, pageSize }],
    queryFn: () => listNotifications(unreadOnly, page, pageSize),
    placeholderData: keepPreviousData,
  });

  function refresh() {
    void queryClient.invalidateQueries({ queryKey: ['notifications'] });
  }

  const markRead = useMutation({
    mutationFn: (notificationId: string) => markNotificationRead(notificationId),
    onSuccess: refresh,
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const markAll = useMutation({
    mutationFn: markAllNotificationsRead,
    onSuccess: () => {
      refresh();
      notify('Semua notifikasi ditandai sudah dibaca.');
    },
    onError: (error) => notify(apiMessage(error), 'danger'),
  });

  const unreadQuery = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: getUnreadCount,
    staleTime: 30_000,
  });

  async function open(notification: NotificationItem) {
    if (!notification.read) {
      try {
        await markNotificationRead(notification.id);
        refresh();
      } catch (error) {
        notify(apiMessage(error), 'danger');
      }
    }
    const path = targetPath(notification);
    if (path) {
      navigate(path);
    }
  }

  const items = query.data?.items ?? [];
  const unreadCount = unreadQuery.data?.unreadCount ?? 0;

  return (
    <>
      <PageHeader
        title="Notifikasi"
        action={
          <Button
            variant="secondary"
            onClick={() => markAll.mutate()}
            loading={markAll.isPending}
            disabled={unreadCount === 0}
          >
            <Icon name="check" size={16} />
            Tandai semua dibaca
          </Button>
        }
      />

      <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
        <label className="flex w-fit cursor-pointer items-center gap-2 text-xs font-medium text-ink-secondary">
          <input
            type="checkbox"
            className="size-4 rounded border-line-strong accent-primary"
            checked={unreadOnly}
            onChange={(event) => {
              setUnreadOnly(event.target.checked);
              setPage(0);
            }}
          />
          Hanya belum dibaca
        </label>
        {unreadCount > 0 ? <Badge tone="info">{unreadCount} belum dibaca</Badge> : null}
      </div>

      {query.isLoading ? (
        <LoadingBlock label="Memuat notifikasi…" />
      ) : query.error ? (
        <ErrorState message={apiMessage(query.error)} onRetry={() => void query.refetch()} />
      ) : items.length === 0 ? (
        <Card>
          <EmptyState
            title={unreadOnly ? 'Tidak ada notifikasi belum dibaca' : 'Belum ada notifikasi'}
            description={
              unreadOnly
                ? 'Semua notifikasi sudah dibaca.'
                : 'Notifikasi akan muncul ketika ada request baru, keputusan approval, atau pemenuhan dokumen.'
            }
          />
        </Card>
      ) : (
        <>
          <ul className="space-y-2" aria-label="Daftar notifikasi">
            {items.map((notification) => (
              <li key={notification.id}>
                <div
                  className={cn(
                    'flex flex-col gap-3 rounded-lg border px-4 py-3.5 sm:flex-row sm:items-start',
                    notification.read ? 'border-line bg-surface' : 'border-primary/25 bg-primary/[0.03]',
                  )}
                >
                  <span
                    className={cn(
                      'flex size-9 shrink-0 items-center justify-center rounded-lg',
                      notification.read ? 'bg-surface-muted text-ink-secondary' : 'bg-primary/10 text-primary',
                    )}
                    aria-hidden="true"
                  >
                    <Icon name="bell" size={17} />
                  </span>
                  <div className="min-w-0 flex-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-sm font-semibold text-ink">{notification.title}</p>
                      {!notification.read ? <Badge tone="info">Baru</Badge> : null}
                    </div>
                    {notification.message ? (
                      <p className="mt-0.5 text-xs whitespace-pre-wrap text-ink-secondary">
                        {notification.message}
                      </p>
                    ) : null}
                    <p className="mt-1 text-[11px] text-ink-secondary">
                      {formatDateTime(notification.createdAt)}
                    </p>
                  </div>
                  <div className="flex shrink-0 items-center gap-2">
                    {!notification.read ? (
                      <Button
                        variant="secondary"
                        size="compact"
                        loading={markRead.isPending && markRead.variables === notification.id}
                        onClick={() => markRead.mutate(notification.id)}
                      >
                        Tandai dibaca
                      </Button>
                    ) : null}
                    {targetPath(notification) ? (
                      <Button variant="secondary" size="compact" onClick={() => void open(notification)}>
                        Buka
                      </Button>
                    ) : null}
                  </div>
                </div>
              </li>
            ))}
          </ul>
          {query.data ? (
            <PaginationBar
              className="mt-4"
              page={page}
              size={pageSize}
              totalItems={query.data.totalItems}
              totalPages={query.data.totalPages}
              itemLabel="notifikasi"
              onPageChange={setPage}
              onSizeChange={(size) => {
                setPageSize(size);
                setPage(0);
              }}
            />
          ) : null}
        </>
      )}
    </>
  );
}
