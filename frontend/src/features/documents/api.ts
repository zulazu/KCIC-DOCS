import { apiBlob, apiErrorFromXhr, apiRequest, mutationHeaders } from '../../shared/api/client';
import type {
  DocumentActivity,
  DocumentCategory,
  DocumentClassification,
  DocumentDetail,
  DocumentPermissions,
  DocumentSummary,
  DocumentTag,
  DocumentType,
  DocumentVersion,
  ExternalLinkInfo,
  FilePolicy,
  PageResponse,
} from '../../shared/api/types';

export interface DocumentSearchParams {
  workspaceId?: string;
  keyword?: string;
  categoryId?: string;
  documentTypeId?: string;
  classificationId?: string;
  status?: string;
  sort?: string;
  includeArchived?: boolean;
  page?: number;
  size?: number;
}

export function listDocuments(params: DocumentSearchParams): Promise<PageResponse<DocumentSummary>> {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') {
      continue;
    }
    query.set(key, String(value));
  }
  const suffix = query.toString();
  return apiRequest(`/api/v1/documents${suffix ? `?${suffix}` : ''}`);
}

export function listFavorites(): Promise<DocumentSummary[]> {
  return apiRequest('/api/v1/documents/favorites');
}

export function getDocument(documentId: string): Promise<DocumentDetail> {
  return apiRequest(`/api/v1/documents/${documentId}`);
}

export interface DocumentPayload {
  documentCode?: string;
  title: string;
  description?: string;
  folderId?: string | null;
  categoryId: string;
  documentTypeId: string;
  classificationId: string;
  ownerUserId?: string | null;
  ownerOrganizationUnitId?: string | null;
  tagIds?: string[];
  effectiveDate?: string | null;
  reviewDate?: string | null;
  expiryDate?: string | null;
  ownerChangeReason?: string;
}

export function createDocument(
  workspaceId: string,
  payload: DocumentPayload,
): Promise<DocumentDetail> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}/documents`, {
    method: 'POST',
    body: payload,
  });
}

export function updateDocument(documentId: string, payload: DocumentPayload): Promise<DocumentDetail> {
  return apiRequest(`/api/v1/documents/${documentId}`, { method: 'PUT', body: payload });
}

export function changeDocumentStatus(
  documentId: string,
  action: 'activate' | 'archive' | 'restore',
): Promise<DocumentDetail> {
  return apiRequest(`/api/v1/documents/${documentId}/${action}`, { method: 'POST' });
}

export function listVersions(documentId: string): Promise<DocumentVersion[]> {
  return apiRequest(`/api/v1/documents/${documentId}/versions`);
}

export function registerExternalLink(
  documentId: string,
  payload: { url: string; linkTitle?: string; versionLabel?: string; changeSummary?: string },
): Promise<DocumentVersion> {
  return apiRequest(`/api/v1/documents/${documentId}/versions/external-link`, {
    method: 'POST',
    body: payload,
  });
}

export function promoteVersion(documentId: string, versionId: string): Promise<DocumentVersion> {
  return apiRequest(`/api/v1/documents/${documentId}/versions/${versionId}/promote`, {
    method: 'POST',
  });
}

export function openExternalLink(documentId: string, versionId: string): Promise<ExternalLinkInfo> {
  return apiRequest(`/api/v1/documents/${documentId}/versions/${versionId}/external-link`);
}

export interface VersionContent {
  blob: Blob;
  filename: string | null;
  contentType: string | null;
}

export async function fetchVersionContent(
  documentId: string,
  versionId: string,
  download: boolean,
): Promise<VersionContent> {
  const content = await apiBlob(
    `/api/v1/documents/${documentId}/versions/${versionId}/content?download=${download}`,
  );
  return content;
}

export function listActivity(documentId: string): Promise<DocumentActivity[]> {
  return apiRequest(`/api/v1/documents/${documentId}/activity`);
}

export function addFavorite(documentId: string): Promise<void> {
  return apiRequest(`/api/v1/documents/${documentId}/favorite`, { method: 'PUT' });
}

export function removeFavorite(documentId: string): Promise<void> {
  return apiRequest(`/api/v1/documents/${documentId}/favorite`, { method: 'DELETE' });
}

export function getFilePolicy(): Promise<FilePolicy> {
  return apiRequest('/api/v1/master-data/file-policy');
}

export const listDocumentCategories = (): Promise<DocumentCategory[]> =>
  apiRequest('/api/v1/master-data/categories');
export const listDocumentTypes = (): Promise<DocumentType[]> =>
  apiRequest('/api/v1/master-data/document-types');
export const listDocumentClassifications = (): Promise<DocumentClassification[]> =>
  apiRequest('/api/v1/master-data/classifications');
export const listDocumentTags = (): Promise<DocumentTag[]> =>
  apiRequest('/api/v1/master-data/tags');

export interface UploadMeta {
  versionLabel?: string;
  changeSummary?: string;
}

export async function uploadVersion(
  documentId: string,
  file: File,
  meta: UploadMeta,
  onProgress?: (percent: number) => void,
): Promise<DocumentVersion> {
  const headers = await mutationHeaders();
  return new Promise<DocumentVersion>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('POST', `/api/v1/documents/${documentId}/versions`);
    request.withCredentials = true;
    for (const [name, value] of Object.entries(headers)) {
      request.setRequestHeader(name, value);
    }
    request.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };
    request.onload = () => {
      if (request.status >= 200 && request.status < 300) {
        try {
          resolve(JSON.parse(request.responseText) as DocumentVersion);
        } catch {
          reject(apiErrorFromXhr(request.status, request.responseText));
        }
        return;
      }
      reject(apiErrorFromXhr(request.status, request.responseText));
    };
    request.onerror = () =>
      reject(apiErrorFromXhr(0, JSON.stringify({
        code: 'NETWORK_ERROR',
        message: 'Unggahan gagal karena masalah jaringan. Coba lagi.',
      })));
    const form = new FormData();
    form.append('file', file);
    if (meta.versionLabel) {
      form.append('versionLabel', meta.versionLabel);
    }
    if (meta.changeSummary) {
      form.append('changeSummary', meta.changeSummary);
    }
    request.send(form);
  });
}

export function saveBlob(blob: Blob, filename: string | null): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename ?? 'dokumen';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
}

export function canPreviewInline(contentType: string | null): boolean {
  if (!contentType) {
    return false;
  }
  return (
    contentType === 'application/pdf' ||
    contentType === 'text/plain' ||
    contentType === 'text/csv' ||
    contentType.startsWith('image/')
  );
}

export type { DocumentPermissions };
