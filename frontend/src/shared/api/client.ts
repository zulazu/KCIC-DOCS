import type { ApiErrorPayload, FieldErrorItem } from './types';

const CSRF_ENDPOINT = '/api/v1/auth/csrf';
const UNAUTHENTICATED_EVENT = 'kcic:unauthenticated';

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldErrorItem[];
  readonly requestId: string | null;

  constructor(status: number, payload: ApiErrorPayload) {
    super(payload.message);
    this.name = 'ApiError';
    this.status = status;
    this.code = payload.code;
    this.fieldErrors = payload.fieldErrors ?? [];
    this.requestId = payload.requestId ?? null;
  }
}

interface CsrfResponse {
  headerName: string;
  parameterName: string;
  token: string;
}

let csrfToken: string | null = null;
let csrfHeaderName = 'X-XSRF-TOKEN';
let csrfInFlight: Promise<void> | null = null;

async function loadCsrfToken(): Promise<void> {
  const response = await fetch(CSRF_ENDPOINT, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: 'application/json' },
  });
  if (!response.ok) {
    throw new ApiError(response.status, {
      code: 'CSRF_UNAVAILABLE',
      message: 'Tidak dapat menyiapkan token keamanan. Muat ulang halaman.',
    });
  }
  const payload = (await response.json()) as CsrfResponse;
  csrfToken = payload.token;
  csrfHeaderName = payload.headerName || csrfHeaderName;
}

export async function ensureCsrfToken(): Promise<void> {
  if (csrfToken) {
    return;
  }
  csrfInFlight ??= loadCsrfToken().finally(() => {
    csrfInFlight = null;
  });
  await csrfInFlight;
}

export function resetCsrfToken(): void {
  csrfToken = null;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
}

async function parseError(response: Response): Promise<ApiError> {
  let payload: ApiErrorPayload = {
    code: 'REQUEST_FAILED',
    message: 'Permintaan tidak dapat diproses. Coba lagi.',
  };
  try {
    const parsed = (await response.json()) as Partial<ApiErrorPayload>;
    if (parsed && typeof parsed.code === 'string' && typeof parsed.message === 'string') {
      payload = parsed as ApiErrorPayload;
    }
  } catch {
    // Non-JSON error responses fall back to a safe generic message.
  }
  return new ApiError(response.status, payload);
}

async function execute(path: string, options: RequestOptions): Promise<Response> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = {
    Accept: 'application/json',
    ...options.headers,
  };
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }
  if (method !== 'GET') {
    await ensureCsrfToken();
    if (csrfToken) {
      headers[csrfHeaderName] = csrfToken;
    }
  }
  return fetch(path, {
    method,
    credentials: 'include',
    headers,
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  });
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  let response = await execute(path, options);

  if (response.status === 403) {
    const error = await parseError(response.clone());
    if (error.code === 'CSRF') {
      resetCsrfToken();
      response = await execute(path, options);
    }
  }

  if (!response.ok) {
    const error = await parseError(response);
    if (response.status === 401) {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT));
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }

  const text = await response.text();
  return (text.length === 0 ? undefined : JSON.parse(text)) as T;
}

export function onUnauthenticated(listener: () => void): () => void {
  window.addEventListener(UNAUTHENTICATED_EVENT, listener);
  return () => window.removeEventListener(UNAUTHENTICATED_EVENT, listener);
}

export async function mutationHeaders(): Promise<Record<string, string>> {
  await ensureCsrfToken();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (csrfToken) {
    headers[csrfHeaderName] = csrfToken;
  }
  return headers;
}

export interface BlobContent {
  blob: Blob;
  filename: string | null;
  contentType: string | null;
}

function filenameFromDisposition(disposition: string | null): string | null {
  if (!disposition) {
    return null;
  }
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition);
  if (encoded?.[1]) {
    try {
      return decodeURIComponent(encoded[1]);
    } catch {
      return encoded[1];
    }
  }
  const plain = /filename="?([^";]+)"?/i.exec(disposition);
  return plain?.[1] ?? null;
}

export async function apiBlob(path: string): Promise<BlobContent> {
  const response = await fetch(path, {
    method: 'GET',
    credentials: 'include',
    headers: { Accept: '*/*' },
  });
  if (!response.ok) {
    const error = await parseError(response);
    if (response.status === 401) {
      window.dispatchEvent(new Event(UNAUTHENTICATED_EVENT));
    }
    throw error;
  }
  return {
    blob: await response.blob(),
    filename: filenameFromDisposition(response.headers.get('Content-Disposition')),
    contentType: response.headers.get('Content-Type'),
  };
}

export function apiErrorFromXhr(status: number, body: string): ApiError {
  try {
    const parsed = JSON.parse(body) as Partial<ApiErrorPayload>;
    if (parsed && typeof parsed.code === 'string' && typeof parsed.message === 'string') {
      return new ApiError(status, parsed as ApiErrorPayload);
    }
  } catch {
    // Non-JSON upload failures fall back to a safe generic message.
  }
  return new ApiError(status, {
    code: 'REQUEST_FAILED',
    message: 'Permintaan tidak dapat diproses. Coba lagi.',
  });
}

export function fieldErrorOf(error: unknown): string | null {
  return error instanceof ApiError ? error.message : null;
}

export function saveBlob(blob: Blob, filename: string | null): void {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = filename ?? 'unduhan';
  document.body.appendChild(anchor);
  anchor.click();
  anchor.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 2000);
}
