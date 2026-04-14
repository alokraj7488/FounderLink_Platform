/**
 * useAuth.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi } from 'vitest';
import { renderHook } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import React from 'react';

import useAuth from '../../shared/hooks/useAuth';
import authReducer, { setCredentials, logout } from '../../store/slices/authSlice';

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

const wrapper =
  (store: ReturnType<typeof makeStore>) =>
  ({ children }: { children: React.ReactNode }) => (
    <Provider store={store}>{children}</Provider>
  );

const baseCredentials = {
  token: 'jwt-token',
  userId: 7,
  role: 'ROLE_FOUNDER',
  email: 'user@test.com',
  name: 'Test User',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('useAuth – Normal', () => {
  it('returns isAuthenticated false and null user when no credentials set', () => {
    const store = makeStore();
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
  });

  it('returns isAuthenticated true and populated user after setCredentials', () => {
    const store = makeStore();
    store.dispatch(setCredentials(baseCredentials));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isAuthenticated).toBe(true);
    expect(result.current.user?.email).toBe('user@test.com');
    expect(result.current.userId).toBe(7);
  });

  it('isFounder is true when role is ROLE_FOUNDER', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_FOUNDER' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isFounder).toBe(true);
    expect(result.current.isInvestor).toBe(false);
    expect(result.current.isAdmin).toBe(false);
    expect(result.current.isCoFounder).toBe(false);
  });

  it('isInvestor is true when role is ROLE_INVESTOR', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_INVESTOR' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isInvestor).toBe(true);
    expect(result.current.isFounder).toBe(false);
  });

  it('isAdmin is true when role is ROLE_ADMIN', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_ADMIN' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isAdmin).toBe(true);
  });

  it('isCoFounder is true when role is ROLE_COFOUNDER', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_COFOUNDER' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isCoFounder).toBe(true);
  });

  it('returns role string correctly', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_INVESTOR' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.role).toBe('ROLE_INVESTOR');
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('useAuth – Boundary', () => {
  it('userId is undefined when no user is logged in', () => {
    const store = makeStore();
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.userId).toBeUndefined();
  });

  it('userId is 0 when userId equals 0', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, userId: 0 }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.userId).toBe(0);
  });

  it('role is undefined when no user is logged in', () => {
    const store = makeStore();
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.role).toBeUndefined();
  });

  it('all role flags are false when no user is set', () => {
    const store = makeStore();
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isFounder).toBe(false);
    expect(result.current.isInvestor).toBe(false);
    expect(result.current.isAdmin).toBe(false);
    expect(result.current.isCoFounder).toBe(false);
  });

  it('all role flags are false for an unknown role string', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_UNKNOWN' }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isFounder).toBe(false);
    expect(result.current.isInvestor).toBe(false);
    expect(result.current.isAdmin).toBe(false);
    expect(result.current.isCoFounder).toBe(false);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('useAuth – Exception Handling', () => {
  it('resets to unauthenticated state after logout', () => {
    const store = makeStore();
    store.dispatch(setCredentials(baseCredentials));
    store.dispatch(logout());
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isAuthenticated).toBe(false);
    expect(result.current.user).toBeNull();
    expect(result.current.userId).toBeUndefined();
    expect(result.current.role).toBeUndefined();
  });

  it('updates correctly after credentials are replaced', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_FOUNDER' }));
    store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_ADMIN', userId: 99 }));
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(result.current.isAdmin).toBe(true);
    expect(result.current.isFounder).toBe(false);
    expect(result.current.userId).toBe(99);
  });

  it('hook does not throw when store state changes rapidly', () => {
    const store = makeStore();
    const { result } = renderHook(() => useAuth(), { wrapper: wrapper(store) });
    expect(() => {
      store.dispatch(setCredentials(baseCredentials));
      store.dispatch(logout());
      store.dispatch(setCredentials({ ...baseCredentials, role: 'ROLE_INVESTOR' }));
    }).not.toThrow();
    // Read final state directly from the store — hook snapshot may be stale
    expect(store.getState().auth.user?.role).toBe('ROLE_INVESTOR');
    void result; // hook rendered without error — that is what we verify
  });
});
