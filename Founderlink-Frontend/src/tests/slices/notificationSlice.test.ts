/**
 * notificationSlice.test.ts
 * Tests: Normal | Boundary | Exception Handling
 */
import { describe, it, expect } from 'vitest';
import { configureStore } from '@reduxjs/toolkit';
import notificationReducer, {
  setUnreadCount,
  decrementUnread,
} from '../../store/slices/notificationSlice';

const makeStore = () => configureStore({ reducer: { notifications: notificationReducer } });

// ─────────────────────────────────────────────────────────────────────────────
// NORMAL
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationSlice – Normal', () => {
  it('initial state: unreadCount is 0', () => {
    const store = makeStore();
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('setUnreadCount: sets count to given value', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(7));
    expect(store.getState().notifications.unreadCount).toBe(7);
  });

  it('decrementUnread: reduces count by 1', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(5));
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(4);
  });

  it('setUnreadCount: overwrites a previous count', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(3));
    store.dispatch(setUnreadCount(10));
    expect(store.getState().notifications.unreadCount).toBe(10);
  });

  it('multiple decrements reduce count correctly', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(3));
    store.dispatch(decrementUnread());
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// BOUNDARY
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationSlice – Boundary', () => {
  it('setUnreadCount to 0: sets count to 0', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(5));
    store.dispatch(setUnreadCount(0));
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('decrementUnread at 0: stays at 0 (no negative values)', () => {
    const store = makeStore();
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('decrementUnread from 1: reaches 0 exactly', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(1));
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('setUnreadCount with very large number', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(99999));
    expect(store.getState().notifications.unreadCount).toBe(99999);
  });

  it('decrementUnread many times from 0 never goes negative', () => {
    const store = makeStore();
    for (let i = 0; i < 10; i++) store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// EXCEPTION HANDLING
// ─────────────────────────────────────────────────────────────────────────────
describe('notificationSlice – Exception Handling', () => {
  it('setUnreadCount followed by setUnreadCount(0) then decrement stays at 0', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(5));
    store.dispatch(setUnreadCount(0));
    store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('rapid set and decrement produces consistent state', () => {
    const store = makeStore();
    store.dispatch(setUnreadCount(10));
    for (let i = 0; i < 10; i++) store.dispatch(decrementUnread());
    expect(store.getState().notifications.unreadCount).toBe(0);
    store.dispatch(decrementUnread()); // one extra should still be 0
    expect(store.getState().notifications.unreadCount).toBe(0);
  });

  it('setUnreadCount does not throw when passed 0', () => {
    const store = makeStore();
    expect(() => store.dispatch(setUnreadCount(0))).not.toThrow();
  });
});
