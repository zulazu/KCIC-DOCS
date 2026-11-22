import { apiRequest } from '../../shared/api/client';
import type { NotificationItem, PageResponse, UnreadCount } from '../../shared/api/types';

export function listNotifications(
  unreadOnly: boolean,
  page = 0,
  size = 20,
): Promise<PageResponse<NotificationItem>> {
  const query = new URLSearchParams({ unreadOnly: String(unreadOnly), page: String(page), size: String(size) });
  return apiRequest(`/api/v1/notifications?${query.toString()}`);
}

export function getUnreadCount(): Promise<UnreadCount> {
  return apiRequest('/api/v1/notifications/unread-count');
}

export function markNotificationRead(notificationId: string): Promise<NotificationItem> {
  return apiRequest(`/api/v1/notifications/${notificationId}/read`, { method: 'POST' });
}

export function markAllNotificationsRead(): Promise<UnreadCount> {
  return apiRequest('/api/v1/notifications/read-all', { method: 'POST' });
}
