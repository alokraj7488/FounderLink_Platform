/**
 * tokenService.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, beforeEach, vi } from 'vitest';
import tokenService from '../core/tokenService';

const mockUser = { userId: 1, role: 'ROLE_FOUNDER', email: 'test@test.com', name: 'Test User' };
const MOCK_TOKEN = 'mock.jwt.token';

beforeEach(() => {
  localStorage.clear();
  vi.clearAllMocks();
});

// ─────────────────────────────────────────────────────────────────────────────
// 1. getToken / setToken / removeToken
// ─────────────────────────────────────────────────────────────────────────────
describe('tokenService – Token Operations', () => {
  // NORMAL
  it('setToken stores a token and getToken retrieves it', () => {
    tokenService.setToken(MOCK_TOKEN);
    expect(tokenService.getToken()).toBe(MOCK_TOKEN);
  });

  it('removeToken deletes the token from storage', () => {
    tokenService.setToken(MOCK_TOKEN);
    tokenService.removeToken();
    expect(tokenService.getToken()).toBeNull();
  });

  // BOUNDARY
  it('getToken returns null when no token is stored', () => {
    expect(tokenService.getToken()).toBeNull();
  });

  it('setToken handles a very long JWT string', () => {
    const longToken = 'a'.repeat(5000);
    tokenService.setToken(longToken);
    expect(tokenService.getToken()).toBe(longToken);
  });

  it('setToken with an empty string stores empty string', () => {
    tokenService.setToken('');
    expect(tokenService.getToken()).toBe('');
  });

  // EXCEPTION HANDLING
  it('getToken returns null after localStorage.clear()', () => {
    tokenService.setToken(MOCK_TOKEN);
    localStorage.clear();
    expect(tokenService.getToken()).toBeNull();
  });

  it('overwriting token with a new value updates storage', () => {
    tokenService.setToken('first-token');
    tokenService.setToken('second-token');
    expect(tokenService.getToken()).toBe('second-token');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 2. getUser / setUser / removeUser
// ─────────────────────────────────────────────────────────────────────────────
describe('tokenService – User Operations', () => {
  // NORMAL
  it('setUser stores and getUser retrieves the user object', () => {
    tokenService.setUser(mockUser);
    expect(tokenService.getUser()).toEqual(mockUser);
  });

  it('removeUser deletes the user from storage', () => {
    tokenService.setUser(mockUser);
    tokenService.removeUser();
    expect(tokenService.getUser()).toBeNull();
  });

  // BOUNDARY
  it('getUser returns null when no user is stored', () => {
    expect(tokenService.getUser()).toBeNull();
  });

  it('getUser handles malformed JSON in localStorage gracefully or throws', () => {
    (localStorage.getItem as ReturnType<typeof vi.fn>).mockReturnValueOnce('not-json');
    expect(() => tokenService.getUser()).toThrow();
  });

  it('setUser with special characters in name stores correctly', () => {
    const specialUser = { ...mockUser, name: 'José O\'Brien <script>' };
    tokenService.setUser(specialUser);
    expect(tokenService.getUser()).toEqual(specialUser);
  });

  // EXCEPTION HANDLING
  it('overwriting user with new data updates storage', () => {
    tokenService.setUser(mockUser);
    const updatedUser = { ...mockUser, name: 'Updated Name' };
    tokenService.setUser(updatedUser);
    expect(tokenService.getUser()?.name).toBe('Updated Name');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// 3. clearAll
// ─────────────────────────────────────────────────────────────────────────────
describe('tokenService – clearAll', () => {
  // NORMAL
  it('clearAll removes both token and user', () => {
    tokenService.setToken(MOCK_TOKEN);
    tokenService.setUser(mockUser);
    tokenService.clearAll();
    expect(tokenService.getToken()).toBeNull();
    expect(tokenService.getUser()).toBeNull();
  });

  // BOUNDARY
  it('clearAll on an already-empty store does not throw', () => {
    expect(() => tokenService.clearAll()).not.toThrow();
  });

  // EXCEPTION HANDLING
  it('clearAll is idempotent – calling twice does not throw', () => {
    tokenService.setToken(MOCK_TOKEN);
    tokenService.clearAll();
    expect(() => tokenService.clearAll()).not.toThrow();
    expect(tokenService.getToken()).toBeNull();
  });
});
