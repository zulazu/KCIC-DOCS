import { apiRequest } from '../../../shared/api/client';
import type { PageResponse } from '../../../shared/api/types';

export interface AuditEvent {
  id: string;
  actorUserId: string | null;
  actorUsername: string | null;
  actorDisplayName: string | null;
  actionCode: string;
  resourceType: string;
  resourceId: string | null;
  outcome: string;
  source: string;
  requestId: string | null;
  safeMetadata: Record<string, unknown>;
  occurredAt: string;
}

export interface AuditSearchParams {
  actor?: string;
  action?: string;
  resourceType?: string;
  resourceId?: string;
  outcome?: string;
  from?: string;
  to?: string;
  page: number;
  size: number;
}

export function searchAuditEvents(params: AuditSearchParams): Promise<PageResponse<AuditEvent>> {
  const query = new URLSearchParams();
  query.set('page', String(params.page));
  query.set('size', String(params.size));
  for (const key of ['actor', 'action', 'resourceType', 'resourceId', 'outcome', 'from', 'to'] as const) {
    const value = params[key];
    if (value) {
      query.set(key, value);
    }
  }
  return apiRequest(`/api/v1/audit-events?${query.toString()}`);
}
