/**
 * axiosConfig.test.ts
 * Tests: Normal | Boundary | Exception Handling
 *
 * Covers the request interceptor (token injection) and the response
 * interceptor (token refresh on 401, redirect on missing token).
 */
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => null),
    setToken: vi.fn(),
    clearAll: vi.fn(),
  },
}));

import tokenService from '../../core/tokenService';

let mock: MockAdapter;

beforeEach(() => {
  mock = new MockAdapter(api);
  vi.clearAllMocks();
  Object.defineProperty(window, 'location', {
    value: { href: '' },
    writable: true,
  });
});

afterEach(() => mock.restore());

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('axiosConfig – Request Interceptor (Normal)', () => {
  it('attaches Authorization header when a token is present', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('my-token');
    mock.onGet('/test').reply((config) => {
      expect(config.headers?.Authorization).toBe('Bearer my-token');
      return [200, { ok: true }];
    });
    const res = await api.get('/test');
    expect(res.status).toBe(200);
  });

  it('does not attach Authorization header when token is null', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue(null);
    mock.onGet('/test').reply((config) => {
      expect(config.headers?.Authorization).toBeUndefined();
      return [200, {}];
    });
    const res = await api.get('/test');
    expect(res.status).toBe(200);
  });

  it('passes through a successful response without modification', async () => {
    mock.onGet('/ping').reply(200, { pong: true });
    const res = await api.get('/ping');
    expect(res.data.pong).toBe(true);
  });

  it('passes through a 201 response correctly', async () => {
    mock.onPost('/resource').reply(201, { id: 1 });
    const res = await api.post('/resource', {});
    expect(res.status).toBe(201);
    expect(res.data.id).toBe(1);
  });
});

describe('axiosConfig – Response Interceptor: 401 with valid token (Normal)', () => {
  it('refreshes the token and retries the original request on 401', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('old-token');

    // First call to /data returns 401; after refresh, returns 200
    let callCount = 0;
    mock.onGet('/data').reply(() => {
      callCount++;
      return callCount === 1 ? [401, {}] : [200, { result: 'ok' }];
    });
    mock.onPost('/auth/refresh').reply(200, { token: 'new-token' });

    const res = await api.get('/data');
    expect(res.status).toBe(200);
    expect(res.data.result).toBe('ok');
    expect(tokenService.setToken).toHaveBeenCalledWith('new-token');
  });

  it('also handles accessToken field in the refresh response', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('old-token');

    let callCount = 0;
    mock.onGet('/resource').reply(() => {
      callCount++;
      return callCount === 1 ? [401, {}] : [200, { data: 'value' }];
    });
    mock.onPost('/auth/refresh').reply(200, { accessToken: 'access-refreshed' });

    const res = await api.get('/resource');
    expect(res.status).toBe(200);
    expect(tokenService.setToken).toHaveBeenCalledWith('access-refreshed');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('axiosConfig – Boundary', () => {
  it('does not trigger refresh interceptor for 401 on /auth/login', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onPost('/auth/login').reply(401, { message: 'Bad credentials' });

    await expect(api.post('/auth/login', {})).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });

  it('does not trigger refresh interceptor for 401 on /auth/register', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onPost('/auth/register').reply(401);

    await expect(api.post('/auth/register', {})).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });

  it('does not trigger refresh interceptor for 401 on /auth/refresh', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onPost('/auth/refresh').reply(401);

    await expect(api.post('/auth/refresh', {})).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });

  it('does not retry on non-401 errors (e.g. 400)', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onGet('/bad').reply(400, { message: 'Bad Request' });

    await expect(api.get('/bad')).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });

  it('does not retry on 403 errors', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onGet('/forbidden').reply(403);

    await expect(api.get('/forbidden')).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });

  it('does not retry on 500 server errors', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('tok');
    mock.onGet('/crash').reply(500);

    await expect(api.get('/crash')).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('axiosConfig – Exception Handling', () => {
  it('redirects to /login and clears session when 401 occurs with no stored token', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue(null);
    mock.onGet('/secure').reply(401);

    await expect(api.get('/secure')).rejects.toThrow();
    expect(tokenService.clearAll).toHaveBeenCalled();
    expect(window.location.href).toBe('/login');
  });

  it('does not call setToken when a non-401 error occurs during the request', async () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('some-token');
    mock.onDelete('/resource').reply(422, { message: 'Unprocessable' });

    await expect(api.delete('/resource')).rejects.toThrow();
    expect(tokenService.setToken).not.toHaveBeenCalled();
    expect(tokenService.clearAll).not.toHaveBeenCalled();
  });

  it('request interceptor propagates error when token getter throws', async () => {
    // The axiosConfig request interceptor calls tokenService.getToken() and
    // does NOT swallow exceptions — the throw propagates as a rejected request.
    // This documents the actual behaviour of the interceptor.
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockImplementationOnce(() => {
      throw new Error('Storage unavailable');
    });
    mock.onGet('/safe').reply(200, { data: 'ok' });

    await expect(api.get('/safe')).rejects.toThrow('Storage unavailable');
  });
});
