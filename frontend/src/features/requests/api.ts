import { apiErrorFromXhr, apiRequest, mutationHeaders } from '../../shared/api/client';
import type {
  FulfillmentType,
  PageResponse,
  RequestDetail,
  RequestScope,
  RequestSummary,
} from '../../shared/api/types';

export interface RequestSearchParams {
  scope: RequestScope;
  status?: string;
  type?: string;
  keyword?: string;
  page?: number;
  size?: number;
}

export function listRequests(params: RequestSearchParams): Promise<PageResponse<RequestSummary>> {
  const query = new URLSearchParams({ scope: params.scope });
  for (const [key, value] of Object.entries(params)) {
    if (key === 'scope' || value === undefined || value === null || value === '') {
      continue;
    }
    query.set(key, String(value));
  }
  return apiRequest(`/api/v1/requests?${query.toString()}`);
}

export function getRequest(requestId: string): Promise<RequestDetail> {
  return apiRequest(`/api/v1/requests/${requestId}`);
}

export interface CreateRequestPayload {
  requestType: string;
  title: string;
  description?: string;
  reason: string;
  targetDocumentId?: string | null;
  targetOrganizationUnitId?: string | null;
  neededByDate?: string | null;
}

export function createRequest(payload: CreateRequestPayload): Promise<RequestDetail> {
  return apiRequest('/api/v1/requests', { method: 'POST', body: payload });
}

function postAction(requestId: string, action: string, comment?: string): Promise<RequestDetail> {
  return apiRequest(`/api/v1/requests/${requestId}/${action}`, {
    method: 'POST',
    body: comment === undefined ? undefined : { comment },
  });
}

export const startReview = (requestId: string): Promise<RequestDetail> =>
  postAction(requestId, 'start-review');

export const approveRequest = (requestId: string, comment?: string): Promise<RequestDetail> =>
  postAction(requestId, 'approve', comment);

export const rejectRequest = (requestId: string, comment: string): Promise<RequestDetail> =>
  postAction(requestId, 'reject', comment);

export const cancelRequest = (requestId: string, comment?: string): Promise<RequestDetail> =>
  postAction(requestId, 'cancel', comment);

export const closeRequest = (requestId: string, comment?: string): Promise<RequestDetail> =>
  postAction(requestId, 'close', comment);

export interface FulfillPayload {
  fulfillmentType: FulfillmentType;
  documentIds?: string[];
  externalUrl?: string;
  linkTitle?: string;
  note?: string;
}

export function fulfillRequest(requestId: string, payload: FulfillPayload): Promise<RequestDetail> {
  return apiRequest(`/api/v1/requests/${requestId}/fulfill`, { method: 'POST', body: payload });
}

export async function uploadFulfillment(
  requestId: string,
  documentId: string,
  file: File,
  meta: { note?: string; versionLabel?: string },
  onProgress?: (percent: number) => void,
): Promise<RequestDetail> {
  const headers = await mutationHeaders();
  return new Promise<RequestDetail>((resolve, reject) => {
    const request = new XMLHttpRequest();
    request.open('POST', `/api/v1/requests/${requestId}/fulfill/upload`);
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
          resolve(JSON.parse(request.responseText) as RequestDetail);
        } catch {
          reject(apiErrorFromXhr(request.status, request.responseText));
        }
        return;
      }
      reject(apiErrorFromXhr(request.status, request.responseText));
    };
    request.onerror = () =>
      reject(
        apiErrorFromXhr(
          0,
          JSON.stringify({
            code: 'NETWORK_ERROR',
            message: 'Unggahan gagal karena masalah jaringan. Coba lagi.',
          }),
        ),
      );
    const form = new FormData();
    form.append('documentId', documentId);
    form.append('file', file);
    if (meta.note) {
      form.append('note', meta.note);
    }
    if (meta.versionLabel) {
      form.append('versionLabel', meta.versionLabel);
    }
    request.send(form);
  });
}
