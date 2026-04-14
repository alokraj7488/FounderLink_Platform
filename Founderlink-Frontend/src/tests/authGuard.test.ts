/**
 * authGuard.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { isTokenValid, clearAuth } from '../core/guards/authGuard';

// Helper: create a JWT with a given expiry (seconds since epoch)
const makeJwt = (expSeconds: number): string => {
  const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const payload = btoa(JSON.stringify({ sub: 'user1', exp: expSeconds }));
  return `${header}.${payload}.signature`;
};

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// 1. isTokenValid – Normal
// ─────────────────────────────────────────────────────────────────────────────
describe('isTokenValid – Normal', () => {
  it('returns true for a valid, future-expiry JWT', () => {
    const futureExp = Math.floor(Date.now() / 1000) + 3600; // 1 hour from now
    const token = makeJwt(futureExp);
    expect(isTokenValid(token)).toBe(true);
  });

  it('returns false for an expired JWT', () => {
    const pastExp = Math.floor(Date.now() / 1000) - 1; // 1 second ago
    const token = makeJwt(pastExp);
    expect(isTokenValid(token)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. isTokenValid – Boundary
// ─────────────────────────────────────────────────────────────────────────────
describe('isTokenValid – Boundary', () => {
  it('returns false for null token', () => {
    expect(isTokenValid(null)).toBe(false);
  });

  it('returns false for empty string token', () => {
    expect(isTokenValid('')).toBe(false);
  });

  it('returns false for token expiring exactly at current second', () => {
    const nowSeconds = Math.floor(Date.now() / 1000);
    const token = makeJwt(nowSeconds);
    // exp * 1000 === Date.now() (approximately) → not strictly greater, so false
    expect(isTokenValid(token)).toBe(false);
  });

  it('returns true for a token expiring far in the future', () => {
    const farFuture = Math.floor(Date.now() / 1000) + 9999999;
    expect(isTokenValid(makeJwt(farFuture))).toBe(true);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. isTokenValid – Exception Handling
// ─────────────────────────────────────────────────────────────────────────────
describe('isTokenValid – Exception Handling', () => {
  it('returns false for a malformed token (not three parts)', () => {
    expect(isTokenValid('not.a.valid.jwt.parts')).toBe(false);
  });

  it('returns false when payload is not valid base64', () => {
    expect(isTokenValid('header.!!!invalid_base64!!!.sig')).toBe(false);
  });

  it('returns false when payload is valid base64 but not JSON', () => {
    const badPayload = btoa('not-json-at-all');
    expect(isTokenValid(`header.${badPayload}.sig`)).toBe(false);
  });

  it('returns false for a token with missing exp field', () => {
    const payload = btoa(JSON.stringify({ sub: 'user1' })); // no exp
    expect(isTokenValid(`header.${payload}.sig`)).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 4. clearAuth
// ─────────────────────────────────────────────────────────────────────────────
describe('clearAuth', () => {
  it('Normal: clears token and user from localStorage', () => {
    localStorage.setItem('token', 'some-token');
    localStorage.setItem('user', JSON.stringify({ userId: 1 }));
    clearAuth();
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  it('Boundary: calling clearAuth on empty storage does not throw', () => {
    expect(() => clearAuth()).not.toThrow();
  });

  it('Exception: clearAuth is safe to call multiple times', () => {
    clearAuth();
    expect(() => clearAuth()).not.toThrow();
  });
});
