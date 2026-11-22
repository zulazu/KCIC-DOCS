import { apiRequest } from '../../shared/api/client';
import type { PageResponse } from '../../shared/api/types';

export type TemplateStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
export type TemplateUsageType = 'VIEW' | 'DOWNLOAD' | 'COPY';

export interface TemplateSummary {
  id: string;
  templateCode: string;
  templateName: string;
  description: string | null;
  status: TemplateStatus;
  documentId: string;
  documentCode: string | null;
  documentTitle: string | null;
  documentStatus: string;
  categoryId: string | null;
  categoryName: string | null;
  ownerUserId: string | null;
  ownerUserName: string | null;
  ownerOrganizationUnitId: string | null;
  ownerOrganizationUnitName: string | null;
  currentPublishedVersionId: string | null;
  currentPublishedVersionNumber: number | null;
  currentPublishedVersionLabel: string | null;
  contentSource: string | null;
  publishedAt: string | null;
  publishedByName: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface TemplateVersion {
  id: string;
  versionNumber: number;
  versionLabel: string | null;
  contentSource: string;
  status: string;
  changeSummary: string | null;
  publishedAt: string | null;
  currentlyPublished: boolean;
}

export interface TemplatePermissions {
  canEdit: boolean;
  canPublish: boolean;
  canArchive: boolean;
  canViewDocument: boolean;
  canDownloadDocument: boolean;
}

export interface TemplateDetail {
  summary: TemplateSummary;
  documentDescription: string | null;
  effectiveDate: string | null;
  reviewDate: string | null;
  expiryDate: string | null;
  documentUpdatedAt: string;
  currentVersionChangeSummary: string | null;
  versionHistory: TemplateVersion[];
  versionHistoryVisible: boolean;
  usageCount: number;
  viewCount: number;
  downloadCount: number;
  copyCount: number;
  permissions: TemplatePermissions;
}

export interface TemplateUsageResult {
  templateId: string;
  usageType: TemplateUsageType;
  versionNumber: number;
  occurredAt: string;
}

export interface TemplateSearchParams {
  keyword?: string;
  status?: TemplateStatus;
  categoryId?: string;
  ownerUserId?: string;
  organizationUnitId?: string;
  page?: number;
  size?: number;
}

export function listTemplates(
  params: TemplateSearchParams,
): Promise<PageResponse<TemplateSummary>> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    query.set(key, String(value));
  }
  const suffix = query.toString();
  return apiRequest(`/api/v1/templates${suffix ? `?${suffix}` : ''}`);
}

export function getTemplate(templateId: string): Promise<TemplateDetail> {
  return apiRequest(`/api/v1/templates/${templateId}`);
}

export interface CreateTemplatePayload {
  documentId: string;
  templateCode: string;
  templateName: string;
  description?: string;
}

export function createTemplate(payload: CreateTemplatePayload): Promise<TemplateDetail> {
  return apiRequest('/api/v1/templates', { method: 'POST', body: payload });
}

export interface UpdateTemplatePayload {
  templateCode: string;
  templateName: string;
  description?: string;
}

export function updateTemplate(
  templateId: string,
  payload: UpdateTemplatePayload,
): Promise<TemplateDetail> {
  return apiRequest(`/api/v1/templates/${templateId}`, { method: 'PUT', body: payload });
}

export function publishTemplate(
  templateId: string,
  versionId?: string,
): Promise<TemplateDetail> {
  return apiRequest(`/api/v1/templates/${templateId}/publish`, {
    method: 'POST',
    body: versionId ? { versionId } : {},
  });
}

export function archiveTemplate(templateId: string): Promise<TemplateDetail> {
  return apiRequest(`/api/v1/templates/${templateId}/archive`, { method: 'POST' });
}

export function recordTemplateUsage(
  templateId: string,
  usageType: TemplateUsageType,
): Promise<TemplateUsageResult> {
  return apiRequest(`/api/v1/templates/${templateId}/usage`, {
    method: 'POST',
    body: { usageType },
  });
}

export const templateStatusLabels: Record<TemplateStatus, string> = {
  DRAFT: 'Draft',
  PUBLISHED: 'Dipublikasikan',
  ARCHIVED: 'Diarsipkan',
};

export const templateUsageLabels: Record<TemplateUsageType, string> = {
  VIEW: 'Dibuka',
  DOWNLOAD: 'Diunduh',
  COPY: 'Disalin',
};

export const TEMPLATE_STATUS_OPTIONS: { value: TemplateStatus; label: string }[] = [
  { value: 'PUBLISHED', label: templateStatusLabels.PUBLISHED },
  { value: 'DRAFT', label: templateStatusLabels.DRAFT },
  { value: 'ARCHIVED', label: templateStatusLabels.ARCHIVED },
];
