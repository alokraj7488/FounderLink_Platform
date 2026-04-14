/**
 * store.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect, vi } from 'vitest';
import store from '../../store/store';
import { setCredentials, logout } from '../../store/slices/authSlice';
import { setUnreadCount, decrementUnread } from '../../store/slices/notificationSlice';
import { toggleTheme, setTheme } from '../../store/slices/themeSlice';
import { setCurrentPage } from '../../store/slices/startupSlice';
import { toggleSidebar, setSidebar } from '../../store/slices/sidebarSlice';

vi.mock('../../core/tokenService', () => ({
  default: {
    getToken: vi.fn(() => null),
    getUser: vi.fn(() => null),
    setToken: vi.fn(),
    setUser: vi.fn(),
    clearAll: vi.fn(),
  },
}));

vi.mock('../../core/api/startupApi', () => ({
  getAllStartups: vi.fn(),
}));

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('store – Normal', () => {
  it('has all five required slices in state shape', () => {
    const state = store.getState();
    expect(state).toHaveProperty('auth');
    expect(state).toHaveProperty('notifications');
    expect(state).toHaveProperty('theme');
    expect(state).toHaveProperty('startups');
    expect(state).toHaveProperty('sidebar');
  });

  it('auth slice initial state: unauthenticated', () => {
    const { auth } = store.getState();
    expect(auth.isAuthenticated).toBe(false);
    expect(auth.user).toBeNull();
  });

  it('notifications slice initial state: unreadCount is 0', () => {
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('sidebar slice initial state: open is true', () => {
    expect(store.getState().sidebar.open).toBe(true);
  });

  it('startups slice initial state: items empty, loading false', () => {
    const { startups } = store.getState();
    expect(startups.items).toHaveLength(0);
    expect(startups.loading).toBe(false);
    expect(startups.error).toBeNull();
  });

  it('dispatching setCredentials updates auth slice', () => {
    store.dispatch(setCredentials({
      token: 'tok', userId: 1, role: 'ROLE_FOUNDER', email: 'a@b.com', name: 'Alice',
    }));
    expect(store.getState().auth.isAuthenticated).toBe(true);
    store.dispatch(logout());
  });

  it('dispatching setUnreadCount updates notifications slice', () => {
    store.dispatch(setUnreadCount(5));
    expect(store.getState().notifications.unreadCount).toBe(5);
    store.dispatch(setUnreadCount(0));
  });

  it('dispatching toggleTheme updates theme slice', () => {
    const before = store.getState().theme.mode;
    store.dispatch(toggleTheme());
    const after = store.getState().theme.mode;
    expect(after).not.toBe(before);
    store.dispatch(setTheme(before));
  });

  it('dispatching setCurrentPage updates startups slice', () => {
    store.dispatch(setCurrentPage(3));
    expect(store.getState().startups.currentPage).toBe(3);
    store.dispatch(setCurrentPage(0));
  });

  it('dispatching toggleSidebar updates sidebar slice', () => {
    const before = store.getState().sidebar.open;
    store.dispatch(toggleSidebar());
    expect(store.getState().sidebar.open).toBe(!before);
    store.dispatch(setSidebar(before));
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('store – Boundary', () => {
  it('store.getState() returns a plain object', () => {
    expect(typeof store.getState()).toBe('object');
    expect(store.getState()).not.toBeNull();
  });

  it('store.dispatch is a function', () => {
    expect(typeof store.dispatch).toBe('function');
  });

  it('store.subscribe is a function', () => {
    expect(typeof store.subscribe).toBe('function');
  });

  it('subscribe listener is called on state change', () => {
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);
    store.dispatch(setUnreadCount(99));
    expect(listener).toHaveBeenCalled();
    unsubscribe();
    store.dispatch(setUnreadCount(0));
  });

  it('unsubscribed listener is not called after unsubscribe', () => {
    const listener = vi.fn();
    const unsubscribe = store.subscribe(listener);
    unsubscribe();
    store.dispatch(setUnreadCount(42));
    expect(listener).not.toHaveBeenCalled();
    store.dispatch(setUnreadCount(0));
  });

  it('notifications do not go negative via decrementUnread from 0', () => {
    store.dispatch(setUnreadCount(0));
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('store – Exception Handling', () => {
  it('state remains consistent after login → logout → login cycle', () => {
    const creds = { token: 'tok', userId: 5, role: 'ROLE_INVESTOR', email: 'b@c.com', name: 'Bob' };
    store.dispatch(setCredentials(creds));
    store.dispatch(logout());
    store.dispatch(setCredentials({ ...creds, email: 're@login.com' }));
    expect(store.getState().auth.isAuthenticated).toBe(true);
    expect(store.getState().auth.user?.email).toBe('re@login.com');
    store.dispatch(logout());
  });

  it('multiple slices can be updated independently without interference', () => {
    store.dispatch(setUnreadCount(10));
    store.dispatch(setCurrentPage(7));
    store.dispatch(setSidebar(false));
    const state = store.getState();
    expect(state.notifications.unreadCount).toBe(10);
    expect(state.startups.currentPage).toBe(7);
    expect(state.sidebar.open).toBe(false);
    // Reset
    store.dispatch(setUnreadCount(0));
    store.dispatch(setCurrentPage(0));
    store.dispatch(setSidebar(true));
  });

  it('dispatching an unknown action does not crash or corrupt state', () => {
    const before = store.getState();
    store.dispatch({ type: '__UNKNOWN_ACTION__' });
    const after = store.getState();
    expect(after.auth.isAuthenticated).toBe(before.auth.isAuthenticated);
    expect(after.notifications.unreadCount).toBe(before.notifications.unreadCount);
    expect(after.sidebar.open).toBe(before.sidebar.open);
  });
});
