import { beforeEach, describe, expect, it, vi } from 'vitest';

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('apiRequest', () => {
  beforeEach(() => {
    vi.resetModules();
    vi.unstubAllGlobals();
  });

  it('sends CSRF header and credentials for mutating requests', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token-1' }),
      )
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    const { apiRequest } = await import('./client');
    const result = await apiRequest<{ ok: boolean }>('/api/v1/example', {
      method: 'POST',
      body: { value: 1 },
    });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(2);
    const [csrfUrl, csrfInit] = fetchMock.mock.calls[0] ?? [];
    expect(csrfUrl).toBe('/api/v1/auth/csrf');
    expect(csrfInit?.credentials).toBe('include');
    const [, postInit] = fetchMock.mock.calls[1] ?? [];
    const headers = new Headers(postInit?.headers);
    expect(headers.get('X-XSRF-TOKEN')).toBe('token-1');
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(postInit?.credentials).toBe('include');
  });

  it('maps API error payloads to ApiError with field errors', async () => {
    const fetchMock = vi.fn<typeof fetch>().mockResolvedValueOnce(
      jsonResponse(
        {
          code: 'VALIDATION_ERROR',
          message: 'Input tidak valid.',
          fieldErrors: [{ field: 'username', message: 'Sudah digunakan.' }],
          requestId: 'req-1',
        },
        400,
      ),
    );
    vi.stubGlobal('fetch', fetchMock);

    const { apiRequest, ApiError } = await import('./client');
    await expect(apiRequest('/api/v1/admin/users')).rejects.toMatchObject({
      name: ApiError.name,
      status: 400,
      code: 'VALIDATION_ERROR',
      requestId: 'req-1',
      fieldErrors: [{ field: 'username', message: 'Sudah digunakan.' }],
    });
  });

  it('refreshes CSRF token once when the backend rejects the token', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'stale' }),
      )
      .mockResolvedValueOnce(jsonResponse({ code: 'CSRF', message: 'Token tidak valid.' }, 403))
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'fresh' }),
      )
      .mockResolvedValueOnce(jsonResponse({ ok: true }));
    vi.stubGlobal('fetch', fetchMock);

    const { apiRequest } = await import('./client');
    const result = await apiRequest<{ ok: boolean }>('/api/v1/example', { method: 'POST' });

    expect(result).toEqual({ ok: true });
    expect(fetchMock).toHaveBeenCalledTimes(4);
    const [, retryInit] = fetchMock.mock.calls[3] ?? [];
    expect(new Headers(retryInit?.headers).get('X-XSRF-TOKEN')).toBe('fresh');
  });

  it('returns undefined for 204 responses', async () => {
    const fetchMock = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        jsonResponse({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'token-2' }),
      )
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    vi.stubGlobal('fetch', fetchMock);

    const { apiRequest } = await import('./client');
    await expect(
      apiRequest('/api/v1/example', { method: 'DELETE' }),
    ).resolves.toBeUndefined();
  });
});
