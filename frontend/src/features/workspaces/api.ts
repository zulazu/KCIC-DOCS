import { apiRequest } from '../../shared/api/client';
import type {
  OrganizationUnit,
  RepositoryFolder,
  Workspace,
  WorkspaceStatus,
} from '../../shared/api/types';

export function listActiveUnits(): Promise<OrganizationUnit[]> {
  return apiRequest('/api/v1/organization-units');
}

function archivedFlag(includeArchived: boolean): string {
  return includeArchived ? '?includeArchived=true' : '';
}

export function listWorkspaces(includeArchived: boolean): Promise<Workspace[]> {
  return apiRequest(`/api/v1/workspaces${archivedFlag(includeArchived)}`);
}

export function getWorkspace(workspaceId: string): Promise<Workspace> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}`);
}

export interface WorkspacePayload {
  workspaceName: string;
  description?: string;
  ownerOrganizationUnitId: string;
  ownerUserId?: string | null;
  status?: WorkspaceStatus;
}

export function createWorkspace(payload: {
  workspaceCode: string;
  workspaceName: string;
  description?: string;
  ownerOrganizationUnitId: string;
}): Promise<Workspace> {
  return apiRequest('/api/v1/workspaces', {
    method: 'POST',
    body: { ...payload, ownerUserId: null },
  });
}

export function updateWorkspace(workspaceId: string, payload: WorkspacePayload): Promise<Workspace> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}`, { method: 'PUT', body: payload });
}

export function listFolders(
  workspaceId: string,
  includeArchived: boolean,
): Promise<RepositoryFolder[]> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}/folders${archivedFlag(includeArchived)}`);
}

export interface FolderPayload {
  parentFolderId: string | null;
  folderName: string;
  description?: string;
}

export function createFolder(workspaceId: string, payload: FolderPayload): Promise<RepositoryFolder> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}/folders`, {
    method: 'POST',
    body: payload,
  });
}

export function updateFolder(
  workspaceId: string,
  folderId: string,
  payload: FolderPayload,
): Promise<RepositoryFolder> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}/folders/${folderId}`, {
    method: 'PUT',
    body: payload,
  });
}

export function archiveFolder(
  workspaceId: string,
  folderId: string,
): Promise<RepositoryFolder> {
  return apiRequest(`/api/v1/workspaces/${workspaceId}/folders/${folderId}/archive`, {
    method: 'POST',
  });
}
