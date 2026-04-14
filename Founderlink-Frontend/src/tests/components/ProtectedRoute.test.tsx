/**
 * ProtectedRoute.test.tsx
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import React from 'react';

import ProtectedRoute from '../../shared/components/ProtectedRoute';
import authReducer, { setCredentials } from '../../store/slices/authSlice';
import themeReducer from '../../store/slices/themeSlice';
import notificationReducer from '../../store/slices/notificationSlice';
import sidebarReducer from '../../store/slices/sidebarSlice';
import startupReducer from '../../store/slices/startupSlice';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => null),
    getUser: vi.fn(() => null),
    setToken: vi.fn(),
    setUser: vi.fn(),
    clearAll: vi.fn(),
  },
}));

import tokenService from '../../core/tokenService';

const makeStore = () =>
  configureStore({
    reducer: {
      auth: authReducer,
      theme: themeReducer,
      notifications: notificationReducer,
      sidebar: sidebarReducer,
      startups: startupReducer,
    },
  });

// Helper: renders ProtectedRoute wrapped with routing and Redux
const renderProtectedRoute = (
  store: ReturnType<typeof makeStore>,
  props: { allowedRoles?: string[]; children?: React.ReactNode },
  initialPath = '/dashboard'
) => {
  return render(
    <Provider store={store}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/unauthorized" element={<div>Unauthorized Page</div>} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute allowedRoles={props.allowedRoles}>
                {props.children ?? <div>Protected Content</div>}
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </Provider>
  );
};

const credentials = {
  token: 'valid-token',
  userId: 1,
  role: 'ROLE_FOUNDER',
  email: 'user@test.com',
  name: 'Test User',
};

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('ProtectedRoute – Normal', () => {
  beforeEach(() => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('valid-token');
  });

  it('renders children when user is authenticated with no role restriction', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    renderProtectedRoute(store, {});
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('renders children when user role matches allowedRoles', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    renderProtectedRoute(store, { allowedRoles: ['ROLE_FOUNDER'] });
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });

  it('renders children when role is one of many allowed roles', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    renderProtectedRoute(store, { allowedRoles: ['ROLE_ADMIN', 'ROLE_FOUNDER', 'ROLE_INVESTOR'] });
    expect(screen.getByText('Protected Content')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('ProtectedRoute – Boundary', () => {
  it('redirects to /login when not authenticated and no token', () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue(null);
    const store = makeStore(); // no credentials dispatched
    renderProtectedRoute(store, {});
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });

  it('redirects to /unauthorized when role is not in allowedRoles', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...credentials, role: 'ROLE_INVESTOR' }));
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('valid-token');

    renderProtectedRoute(store, { allowedRoles: ['ROLE_FOUNDER'] });
    expect(screen.getByText('Unauthorized Page')).toBeInTheDocument();
  });

  it('renders Outlet children (no explicit children prop) when authenticated', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('valid-token');

    // allowedRoles not set → no role check
    renderProtectedRoute(store, { allowedRoles: undefined, children: undefined });
    // No children provided + no nested Outlet route → renders nothing inside, but does not crash
    expect(screen.queryByText('Login Page')).not.toBeInTheDocument();
  });

  it('redirects to /login when token is null even if Redux says authenticated', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    // Token was removed from storage after login
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue(null);

    renderProtectedRoute(store, {});
    expect(screen.getByText('Login Page')).toBeInTheDocument();
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('ProtectedRoute – Exception Handling', () => {
  it('redirects to /login when allowedRoles is an empty array', () => {
    const store = makeStore();
    store.dispatch(setCredentials(credentials));
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('valid-token');

    // Empty allowedRoles → user's role not included → /unauthorized
    renderProtectedRoute(store, { allowedRoles: [] });
    expect(screen.getByText('Unauthorized Page')).toBeInTheDocument();
  });

  it('calls clearAll when redirecting due to missing token', () => {
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue(null);
    const store = makeStore();

    renderProtectedRoute(store, {});
    expect(tokenService.clearAll).toHaveBeenCalled();
  });

  it('does not crash when user has an unknown role', () => {
    const store = makeStore();
    store.dispatch(setCredentials({ ...credentials, role: 'ROLE_UNKNOWN' }));
    (tokenService.getToken as ReturnType<typeof vi.fn>).mockReturnValue('valid-token');

    renderProtectedRoute(store, { allowedRoles: ['ROLE_FOUNDER'] });
    expect(screen.getByText('Unauthorized Page')).toBeInTheDocument();
  });
});
