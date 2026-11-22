import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Icon } from '../../shared/components/Icon';
import { getUnreadCount } from './api';

export function NotificationBell() {
  const query = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: getUnreadCount,
    refetchInterval: 60_000,
    staleTime: 30_000,
  });
  const count = query.data?.unreadCount ?? 0;
  const label = count > 0 ? `Notifikasi, ${count} belum dibaca` : 'Notifikasi';
  return (
    <Link
      to="/notifications"
      aria-label={label}
      title={label}
      className="relative rounded-md p-2 text-ink-secondary hover:bg-surface-muted hover:text-primary"
    >
      <Icon name="bell" size={18} />
      {count > 0 ? (
        <span className="absolute top-0.5 right-0.5 flex min-w-4 items-center justify-center rounded-full bg-primary px-1 text-[10px] leading-4 font-bold text-white">
          {count > 99 ? '99+' : count}
        </span>
      ) : null}
    </Link>
  );
}
