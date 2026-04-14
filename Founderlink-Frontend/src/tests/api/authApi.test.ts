/**
 * authApi.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import MockAdapter from 'axios-mock-adapter';
import api from '../../core/api/axiosConfig';
import { login, register, refreshToken } from '../../core/api/authApi';

let mock: MockAdapter;

beforeEach(() => { mock = new MockAdapter(api); });
afterEach(() => { mock.restore(); });

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('authApi – Normal', () => {
  it('login: POSTs credentials and returns LoginResponse', async () => {
    const payload = {
      status: 200,
      message: 'Success',
      data: { token: 'jwt-abc', refreshToken: 'ref-xyz', userId: 1, role: 'ROLE_FOUNDER', email: 'a@b.com', name: 'Alice' },
    };
    mock.onPost('/auth/login').reply(200, payload);

    const res = await login({ email: 'a@b.com', password: 'pass123' });

    expect(res.status).toBe(200);
    expect(res.data.data.token).toBe('jwt-abc');
    expect(res.data.data.role).toBe('ROLE_FOUNDER');
  });

  it('register: POSTs new user data and returns 201', async () => {
    mock.onPost('/auth/register').reply(201);

    const res = await register({
      name: 'Bob',
      email: 'bob@test.com',
      password: 'secure99',
      role: 'ROLE_INVESTOR',
    });

    expect(res.status).toBe(201);
  });

  it('refreshToken: POSTs empty body and receives a new token', async () => {
    mock.onPost('/auth/refresh').reply(200, { token: 'new-jwt-token' });

    const res = await refreshToken({});

    expect(res.status).toBe(200);
    expect(res.data.token).toBe('new-jwt-token');
  });

  it('refreshToken: also handles accessToken field in response', async () => {
    mock.onPost('/auth/refresh').reply(200, { accessToken: 'access-token-xyz' });

    const res = await refreshToken({});

    expect(res.data.accessToken).toBe('access-token-xyz');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('authApi – Boundary', () => {
  it('login: sends the exact email and password in the request body', async () => {
    mock.onPost('/auth/login', { email: 'exact@test.com', password: '123456' }).reply(200, {
      status: 200, message: 'OK', data: { token: 't', refreshToken: 'r', userId: 2, role: 'ROLE_FOUNDER', email: 'exact@test.com', name: 'X' },
    });

    const res = await login({ email: 'exact@test.com', password: '123456' });
    expect(res.status).toBe(200);
  });

  it('register: accepts all valid role values', async () => {
    for (const role of ['ROLE_FOUNDER', 'ROLE_INVESTOR', 'ROLE_COFOUNDER'] as const) {
      mock.onPost('/auth/register').reply(201);
      const res = await register({ name: 'Test', email: 't@t.com', password: 'pass12', role });
      expect(res.status).toBe(201);
    }
  });

  it('refreshToken: passes additional payload fields without error', async () => {
    mock.onPost('/auth/refresh').reply(200, { token: 'tok' });
    const res = await refreshToken({ extraField: 'value' });
    expect(res.data.token).toBe('tok');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('authApi – Exception Handling', () => {
  it('login: rejects and throws on 401 Unauthorized', async () => {
    mock.onPost('/auth/login').reply(401, { message: 'Bad credentials' });
    await expect(login({ email: 'x@y.com', password: 'wrong' })).rejects.toThrow();
  });

  it('register: rejects on 409 Conflict (email already exists)', async () => {
    mock.onPost('/auth/register').reply(409, { message: 'Email already in use' });
    await expect(
      register({ name: 'Bob', email: 'existing@test.com', password: 'pass', role: 'ROLE_INVESTOR' })
    ).rejects.toThrow();
  });

  it('login: rejects on network error (no response)', async () => {
    mock.onPost('/auth/login').networkError();
    await expect(login({ email: 'a@b.com', password: 'pass' })).rejects.toThrow();
  });

  it('refreshToken: rejects on 403 Forbidden', async () => {
    mock.onPost('/auth/refresh').reply(403, { message: 'Token expired' });
    await expect(refreshToken({})).rejects.toThrow();
  });

  it('refreshToken: rejects on 500 Internal Server Error', async () => {
    mock.onPost('/auth/refresh').reply(500);
    await expect(refreshToken({})).rejects.toThrow();
  });
});
