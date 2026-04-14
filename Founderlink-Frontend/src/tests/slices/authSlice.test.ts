/**
 * authSlice.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import authReducer, {
  setCredentials,
  logout,
  selectCurrentUser,
  selectIsAuthenticated,
} from '../../store/slices/authSlice';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => null),
    getUser: vi.fn(() => null),
    setToken: vi.fn(),
    setUser: vi.fn(),
    clearAll: vi.fn(),
  },
}));

const makeStore = () => configureStore({ reducer: { auth: authReducer } });

const credentials = {
  token: 'jwt.token.value',
  userId: 42,
  role: 'ROLE_FOUNDER',
  email: 'founder@test.com',
  name: 'Founder One',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('authSlice – Normal', () => {
  it('initial state: isAuthenticated is false, user is null', () => {
    const store = makeStore();
    const state = store.getState();
    expect(selectIsAuthenticated(state as never)).toBe(false);
    expect(selectCurrentUser(state as never)).toBeNull();
  });

  it('setCredentials: sets user, token and isAuthenticated', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    const state = store.getState();
    expect(selectIsAuthenticated(state as never)).toBe(true);
    expect(selectCurrentUser(state as never)?.email).toBe('founder@test.com');
    expect(selectCurrentUser(state as never)?.role).toBe('ROLE_FOUNDER');
    expect(selectCurrentUser(state as never)?.userId).toBe(42);
  });

  it('logout: clears user and sets isAuthenticated to false', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    store.dispatch(logout());
    const state = store.getState();
    expect(selectIsAuthenticated(state as never)).toBe(false);
    expect(selectCurrentUser(state as never)).toBeNull();
  });

  it('setCredentials: persists name field correctly', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    expect(selectCurrentUser(store.getState() as never)?.name).toBe('Founder One');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('authSlice – Boundary', () => {
  it('setCredentials: handles userId of 0', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...credentials, userId: 0 }));
    expect(selectCurrentUser(store.getState() as never)?.userId).toBe(0);
  });

  it('setCredentials: overwrites previous credentials', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    store.dispatch(setCredentials({ ...credentials, email: 'new@test.com', userId: 99 }));
    expect(selectCurrentUser(store.getState() as never)?.email).toBe('new@test.com');
    expect(selectCurrentUser(store.getState() as never)?.userId).toBe(99);
  });

  it('setCredentials: handles empty name string', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...credentials, name: '' }));
    expect(selectCurrentUser(store.getState() as never)?.name).toBe('');
  });

  it('setCredentials: handles ROLE_ADMIN role', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...credentials, role: 'ROLE_ADMIN' }));
    expect(selectCurrentUser(store.getState() as never)?.role).toBe('ROLE_ADMIN');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('authSlice – Exception Handling', () => {
  it('logout on an unauthenticated store does not throw', () => {
    const store = makeStore();
    expect(() => store.dispatch(logout())).not.toThrow();
    expect(selectIsAuthenticated(store.getState() as never)).toBe(false);
  });

  it('double logout is safe and stays false', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    store.dispatch(logout());
    expect(() => store.dispatch(logout())).not.toThrow();
    expect(selectIsAuthenticated(store.getState() as never)).toBe(false);
  });

  it('login → logout → login restores authenticated state', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    store.dispatch(logout());
    store.dispatch(setCredentials({ ...credentials, email: 're-login@test.com' }));
    expect(selectIsAuthenticated(store.getState() as never)).toBe(true);
    expect(selectCurrentUser(store.getState() as never)?.email).toBe('re-login@test.com');
  });
});
