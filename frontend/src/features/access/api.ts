import { apiRequest } from '../../shared/api/client';
import type {
  AccessDirectory,
  AccessGrant,
  AccessResourceType,
  AccessSummary,
  AclEffect,
  PrincipalType,
} from '../../shared/api/types';

export interface AccessTarget {
  type: AccessResourceType;
  workspaceId?: string;
  folderId?: string;
  documentId?: string;
}

export function accessBasePath(target: AccessTarget): string {
  switch (target.type) {
    case 'WORKSPACE':
      return `/api/v1/workspaces/${target.workspaceId}/access`;
    case 'FOLDER':
      return `/api/v1/workspaces/${target.workspaceId}/folders/${target.folderId}/access`;
    case 'DOCUMENT':
      return `/api/v1/documents/${target.documentId}/access`;
  }
}

export function getAccessSummary(target: AccessTarget): Promise<AccessSummary> {
  return apiRequest(`${accessBasePath(target)}?includeHistory=true`);
}

export interface CreateGrantPayload {
  principalType: PrincipalType;
  principalId: string;
  permissionCode: string;
  effect: AclEffect;
  includeDescendants?: boolean;
  expiresAt?: string;
}

export function createAccessGrant(
  target: AccessTarget,
  payload: CreateGrantPayload,
): Promise<AccessGrant> {
  return apiRequest(accessBasePath(target), { method: 'POST', body: payload });
}

export function revokeAccessGrant(target: AccessTarget, grantId: string): Promise<void> {
  return apiRequest(`${accessBasePath(target)}/${grantId}`, { method: 'DELETE' });
}

export function searchAccessDirectory(search: string): Promise<AccessDirectory> {
  const query = search.trim() ? `?search=${encodeURIComponent(search.trim())}` : '';
  return apiRequest(`/api/v1/access-directory${query}`);
}

export interface PermissionOption {
  code: string;
  label: string;
}

const DOCUMENT_PERMISSIONS: PermissionOption[] = [
  { code: 'document.view', label: 'Lihat detail dokumen' },
  { code: 'document.preview', label: 'Preview konten' },
  { code: 'document.download', label: 'Unduh konten' },
  { code: 'document.create_version', label: 'Tambah versi' },
  { code: 'document.update_metadata', label: 'Ubah metadata' },
  { code: 'document.archive', label: 'Arsipkan dokumen' },
  { code: 'document.restore', label: 'Pulihkan dokumen' },
  { code: 'document.share', label: 'Distribusikan dokumen' },
];

/**
 * Permissions a manual grant can meaningfully change. Access-management capability
 * itself is never granted through ACL; it stays a role capability.
 */
export function permissionOptionsFor(type: AccessResourceType): PermissionOption[] {
  switch (type) {
    case 'DOCUMENT':
      return DOCUMENT_PERMISSIONS;
    case 'FOLDER':
      return [{ code: 'folder.view', label: 'Lihat folder' }, ...DOCUMENT_PERMISSIONS];
    case 'WORKSPACE':
      return [
        { code: 'workspace.view', label: 'Lihat workspace' },
        { code: 'folder.view', label: 'Lihat folder' },
        ...DOCUMENT_PERMISSIONS,
      ];
  }
}

export function toIsoExpiry(value: string): string | undefined {
  if (!value) {
    return undefined;
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    throw new Error('Waktu kedaluwarsa tidak valid.');
  }
  return parsed.toISOString();
}

export function isFutureExpiry(value: string): boolean {
  if (!value) {
    return true;
  }
  const parsed = new Date(value);
  return !Number.isNaN(parsed.getTime()) && parsed.getTime() > Date.now();
}

export const principalTypeLabels: Record<PrincipalType, string> = {
  USER: 'User',
  ROLE: 'Role',
  ORGANIZATION_UNIT: 'Unit organisasi',
};

export const effectLabels: Record<AclEffect, string> = {
  ALLOW: 'Izinkan',
  DENY: 'Tolak',
};

export const grantSourceLabels: Record<string, string> = {
  MANUAL: 'Manual',
  REQUEST: 'Permintaan',
  DISTRIBUTION: 'Distribusi',
  SYSTEM: 'Sistem',
};
