import { apiRequest } from '../../shared/api/client';
import type {
  DocumentCategory,
  DocumentClassification,
  DocumentTag,
  DocumentType,
  FilePolicy,
  Position,
} from '../../shared/api/types';

export const listCategories = (): Promise<DocumentCategory[]> =>
  apiRequest('/api/v1/admin/master-data/categories');
export const createCategory = (body: {
  parentId?: string | null;
  categoryCode: string;
  categoryName: string;
  description?: string;
}): Promise<DocumentCategory> =>
  apiRequest('/api/v1/admin/master-data/categories', { method: 'POST', body });
export const updateCategory = (
  id: string,
  body: { parentId?: string | null; categoryName: string; description?: string; active: boolean },
): Promise<DocumentCategory> =>
  apiRequest(`/api/v1/admin/master-data/categories/${id}`, { method: 'PUT', body });

export const listTypes = (): Promise<DocumentType[]> =>
  apiRequest('/api/v1/admin/master-data/document-types');
export const createType = (body: {
  typeCode: string;
  typeName: string;
  description?: string;
}): Promise<DocumentType> =>
  apiRequest('/api/v1/admin/master-data/document-types', { method: 'POST', body });
export const updateType = (
  id: string,
  body: { typeName: string; description?: string; active: boolean },
): Promise<DocumentType> =>
  apiRequest(`/api/v1/admin/master-data/document-types/${id}`, { method: 'PUT', body });

export const listClassifications = (): Promise<DocumentClassification[]> =>
  apiRequest('/api/v1/admin/master-data/classifications');
export const createClassification = (body: {
  classificationCode: string;
  classificationName: string;
  sensitivityRank: number;
  description?: string;
}): Promise<DocumentClassification> =>
  apiRequest('/api/v1/admin/master-data/classifications', { method: 'POST', body });
export const updateClassification = (
  id: string,
  body: {
    classificationName: string;
    sensitivityRank: number;
    description?: string;
    active: boolean;
  },
): Promise<DocumentClassification> =>
  apiRequest(`/api/v1/admin/master-data/classifications/${id}`, { method: 'PUT', body });

export const listTags = (): Promise<DocumentTag[]> =>
  apiRequest('/api/v1/admin/master-data/tags');
export const createTag = (body: { tagCode: string; tagName: string }): Promise<DocumentTag> =>
  apiRequest('/api/v1/admin/master-data/tags', { method: 'POST', body });
export const updateTag = (
  id: string,
  body: { tagName: string; active: boolean },
): Promise<DocumentTag> =>
  apiRequest(`/api/v1/admin/master-data/tags/${id}`, { method: 'PUT', body });

export const listPositions = (): Promise<Position[]> =>
  apiRequest('/api/v1/admin/master-data/positions');
export const createPosition = (body: {
  positionCode: string;
  positionName: string;
  description?: string;
}): Promise<Position> =>
  apiRequest('/api/v1/admin/master-data/positions', { method: 'POST', body });
export const updatePosition = (
  id: string,
  body: { positionName: string; description?: string; active: boolean },
): Promise<Position> =>
  apiRequest(`/api/v1/admin/master-data/positions/${id}`, { method: 'PUT', body });

export const listFilePolicies = (): Promise<FilePolicy[]> =>
  apiRequest('/api/v1/admin/master-data/file-policies');
export const updateFilePolicy = (
  id: string,
  body: { allowedExtensions: string[]; allowedMimeTypes: string[]; maxFileSizeBytes: number },
): Promise<FilePolicy> =>
  apiRequest(`/api/v1/admin/master-data/file-policies/${id}`, { method: 'PUT', body });
