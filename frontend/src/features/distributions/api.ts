import { apiRequest } from '../../shared/api/client';
import type { PageResponse } from '../../shared/api/types';

export type DistributionBox = 'sent' | 'received';
export type DistributionAccessLevel = 'VIEW' | 'PREVIEW' | 'DOWNLOAD';
export type DistributionStatus = 'DRAFT' | 'SENT' | 'PARTIALLY_REVOKED' | 'REVOKED' | 'EXPIRED';
export type DistributionRecipientStatus = 'SENT' | 'READ' | 'REVOKED' | 'EXPIRED';

export interface DistributionSummary {
  id: string;
  distributionCode: string;
  title: string;
  senderUserId: string;
  senderName: string | null;
  status: DistributionStatus;
  sentAt: string;
  expiresAt: string | null;
  itemCount: number;
  recipientCount: number;
  readCount: number;
  recipientStatus: DistributionRecipientStatus | null;
  recipientStatusAt: string | null;
  canRevoke: boolean;
}

export interface DistributionItem {
  documentId: string;
  documentTitle: string | null;
  documentCode: string | null;
  documentVersionId: string | null;
  versionNumber: number | null;
  versionLabel: string | null;
  accessLevel: DistributionAccessLevel;
  canOpen: boolean;
}

export interface DistributionRecipient {
  id: string;
  userId: string;
  userName: string | null;
  status: DistributionRecipientStatus;
  sentAt: string;
  readAt: string | null;
  revokedAt: string | null;
  expiresAt: string | null;
}

export interface DistributionEvent {
  id: string;
  eventType: 'SENT' | 'READ' | 'REVOKED' | 'EXPIRED';
  recipientId: string | null;
  recipientName: string | null;
  actorUserId: string | null;
  actorName: string | null;
  note: string | null;
  occurredAt: string;
}

export interface DistributionDetail {
  summary: DistributionSummary;
  message: string | null;
  items: DistributionItem[];
  recipients: DistributionRecipient[];
  events: DistributionEvent[];
}

export interface DistributionSearchParams {
  box: DistributionBox;
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}

export function listDistributions(
  params: DistributionSearchParams,
): Promise<PageResponse<DistributionSummary>> {
  const query = new URLSearchParams({ box: params.box });
  for (const [key, value] of Object.entries(params)) {
    if (key === 'box' || value === undefined || value === null || value === '') {
      continue;
    }
    query.set(key, String(value));
  }
  return apiRequest(`/api/v1/distributions?${query.toString()}`);
}

export function getDistribution(distributionId: string): Promise<DistributionDetail> {
  return apiRequest(`/api/v1/distributions/${distributionId}`);
}

export interface CreateDistributionPayload {
  title: string;
  message?: string;
  expiresAt?: string;
  recipientUserIds: string[];
  recipientOrganizationUnitIds: string[];
  items: { documentId: string; accessLevel: DistributionAccessLevel }[];
}

export function createDistribution(
  payload: CreateDistributionPayload,
): Promise<DistributionDetail> {
  return apiRequest('/api/v1/distributions', { method: 'POST', body: payload });
}

export function revokeDistribution(distributionId: string): Promise<DistributionDetail> {
  return apiRequest(`/api/v1/distributions/${distributionId}/revoke`, { method: 'POST' });
}

export function revokeDistributionRecipient(
  distributionId: string,
  recipientId: string,
): Promise<DistributionDetail> {
  return apiRequest(
    `/api/v1/distributions/${distributionId}/recipients/${recipientId}/revoke`,
    { method: 'POST' },
  );
}

export const accessLevelLabels: Record<DistributionAccessLevel, string> = {
  VIEW: 'Lihat detail',
  PREVIEW: 'Lihat + preview',
  DOWNLOAD: 'Lihat + preview + unduh',
};

export const accessLevelDescriptions: Record<DistributionAccessLevel, string> = {
  VIEW: 'Metadata dan detail dokumen tanpa membuka isi file.',
  PREVIEW: 'Dapat membuka preview isi dokumen di aplikasi.',
  DOWNLOAD: 'Dapat preview dan mengunduh file dokumen.',
};

export const distributionStatusLabels: Record<DistributionStatus, string> = {
  DRAFT: 'Draft',
  SENT: 'Terkirim',
  PARTIALLY_REVOKED: 'Dicabut sebagian',
  REVOKED: 'Dicabut',
  EXPIRED: 'Kedaluwarsa',
};

export const recipientStatusLabels: Record<DistributionRecipientStatus, string> = {
  SENT: 'Terkirim',
  READ: 'Dibaca',
  REVOKED: 'Dicabut',
  EXPIRED: 'Kedaluwarsa',
};

export const distributionEventLabels: Record<DistributionEvent['eventType'], string> = {
  SENT: 'Dikirim',
  READ: 'Dibaca',
  REVOKED: 'Akses dicabut',
  EXPIRED: 'Kedaluwarsa',
};

export const DISTRIBUTION_STATUS_OPTIONS: { value: DistributionStatus; label: string }[] = [
  { value: 'SENT', label: distributionStatusLabels.SENT },
  { value: 'PARTIALLY_REVOKED', label: distributionStatusLabels.PARTIALLY_REVOKED },
  { value: 'REVOKED', label: distributionStatusLabels.REVOKED },
  { value: 'EXPIRED', label: distributionStatusLabels.EXPIRED },
];
